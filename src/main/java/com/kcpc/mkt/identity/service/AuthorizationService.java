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
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    public AuthorizationService(PermissionGrantRepository grantRepository,
                                 PermissionGrantStageScopeRepository stageScopeRepository,
                                 PermissionGrantItemScopeRepository itemScopeRepository) {
        this.grantRepository = grantRepository;
        this.stageScopeRepository = stageScopeRepository;
        this.itemScopeRepository = itemScopeRepository;
    }

    public boolean hasNativeAuthority(User user) {
        AccessClass ac = user.resolvedAccessClass();
        return ac == AccessClass.CEO_OWNER || ac == AccessClass.MARKETING_MANAGER;
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
