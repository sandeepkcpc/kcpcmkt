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
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.domain.ShootingExecutionParticipant;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingExecutionParticipantRepository;
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

/** BRS-REQ-031..033: Shooting execution and the Shoot Review gate (Permission #5). */
@Service
public class ShootingService {

    private final ContentPlanRepository contentPlanRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final ShootingExecutionParticipantRepository participantRepository;
    private final PredefinedRoleMarksRepository predefinedRoleMarksRepository;
    private final PersonalMarkAttributionRepository attributionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionService workflowService;
    private final AuthorizationService authorizationService;
    private final HoldService holdService;
    private final AuditService auditService;

    public ShootingService(ContentPlanRepository contentPlanRepository,
                            ShootingAssignmentRepository shootingAssignmentRepository,
                            ShootingExecutionParticipantRepository participantRepository,
                            PredefinedRoleMarksRepository predefinedRoleMarksRepository,
                            PersonalMarkAttributionRepository attributionRepository,
                            ReviewCycleRepository reviewCycleRepository, WorkflowTransitionService workflowService,
                            AuthorizationService authorizationService, HoldService holdService,
                            AuditService auditService) {
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
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

    /**
     * No CEO-Granted Operational Permission exists for the shoot-start/submit-for-review acts
     * themselves (only Assignment #4 and Review #5 are catalogued) - gated to native CEO/MM or
     * an actively assigned Cameraperson (see docs/IMPLEMENTATION_DECISIONS.md ENG-013).
     */
    private void requireAssigneeOrNative(User actor, ContentPlan plan) {
        if (authorizationService.hasNativeAuthority(actor)) {
            return;
        }
        boolean isAssignee = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getCameraperson().getId().equals(actor.getId()));
        if (!isAssignee) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    "Only an assigned Cameraperson or CEO/MM can perform this action");
        }
    }

    @Transactional
    public void startShooting(User actor, UUID contentPlanId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.SA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Shooting can only start once Shoot Assigned");
        }
        requireAssigneeOrNative(actor, plan);
        workflowService.transition(workflowInstance, WorkflowStatus.SIP, actor, Optional.empty(),
                "START_SHOOTING", null);
        auditService.record(actor, Optional.empty(), "SHOOTING", "SHOOTING_STARTED", "content_plans",
                plan.getId(), null);
    }

    @Transactional
    public ReviewCycle submitShootReview(User submitter, UUID contentPlanId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.SIP) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Shoot Review can only be submitted while Shoot In Progress");
        }
        holdService.requireNoOpenHold(workflowInstance);
        requireAssigneeOrNative(submitter, plan);
        if (plan.getFolderLink() == null || plan.getFolderLink().isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Content Asset Folder Link is required before Shoot Review");
        }

        List<ShootingAssignment> activeAssignments = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        for (ShootingAssignment assignment : activeAssignments) {
            participantRepository.save(new ShootingExecutionParticipant(assignment, plan, assignment.getCameraperson()));
        }

        int cycleNumber = nextCycleNumber(workflowInstance, GateType.SHOOT_REVIEW);
        ReviewCycle cycle = reviewCycleRepository.save(new ReviewCycle(workflowInstance, GateType.SHOOT_REVIEW,
                cycleNumber, submitter));
        workflowService.transition(workflowInstance, WorkflowStatus.SRV, submitter, Optional.empty(),
                "SUBMIT_SHOOT_REVIEW", null);
        auditService.record(submitter, Optional.empty(), "SHOOTING", "SHOOT_REVIEW_SUBMITTED", "content_plans",
                plan.getId(), null);
        return cycle;
    }

    @Transactional
    public ContentPlan decideShootReview(User reviewer, UUID contentPlanId, boolean approve, String reason,
                                          List<UUID> qualifyingRecipientUserIds) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.SRV) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Shoot Review decisions are only valid while under review");
        }
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(reviewer,
                OperationalPermission.PERM_05_SHOOT_REVIEW, LifecycleStage.SHOOTING, workflowInstance);
        List<ShootingExecutionParticipant> participants = participantRepository.findByContentPlan(plan);
        boolean isParticipant = participants.stream().anyMatch(p -> p.getCameraperson().getId().equals(reviewer.getId()));
        if (actingGrant.isPresent() && isParticipant) {
            throw DomainException.forbidden(ErrorCode.PERM_SELF_APPROVAL_PROHIBITED,
                    "Cannot make a review decision on a shoot you participated in");
        }

        ReviewCycle cycle = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, GateType.SHOOT_REVIEW)
                .stream().filter(c -> c.getDecidedAt() == null).findFirst()
                .orElseThrow(() -> DomainException.notFound("No pending Shoot Review submission found"));

        if (approve) {
            if (qualifyingRecipientUserIds == null || qualifyingRecipientUserIds.isEmpty()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "At least one qualifying final Cameraperson must be confirmed on Approve");
            }
            PredefinedRoleMarks marks = predefinedRoleMarksRepository.findByContentPlan(plan)
                    .orElseThrow(() -> DomainException.notFound("Predefined Marks not found for this Content Plan"));
            cycle.decide(reviewer, "APPROVED", null, actingGrant.orElse(null));
            reviewCycleRepository.save(cycle);

            Set<UUID> distinctRecipients = new HashSet<>(qualifyingRecipientUserIds);
            for (UUID recipientId : distinctRecipients) {
                ShootingExecutionParticipant participant = participants.stream()
                        .filter(p -> p.getCameraperson().getId().equals(recipientId)).findFirst()
                        .orElseThrow(() -> DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                                "Recipient " + recipientId + " was not a recorded shoot participant"));
                attributionRepository.save(new PersonalMarkAttribution(participant.getCameraperson(),
                        RoleType.CAMERAPERSON, plan, cycle, marks, marks.getPredefinedCameramanMark()));
            }
            workflowService.transition(workflowInstance, WorkflowStatus.SAP, reviewer, actingGrant,
                    "APPROVE_SHOOT", null);
            auditService.record(reviewer, actingGrant, "SHOOTING", "SHOOT_APPROVED", "content_plans", plan.getId(), null);
        } else {
            if (reason == null || reason.isBlank()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A rework reason is mandatory (ERD-CON-059)");
            }
            cycle.decide(reviewer, "REQUEST_REWORK", reason, actingGrant.orElse(null));
            reviewCycleRepository.save(cycle);
            workflowService.transition(workflowInstance, WorkflowStatus.SIP, reviewer, actingGrant,
                    "REQUEST_REWORK_SHOOT", reason);
            auditService.record(reviewer, actingGrant, "SHOOTING", "SHOOT_REWORK_REQUESTED", "content_plans",
                    plan.getId(), reason);
        }
        return plan;
    }

    private int nextCycleNumber(WorkflowInstance workflowInstance, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType)
                .stream().findFirst().map(c -> c.getCycleNumber() + 1).orElse(1);
    }
}
