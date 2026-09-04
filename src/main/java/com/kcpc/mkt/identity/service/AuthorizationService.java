package com.kcpc.mkt.identity.service;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.PermissionGrantItemScopeRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantStageScopeRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server-authoritative authorization: access class -&gt; native CEO/MM authority -&gt; active
 * Operational Permission -&gt; scope -&gt; self-review conflict. Never trust the client; every
 * write path in every domain service calls through here (KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md:
 * "The frontend is never the security authority").
 */
@Service
public class AuthorizationService {

    private final PermissionGrantRepository grantRepository;
    private final PermissionGrantStageScopeRepository stageScopeRepository;
    private final PermissionGrantItemScopeRepository itemScopeRepository;
    private final UserRepository userRepository;

    public AuthorizationService(PermissionGrantRepository grantRepository,
                                 PermissionGrantStageScopeRepository stageScopeRepository,
                                 PermissionGrantItemScopeRepository itemScopeRepository,
                                 UserRepository userRepository) {
        this.grantRepository = grantRepository;
        this.stageScopeRepository = stageScopeRepository;
        this.itemScopeRepository = itemScopeRepository;
        this.userRepository = userRepository;
    }

    public boolean hasNativeAuthority(User user) {
        AccessClass ac = user.resolvedAccessClass();
        return ac == AccessClass.CEO_OWNER || ac == AccessClass.MARKETING_MANAGER;
    }

    /** Every active CEO_OWNER/MARKETING_MANAGER user - e.g. comment notifications' own "an
     * Employee's comment notifies MM/CEO" rule (StageCommentService#addComment), which needs the
     * actual set of native-authority holders as recipients, not just a single-user yes/no check. */
    public List<User> findActiveNativeAuthorityUsers() {
        return userRepository.findByActiveTrueOrderByFullNameAsc().stream()
                .filter(this::hasNativeAuthority)
                .toList();
    }

    /**
     * Centralized workflow-participation gate: single source of truth for whether an EMPLOYEE is
     * restricted to My Ideas + Submit Idea only (nav visibility - {@code MvcNavigationAdvice} - and
     * the server-side {@code WorkflowParticipationInterceptor} both call this, never re-derive it).
     * Never based on the Business Role's name/designation, and never based on OperationalPermission
     * grants - those stay a separate, per-user, in-area authorization layer. CEO_OWNER and
     * MARKETING_MANAGER are never restricted by this rule.
     */
    public boolean isNonProductionEmployee(User user) {
        if (user.resolvedAccessClass() != AccessClass.EMPLOYEE) {
            return false;
        }
        var role = user.getBusinessRole();
        return role == null || !role.isParticipatesInWorkflow();
    }

    /**
     * Resolves authority for a governed action. Returns {@code empty()} when the user acts under
     * native CEO/MM authority (no grant record involved). Returns the specific {@link
     * PermissionGrant} when an Employee acts under a valid delegated grant whose scope covers
     * this stage/item. Throws when neither applies.
     */
    public Optional<PermissionGrant> requireAuthority(User user, OperationalPermission permission,
                                                        LifecycleStage stage, WorkflowInstance itemContext) {
        if (hasNativeAuthority(user)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        List<PermissionGrant> grants = grantRepository.findByGranteeAndPermissionAndActiveTrue(user, permission);
        for (PermissionGrant grant : grants) {
            if (grant.isCurrentlyValid(now) && scopeCovers(grant, stage, itemContext)) {
                return Optional.of(grant);
            }
        }
        boolean hasAnyGrantAtAll = !grants.isEmpty();
        if (hasAnyGrantAtAll) {
            boolean allExpiredOrRevoked = grants.stream().noneMatch(g -> g.isCurrentlyValid(now));
            if (allExpiredOrRevoked) {
                throw DomainException.forbidden(ErrorCode.PERM_OPERATIONAL_PERMISSION_EXPIRED,
                        "Permission " + permission + " is not currently active for this user");
            }
            throw DomainException.forbidden(ErrorCode.PERM_OPERATIONAL_PERMISSION_OUT_OF_SCOPE,
                    "Permission " + permission + " does not cover this stage/item");
        }
        throw DomainException.forbidden(ErrorCode.PERM_OPERATIONAL_PERMISSION_REQUIRED,
                "Permission " + permission + " has not been granted to this user");
    }

    /** For actions restricted to native CEO/MM authority only (e.g. Hold/Resume, user administration). */
    public void requireNativeAuthority(User user, String actionDescription) {
        if (!hasNativeAuthority(user)) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    actionDescription + " requires CEO_OWNER or MARKETING_MANAGER");
        }
    }

    public void requireAccessClass(User user, AccessClass required, String actionDescription) {
        if (user.resolvedAccessClass() != required) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    actionDescription + " requires " + required);
        }
    }

    /**
     * BRS-REQ-012 / ERD-CON-011: an Employee acting under a delegated review permission may not
     * decide on work they personally submitted/prepared/executed. Only applies on the delegated
     * path (actingGrant present) - native CEO/MM authority is not subject to this barrier.
     */
    public void requireNoSelfReviewConflict(Optional<PermissionGrant> actingGrant, User currentUser,
                                             UUID conflictedUserId) {
        if (actingGrant.isPresent() && currentUser.getId().equals(conflictedUserId)) {
            throw DomainException.forbidden(ErrorCode.PERM_SELF_APPROVAL_PROHIBITED,
                    "Cannot make a review decision on your own submitted/prepared/executed work");
        }
    }

    /**
     * Scope-agnostic "does this user hold any currently-valid grant of any of these permissions at
     * all" check - used for module-aware nav/route reachability (WorkspaceAccessService), never for
     * authorizing an actual action (which must still go through {@link #requireAuthority} with the
     * real stage/item context - a STAGE_RESTRICTED/ITEM_SPECIFIC grant that doesn't cover a given
     * action is still rejected there even though it makes the module reachable here).
     */
    public boolean hasAnyActiveGrant(User user, OperationalPermission... permissions) {
        Instant now = Instant.now();
        for (OperationalPermission permission : permissions) {
            if (grantRepository.findByGranteeAndPermissionAndActiveTrue(user, permission).stream()
                    .anyMatch(grant -> grant.isCurrentlyValid(now))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Explicit-grant-only eligibility check for operational execution candidacy (Shoot/Edit/
     * Publishing assignee pickers, assignment/reassignment validation, execution start/submit -
     * see OperationalEligibilityService). Deliberately NEVER treats CEO/MM native authority as
     * sufficient, unlike {@link #requireAuthority}: management authority is not hands-on
     * execution eligibility (the same boundary requireActiveAssignee-style checks already draw
     * for execution itself). Only a currently-valid, scope-covering delegated grant counts.
     */
    public boolean hasExplicitPermissionGrant(User user, OperationalPermission permission, LifecycleStage stage,
                                               WorkflowInstance itemContext) {
        Instant now = Instant.now();
        return grantRepository.findByGranteeAndPermissionAndActiveTrue(user, permission).stream()
                .anyMatch(grant -> grant.isCurrentlyValid(now) && scopeCovers(grant, stage, itemContext));
    }

    /**
     * Bulk resolution of every active user currently holding an explicit, currently-valid grant
     * of {@code permission} covering {@code stage}/{@code itemContext} - the source for every
     * permission-driven candidate picker. Mirrors {@link #hasExplicitPermissionGrant}'s semantics
     * (never native-authority-based) at list-population scale: scope rows for every currently
     * active grant of this permission are bulk-fetched (not one query per grant), so populating a
     * candidate dropdown never becomes an N+1 permission lookup.
     */
    public List<User> findActiveGranteesWithExplicitGrant(OperationalPermission permission, LifecycleStage stage,
                                                            WorkflowInstance itemContext) {
        Instant now = Instant.now();
        List<PermissionGrant> currentGrants = grantRepository.findByPermissionAndActiveTrue(permission).stream()
                .filter(grant -> grant.isCurrentlyValid(now))
                .toList();
        if (currentGrants.isEmpty()) {
            return List.of();
        }
        List<UUID> grantIds = currentGrants.stream().map(PermissionGrant::getId).toList();
        Set<UUID> stageMatchedGrantIds = stageScopeRepository.findByGrant_IdIn(grantIds).stream()
                .filter(scope -> scope.getStageNumber() == stage)
                .map(scope -> scope.getGrant().getId())
                .collect(Collectors.toSet());
        Set<UUID> itemMatchedGrantIds = itemContext == null ? Set.of()
                : itemScopeRepository.findByGrant_IdIn(grantIds).stream()
                        .filter(scope -> scope.getWorkflowInstance().getId().equals(itemContext.getId()))
                        .map(scope -> scope.getGrant().getId())
                        .collect(Collectors.toSet());
        Set<UUID> eligibleUserIds = currentGrants.stream()
                .filter(grant -> switch (grant.getScopeType()) {
                    case GLOBAL -> true;
                    case STAGE_RESTRICTED -> stageMatchedGrantIds.contains(grant.getId());
                    case ITEM_SPECIFIC -> itemMatchedGrantIds.contains(grant.getId());
                })
                .map(grant -> grant.getGrantee().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (eligibleUserIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findByIdInAndActiveTrueOrderByFullNameAsc(eligibleUserIds);
    }

    private boolean scopeCovers(PermissionGrant grant, LifecycleStage stage, WorkflowInstance item) {
        return switch (grant.getScopeType()) {
            case GLOBAL -> true;
            case STAGE_RESTRICTED -> stage != null && stageScopeRepository.findByGrant(grant).stream()
                    .anyMatch(s -> s.getStageNumber() == stage);
            case ITEM_SPECIFIC -> item != null && itemScopeRepository.findByGrant(grant).stream()
                    .anyMatch(s -> s.getWorkflowInstance().getId().equals(item.getId()));
        };
    }
}
