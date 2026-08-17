package com.kcpc.mkt.production.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.marks.domain.PersonalMarkAttribution;
import com.kcpc.mkt.marks.domain.PredefinedRoleMarks;
import com.kcpc.mkt.marks.domain.RoleType;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.EditingExecutionParticipant;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.EditingExecutionParticipantRepository;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import com.kcpc.mkt.workflow.service.HoldService;
import com.kcpc.mkt.workflow.service.WorkflowTransitionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** BRS-REQ-034..037: Editor assignment (post-Shoot-Approval only) and the Edit Review gate (Permission #7). */
@Service
public class EditingService {

    private final ContentPlanRepository contentPlanRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final EditingExecutionParticipantRepository participantRepository;
    private final PredefinedRoleMarksRepository predefinedRoleMarksRepository;
    private final PersonalMarkAttributionRepository attributionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionService workflowService;
    private final AuthorizationService authorizationService;
    private final HoldService holdService;
    private final AuditService auditService;

    public EditingService(ContentPlanRepository contentPlanRepository,
                           EditingAssignmentRepository editingAssignmentRepository,
                           EditingExecutionParticipantRepository participantRepository,
                           PredefinedRoleMarksRepository predefinedRoleMarksRepository,
                           PersonalMarkAttributionRepository attributionRepository,
                           ReviewCycleRepository reviewCycleRepository, WorkflowTransitionService workflowService,
                           AuthorizationService authorizationService, HoldService holdService,
                           AuditService auditService) {
        this.contentPlanRepository = contentPlanRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.participantRepository = participantRepository;
        this.predefinedRoleMarksRepository = predefinedRoleMarksRepository;
        this.attributionRepository = attributionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
        this.holdService = holdService;
        this.auditService = auditService;
    }

    private ContentPlan requirePlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    /** ERD-CON-013 / SAD-DES-018: initial Editor assignment blocked prior to Shoot Approval (SAP). */
    @Transactional
    public EditingAssignment assignEditor(User assigner, UUID contentPlanId, User editor) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        WorkflowStatus status = workflowInstance.getCurrentStatusCode();
        if (status != WorkflowStatus.SAP && status != WorkflowStatus.EA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Editor assignment is only valid after Shoot Approval (ERD-CON-013)");
        }
        authorizationService.requireAuthority(assigner, OperationalPermission.PERM_06_EDIT_ASSIGNMENT,
                LifecycleStage.EDITING, workflowInstance);
        EditingAssignment assignment = editingAssignmentRepository.save(new EditingAssignment(plan, editor, assigner));
        if (status == WorkflowStatus.SAP) {
            workflowService.transition(workflowInstance, WorkflowStatus.EA, assigner, Optional.empty(),
                    "ASSIGN_EDITOR", null);
        }
        auditService.record(assigner, Optional.empty(), "EDITING", "EDITOR_ASSIGNED", "editing_assignments",
                assignment.getId(), null);
        return assignment;
    }

    private void requireAssigneeOrNative(User actor, ContentPlan plan) {
        if (authorizationService.hasNativeAuthority(actor)) {
            return;
        }
        boolean isAssignee = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getEditor().getId().equals(actor.getId()));
        if (!isAssignee) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    "Only an assigned Editor or CEO/MM can perform this action");
        }
    }

    @Transactional
    public void startEditing(User actor, UUID contentPlanId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.EA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Editing can only start once Edit Assigned");
        }
        requireAssigneeOrNative(actor, plan);
        workflowService.transition(workflowInstance, WorkflowStatus.ED, actor, Optional.empty(), "START_EDITING", null);
        auditService.record(actor, Optional.empty(), "EDITING", "EDITING_STARTED", "content_plans", plan.getId(), null);
    }

    @Transactional
    public ReviewCycle submitEditReview(User submitter, UUID contentPlanId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.ED) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Edit Review can only be submitted while Editing");
        }
        holdService.requireNoOpenHold(workflowInstance);
        requireAssigneeOrNative(submitter, plan);
        if (plan.getFolderLink() == null || plan.getFolderLink().isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Drive Link is required before Edit Review");
        }

        List<EditingAssignment> activeAssignments = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        for (EditingAssignment assignment : activeAssignments) {
            participantRepository.save(new EditingExecutionParticipant(assignment, plan, assignment.getEditor()));
        }

        int cycleNumber = nextCycleNumber(workflowInstance, GateType.EDIT_REVIEW);
        ReviewCycle cycle = reviewCycleRepository.save(new ReviewCycle(workflowInstance, GateType.EDIT_REVIEW,
                cycleNumber, submitter));
        workflowService.transition(workflowInstance, WorkflowStatus.ERV, submitter, Optional.empty(),
                "SUBMIT_EDIT_REVIEW", null);
        auditService.record(submitter, Optional.empty(), "EDITING", "EDIT_REVIEW_SUBMITTED", "content_plans",
                plan.getId(), null);
        return cycle;
    }

    @Transactional
    public ContentPlan decideEditReview(User reviewer, UUID contentPlanId, boolean approve, String reason,
                                         List<UUID> qualifyingRecipientUserIds) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.ERV) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Edit Review decisions are only valid while under review");
        }
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(reviewer,
                OperationalPermission.PERM_07_EDIT_REVIEW, LifecycleStage.EDITING, workflowInstance);
        List<EditingExecutionParticipant> participants = participantRepository.findByContentPlan(plan);
        boolean isParticipant = participants.stream().anyMatch(p -> p.getEditor().getId().equals(reviewer.getId()));
        if (actingGrant.isPresent() && isParticipant) {
            throw DomainException.forbidden(ErrorCode.PERM_SELF_APPROVAL_PROHIBITED,
                    "Cannot make a review decision on an edit you participated in");
        }

        ReviewCycle cycle = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, GateType.EDIT_REVIEW)
                .stream().filter(c -> c.getDecidedAt() == null).findFirst()
                .orElseThrow(() -> DomainException.notFound("No pending Edit Review submission found"));

        if (approve) {
            if (qualifyingRecipientUserIds == null || qualifyingRecipientUserIds.isEmpty()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "At least one qualifying final Editor must be confirmed on Approve");
            }
            PredefinedRoleMarks marks = predefinedRoleMarksRepository.findByContentPlan(plan)
                    .orElseThrow(() -> DomainException.notFound("Predefined Marks not found for this Content Plan"));
            cycle.decide(reviewer, "APPROVED", null, actingGrant.orElse(null));
            reviewCycleRepository.save(cycle);

            Set<UUID> distinctRecipients = new HashSet<>(qualifyingRecipientUserIds);
            for (UUID recipientId : distinctRecipients) {
                EditingExecutionParticipant participant = participants.stream()
                        .filter(p -> p.getEditor().getId().equals(recipientId)).findFirst()
                        .orElseThrow(() -> DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                                "Recipient " + recipientId + " was not a recorded edit participant"));
                attributionRepository.save(new PersonalMarkAttribution(participant.getEditor(), RoleType.EDITOR,
                        plan, cycle, marks, marks.getPredefinedEditorMark()));
            }
            workflowService.transition(workflowInstance, WorkflowStatus.EAP, reviewer, actingGrant,
                    "APPROVE_EDIT", null);
            workflowService.transition(workflowInstance, WorkflowStatus.RFP, reviewer, actingGrant,
                    "READY_FOR_PUBLISHING", null);
            auditService.record(reviewer, actingGrant, "EDITING", "EDIT_APPROVED", "content_plans", plan.getId(), null);
        } else {
            if (reason == null || reason.isBlank()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A rework reason is mandatory (ERD-CON-059)");
            }
            cycle.decide(reviewer, "REQUEST_REWORK", reason, actingGrant.orElse(null));
            reviewCycleRepository.save(cycle);
            workflowService.transition(workflowInstance, WorkflowStatus.ED, reviewer, actingGrant,
                    "REQUEST_REWORK_EDIT", reason);
            auditService.record(reviewer, actingGrant, "EDITING", "EDIT_REWORK_REQUESTED", "content_plans",
                    plan.getId(), reason);
        }
        return plan;
    }

    private int nextCycleNumber(WorkflowInstance workflowInstance, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType)
                .stream().findFirst().map(c -> c.getCycleNumber() + 1).orElse(1);
    }
}
