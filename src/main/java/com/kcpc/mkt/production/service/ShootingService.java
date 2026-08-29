package com.kcpc.mkt.production.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.service.OperationalEligibilityService;
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

import java.util.ArrayList;
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
    private final OperationalEligibilityService operationalEligibilityService;
    private final HoldService holdService;
    private final AuditService auditService;
    private final EditingService editingService;
    private final UserRepository userRepository;

    public ShootingService(ContentPlanRepository contentPlanRepository,
                            ShootingAssignmentRepository shootingAssignmentRepository,
                            ShootingExecutionParticipantRepository participantRepository,
                            PredefinedRoleMarksRepository predefinedRoleMarksRepository,
                            PersonalMarkAttributionRepository attributionRepository,
                            ReviewCycleRepository reviewCycleRepository, WorkflowTransitionService workflowService,
                            AuthorizationService authorizationService,
                            OperationalEligibilityService operationalEligibilityService, HoldService holdService,
                            AuditService auditService, EditingService editingService, UserRepository userRepository) {
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.participantRepository = participantRepository;
        this.predefinedRoleMarksRepository = predefinedRoleMarksRepository;
        this.attributionRepository = attributionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
        this.operationalEligibilityService = operationalEligibilityService;
        this.holdService = holdService;
        this.auditService = auditService;
        this.editingService = editingService;
        this.userRepository = userRepository;
    }

    private ContentPlan requirePlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    /**
     * Gated to an actively assigned Cameraperson only, NOT native CEO/MM authority (see
     * docs/IMPLEMENTATION_DECISIONS.md ENG-013, revised by ENG-043: CEO/MM's native authority
     * covers management actions - Assign, Review decisions, monitoring - not hands-on execution
     * of an Employee's own task, so it deliberately does not bypass this check). Historically no
     * CEO-Granted Operational Permission existed for the shoot-start/submit-for-review acts
     * themselves; PERM_18_SHOOT_EXECUTION now also gates them explicitly (see
     * {@link #startShooting}/{@link #submitShootReview}, via OperationalEligibilityService) -
     * this active-assignee check remains a separate, additional requirement, not replaced by it.
     */
    private void requireActiveAssignee(User actor, ContentPlan plan) {
        boolean isAssignee = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getCameraperson().getId().equals(actor.getId()));
        if (!isAssignee) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    "Only an assigned Cameraperson can perform this action");
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
        operationalEligibilityService.requireShootExecutionEligible(actor, workflowInstance);
        requireActiveAssignee(actor, plan);
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
        operationalEligibilityService.requireShootExecutionEligible(submitter, workflowInstance);
        requireActiveAssignee(submitter, plan);
        if (plan.getFolderLink() == null || plan.getFolderLink().isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Drive Link is required before Shoot Review");
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
        return decideShootReview(reviewer, contentPlanId, approve, reason, qualifyingRecipientUserIds, null, null);
    }

    /**
     * Workflow redesign: Editor team assignment (incl. Editor Lead) is now folded directly into
     * the SAME Approve action as the Shoot Review decision itself, rather than a separate step on
     * a separate screen - matching how Idea Review approval already folds in the initial Shoot
     * Team. {@code editorUserIds}/{@code leadEditorUserId} are required on Approve only (ignored
     * for Request Rework, which needs no next-team assignment). Reuses
     * {@link EditingService#assignEditTeam} unchanged (same Permission #6 authorization, same
     * eligibility checks, same idempotent assignment/Lead logic) rather than duplicating any of
     * that business logic - called from within this same {@code @Transactional} method so the
     * Shoot Review decision and the Editor team assignment commit or roll back together atomically.
     * SRV -&gt; SAP -&gt; EA both fire here in one transaction (assignEditTeam's first assignment call
     * auto-advances SAP -&gt; EA, exactly as it already does for the standalone Assign Editor(s) action).
     */
    @Transactional
    public ContentPlan decideShootReview(User reviewer, UUID contentPlanId, boolean approve, String reason,
                                          List<UUID> qualifyingRecipientUserIds, List<UUID> editorUserIds,
                                          UUID leadEditorUserId) {
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
            if (editorUserIds == null || editorUserIds.isEmpty()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "At least one Editor must be assigned before approval");
            }
            if (leadEditorUserId == null) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Editor Lead is mandatory");
            }
            if (!editorUserIds.contains(leadEditorUserId)) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Editor Lead must be one of the selected Editor(s)");
            }
            // Resolved up front (before any mutation below) so an invalid editor id fails fast,
            // same pattern IdeaService#approve already uses for the initial Shoot Team.
            List<User> editors = new ArrayList<>();
            for (UUID editorId : editorUserIds) {
                editors.add(userRepository.findById(editorId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + editorId)));
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

            editingService.assignEditTeam(reviewer, contentPlanId, editors, leadEditorUserId);
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
