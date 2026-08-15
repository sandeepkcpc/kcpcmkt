package com.kcpc.mkt.workflow.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.marks.domain.RoleType;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.workflow.domain.AssigneeSide;
import com.kcpc.mkt.workflow.domain.CancellationRecord;
import com.kcpc.mkt.workflow.domain.ReassignmentAssignee;
import com.kcpc.mkt.workflow.domain.ReassignmentRecord;
import com.kcpc.mkt.workflow.domain.ReopenPurpose;
import com.kcpc.mkt.workflow.domain.ReopenRecord;
import com.kcpc.mkt.workflow.domain.RescheduleRecord;
import com.kcpc.mkt.workflow.domain.StageContext;
import com.kcpc.mkt.workflow.domain.TaskStage;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.CancellationRecordRepository;
import com.kcpc.mkt.workflow.repository.ReassignmentAssigneeRepository;
import com.kcpc.mkt.workflow.repository.ReassignmentRecordRepository;
import com.kcpc.mkt.workflow.repository.ReopenRecordRepository;
import com.kcpc.mkt.workflow.repository.RescheduleRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Build-prompt §28: Reschedule (#10), Reassign (#11), Cancel (#12), Reopen Completed (#8/#9). */
@Service
public class AdminActionService {

    private final ContentPlanRepository contentPlanRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final RescheduleRecordRepository rescheduleRecordRepository;
    private final ReassignmentRecordRepository reassignmentRecordRepository;
    private final ReassignmentAssigneeRepository reassignmentAssigneeRepository;
    private final CancellationRecordRepository cancellationRecordRepository;
    private final ReopenRecordRepository reopenRecordRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final WorkflowTransitionService workflowService;
    private final UserRepository userRepository;

    public AdminActionService(ContentPlanRepository contentPlanRepository,
                               ShootingAssignmentRepository shootingAssignmentRepository,
                               EditingAssignmentRepository editingAssignmentRepository,
                               RescheduleRecordRepository rescheduleRecordRepository,
                               ReassignmentRecordRepository reassignmentRecordRepository,
                               ReassignmentAssigneeRepository reassignmentAssigneeRepository,
                               CancellationRecordRepository cancellationRecordRepository,
                               ReopenRecordRepository reopenRecordRepository,
                               AuthorizationService authorizationService, AuditService auditService,
                               WorkflowTransitionService workflowService, UserRepository userRepository) {
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.rescheduleRecordRepository = rescheduleRecordRepository;
        this.reassignmentRecordRepository = reassignmentRecordRepository;
        this.reassignmentAssigneeRepository = reassignmentAssigneeRepository;
        this.cancellationRecordRepository = cancellationRecordRepository;
        this.reopenRecordRepository = reopenRecordRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.workflowService = workflowService;
        this.userRepository = userRepository;
    }

    private ContentPlan requirePlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    private void requireNotClosed(WorkflowInstance workflowInstance, String action) {
        WorkflowStatus status = workflowInstance.getCurrentStatusCode();
        if (status == WorkflowStatus.CAN || status == WorkflowStatus.COMP || status == WorkflowStatus.RJ) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    action + " is not valid once the deliverable is Cancelled, Completed, or Rejected");
        }
    }

    @Transactional
    public RescheduleRecord reschedule(User actor, UUID contentPlanId, StageContext stageContext,
                                        LocalDate newShootDate, LocalDate newEditDate, LocalDate newLiveDate,
                                        String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A reschedule reason is mandatory");
        }
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        requireNotClosed(workflowInstance, "Reschedule");
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_10_RESCHEDULE, LifecycleStage.ADMINISTRATIVE, workflowInstance);

        LocalDate priorShoot = plan.getPlannedShootDate();
        LocalDate priorEdit = plan.getPlannedEditDate();
        LocalDate priorLive = plan.getPlannedLiveDate();
        plan.applyReschedule(
                newShootDate != null ? newShootDate : priorShoot,
                newEditDate != null ? newEditDate : priorEdit,
                newLiveDate != null ? newLiveDate : priorLive);
        contentPlanRepository.save(plan);

        RescheduleRecord record = rescheduleRecordRepository.save(new RescheduleRecord(workflowInstance, stageContext,
                priorShoot, priorEdit, priorLive, plan.getPlannedShootDate(), plan.getPlannedEditDate(),
                plan.getPlannedLiveDate(), reason, actor, actingGrant.orElse(null)));
        auditService.record(actor, actingGrant, "ADMIN_ACTION", "RESCHEDULED", "content_plans", plan.getId(), reason);
        return record;
    }

    @Transactional
    public ReassignmentRecord reassign(User actor, UUID contentPlanId, TaskStage taskStage,
                                        List<UUID> newAssigneeUserIds, String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A reassignment reason is mandatory");
        }
        if (newAssigneeUserIds == null || newAssigneeUserIds.isEmpty()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "At least one new assignee is required");
        }
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        requireNotClosed(workflowInstance, "Reassign");
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_11_REASSIGN, LifecycleStage.ADMINISTRATIVE, workflowInstance);

        ReassignmentRecord record = reassignmentRecordRepository.save(
                new ReassignmentRecord(workflowInstance, taskStage, reason, actor, actingGrant.orElse(null)));

        if (taskStage == TaskStage.SHOOTING) {
            List<ShootingAssignment> previous = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
            for (ShootingAssignment old : previous) {
                old.end();
                shootingAssignmentRepository.save(old);
                reassignmentAssigneeRepository.save(new ReassignmentAssignee(record, old.getCameraperson(),
                        RoleType.CAMERAPERSON, AssigneeSide.PREVIOUS));
            }
            for (UUID newUserId : newAssigneeUserIds) {
                User newUser = userRepository.findById(newUserId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + newUserId));
                shootingAssignmentRepository.save(new ShootingAssignment(plan, newUser, actor));
                reassignmentAssigneeRepository.save(new ReassignmentAssignee(record, newUser, RoleType.CAMERAPERSON,
                        AssigneeSide.NEW));
            }
        } else {
            List<EditingAssignment> previous = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
            for (EditingAssignment old : previous) {
                old.end();
                editingAssignmentRepository.save(old);
                reassignmentAssigneeRepository.save(new ReassignmentAssignee(record, old.getEditor(), RoleType.EDITOR,
                        AssigneeSide.PREVIOUS));
            }
            for (UUID newUserId : newAssigneeUserIds) {
                User newUser = userRepository.findById(newUserId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + newUserId));
                editingAssignmentRepository.save(new EditingAssignment(plan, newUser, actor));
                reassignmentAssigneeRepository.save(new ReassignmentAssignee(record, newUser, RoleType.EDITOR,
                        AssigneeSide.NEW));
            }
        }
        auditService.record(actor, actingGrant, "ADMIN_ACTION", "REASSIGNED", "content_plans", plan.getId(), reason);
        return record;
    }

    /** ERD-CON-006: blocked once ever Completed. */
    @Transactional
    public CancellationRecord cancel(User actor, UUID contentPlanId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A cancellation reason is mandatory");
        }
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.everCompleted()) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Cancellation is blocked once the deliverable has ever been Completed (ERD-CON-006)");
        }
        requireNotClosed(workflowInstance, "Cancel");
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_12_CANCEL, LifecycleStage.ADMINISTRATIVE, workflowInstance);

        workflowService.transition(workflowInstance, WorkflowStatus.CAN, actor,
                actingGrant, "CANCEL", reason);
        CancellationRecord record = cancellationRecordRepository.save(
                new CancellationRecord(workflowInstance, reason, actor, actingGrant.orElse(null)));
        auditService.record(actor, actingGrant, "ADMIN_ACTION", "CANCELLED", "content_plans", plan.getId(), reason);
        return record;
    }

    /** Permission #8 (Publishing reopen: COMP-&gt;RFP) or #9 (Metric correction reopen: COMP-&gt;PFUP). */
    @Transactional
    public ReopenRecord reopenCompleted(User actor, UUID contentPlanId, ReopenPurpose purpose, String reason) {
        WorkflowStatus target = purpose == ReopenPurpose.METRIC_CORRECTION_REOPEN ? WorkflowStatus.PFUP : WorkflowStatus.PUBG;
        OperationalPermission permission = purpose == ReopenPurpose.METRIC_CORRECTION_REOPEN
                ? OperationalPermission.PERM_09_PERFORMANCE_UPDATE : OperationalPermission.PERM_08_PUBLISHING_EXECUTION;
        return reopenCompleted(actor, contentPlanId, purpose, permission, target, reason);
    }

    /** API-OP-052: Reopen Completed Deliverable for Publishing (Permission #8), COMP -&gt; PUBG. */
    @Transactional
    public ReopenRecord reopenForPublishing(User actor, UUID contentPlanId, String reason) {
        return reopenCompleted(actor, contentPlanId, ReopenPurpose.PUBLISHING_REOPEN,
                OperationalPermission.PERM_08_PUBLISHING_EXECUTION, WorkflowStatus.PUBG, reason);
    }

    /** API-OP-053: Reopen Completed Deliverable for Metric Correction (Permission #9), COMP -&gt; PFUP. */
    @Transactional
    public ReopenRecord reopenForPerformance(User actor, UUID contentPlanId, String reason) {
        return reopenCompleted(actor, contentPlanId, ReopenPurpose.METRIC_CORRECTION_REOPEN,
                OperationalPermission.PERM_09_PERFORMANCE_UPDATE, WorkflowStatus.PFUP, reason);
    }

    private ReopenRecord reopenCompleted(User actor, UUID contentPlanId, ReopenPurpose purpose,
                                          OperationalPermission permission, WorkflowStatus target, String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A reopen reason is mandatory for a Completed deliverable");
        }
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.COMP) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Reopen Completed is only valid while the deliverable is Completed");
        }
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor, permission,
                LifecycleStage.ADMINISTRATIVE, workflowInstance);

        workflowService.transition(workflowInstance, target, actor, actingGrant,
                "REOPEN_COMPLETED", reason);
        ReopenRecord record = reopenRecordRepository.save(new ReopenRecord(workflowInstance, WorkflowStatus.COMP, target,
                purpose, reason, actor, actingGrant.orElse(null)));
        auditService.record(actor, actingGrant, "ADMIN_ACTION", "DELIVERABLE_REOPENED", "content_plans", plan.getId(), reason);
        return record;
    }
}
