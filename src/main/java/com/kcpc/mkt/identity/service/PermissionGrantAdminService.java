package com.kcpc.mkt.identity.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.PermissionGrantItemScope;
import com.kcpc.mkt.identity.domain.PermissionGrantStageScope;
import com.kcpc.mkt.identity.domain.PermissionScopeType;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.dto.PermissionGrantSummary;
import com.kcpc.mkt.identity.repository.PermissionGrantItemScopeRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantStageScopeRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.repository.WorkflowInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BRS-REQ-006..013 / API-OP-065: exclusive CEO Operational Permission grant/modify/expire/revoke,
 * full lifecycle with scope and mandatory reason. Modify/revoke is soft - the row is never deleted.
 */
@Service
public class PermissionGrantAdminService {

    private final PermissionGrantRepository grantRepository;
    private final PermissionGrantStageScopeRepository stageScopeRepository;
    private final PermissionGrantItemScopeRepository itemScopeRepository;
    private final UserRepository userRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public PermissionGrantAdminService(PermissionGrantRepository grantRepository,
                                        PermissionGrantStageScopeRepository stageScopeRepository,
                                        PermissionGrantItemScopeRepository itemScopeRepository,
                                        UserRepository userRepository,
                                        WorkflowInstanceRepository workflowInstanceRepository,
                                        AuthorizationService authorizationService, AuditService auditService) {
        this.grantRepository = grantRepository;
        this.stageScopeRepository = stageScopeRepository;
        this.itemScopeRepository = itemScopeRepository;
        this.userRepository = userRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private void requireCeo(User actor) {
        authorizationService.requireAccessClass(actor, AccessClass.CEO_OWNER, "Operational Permission administration");
    }

    @Transactional
    public PermissionGrant grant(User ceo, UUID granteeUserId, OperationalPermission permission,
                                  PermissionScopeType scopeType, List<LifecycleStage> stages,
                                  List<UUID> workflowInstanceIds, Instant effectiveFrom, Instant effectiveUntil,
                                  String reason) {
        requireCeo(ceo);
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A reason is mandatory for a permission grant");
        }
        User grantee = userRepository.findById(granteeUserId)
                .orElseThrow(() -> DomainException.notFound("User not found: " + granteeUserId));
        // ERD-CON-023/024: STAGE_RESTRICTED/ITEM_SPECIFIC grants must have >=1 matching scope child rows.
        if (scopeType == PermissionScopeType.STAGE_RESTRICTED && (stages == null || stages.isEmpty())) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "At least one lifecycle stage is required for a STAGE_RESTRICTED grant");
        }
        if (scopeType == PermissionScopeType.ITEM_SPECIFIC && (workflowInstanceIds == null || workflowInstanceIds.isEmpty())) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "At least one item (Idea/Content ID) is required for an ITEM_SPECIFIC grant");
        }

        PermissionGrant grant = grantRepository.save(new PermissionGrant(grantee, ceo, permission, scopeType,
                effectiveFrom != null ? effectiveFrom : Instant.now(), effectiveUntil));

        if (scopeType == PermissionScopeType.STAGE_RESTRICTED) {
            for (LifecycleStage stage : stages) {
                stageScopeRepository.save(new PermissionGrantStageScope(grant, stage));
            }
        } else if (scopeType == PermissionScopeType.ITEM_SPECIFIC) {
            for (UUID workflowInstanceId : workflowInstanceIds) {
                WorkflowInstance workflowInstance = workflowInstanceRepository.findById(workflowInstanceId)
                        .orElseThrow(() -> DomainException.notFound("Workflow instance not found: " + workflowInstanceId));
                itemScopeRepository.save(new PermissionGrantItemScope(grant, workflowInstance));
            }
        }
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "PERMISSION_GRANTED", "permission_grants",
                grant.getId(), reason);
        return grant;
    }

    @Transactional
    public PermissionGrant modifyExpiry(User ceo, UUID grantId, Instant newEffectiveUntil, String reason) {
        requireCeo(ceo);
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A reason is mandatory for a permission modification");
        }
        PermissionGrant grant = requireGrant(grantId);
        grant.modifyExpiry(newEffectiveUntil);
        grantRepository.save(grant);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "PERMISSION_MODIFIED", "permission_grants",
                grant.getId(), reason);
        return grant;
    }

    /** Soft revoke: revoked_at set, row never deleted (API-OP-065). */
    @Transactional
    public PermissionGrant revoke(User ceo, UUID grantId, String reason) {
        requireCeo(ceo);
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A reason is mandatory for a permission revocation");
        }
        PermissionGrant grant = requireGrant(grantId);
        grant.revoke(Instant.now());
        grantRepository.save(grant);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "PERMISSION_REVOKED", "permission_grants",
                grant.getId(), reason);
        return grant;
    }

    private PermissionGrant requireGrant(UUID grantId) {
        return grantRepository.findById(grantId)
                .orElseThrow(() -> DomainException.notFound("Permission grant not found: " + grantId));
    }

    /**
     * Consolidated "who currently holds what permission" view (§6.2's read-through model extended
     * to a single all-users list) - CEO-exclusive, same as every other permission-administration
     * action. Built eagerly inside the transaction (grantee/grantor/scope children are all LAZY)
     * so the returned DTOs are safe to render from a JSP outside any Hibernate session.
     */
    @Transactional(readOnly = true)
    public List<PermissionGrantSummary> listActiveGrants(User ceo) {
        requireCeo(ceo);
        return grantRepository.findByActiveTrueOrderByEffectiveFromDesc().stream()
                .map(grant -> new PermissionGrantSummary(grant.getId(), grant.getGrantee().getId(),
                        grant.getGrantee().getFullName(), grant.getGrantee().getEmail(),
                        grant.getGrantor().getFullName(), grant.getPermission(), grant.getScopeType(),
                        scopeDetail(grant), grant.getEffectiveFrom(), grant.getEffectiveUntil()))
                .toList();
    }

    private String scopeDetail(PermissionGrant grant) {
        return switch (grant.getScopeType()) {
            case GLOBAL -> "";
            case STAGE_RESTRICTED -> stageScopeRepository.findByGrant(grant).stream()
                    .map(s -> s.getStageNumber().toString())
                    .collect(Collectors.joining(", "));
            case ITEM_SPECIFIC -> itemScopeRepository.findByGrant(grant).size() + " item(s)";
        };
    }
}
