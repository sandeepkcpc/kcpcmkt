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
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.EditingExecutionParticipant;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.EditingExecutionParticipantRepository;
import com.kcpc.mkt.publishing.service.PublishingService;
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
    private final OperationalEligibilityService operationalEligibilityService;
    private final HoldService holdService;
    private final AuditService auditService;
    private final PublishingService publishingService;
    private final UserRepository userRepository;

    public EditingService(ContentPlanRepository contentPlanRepository,
                           EditingAssignmentRepository editingAssignmentRepository,
                           EditingExecutionParticipantRepository participantRepository,
                           PredefinedRoleMarksRepository predefinedRoleMarksRepository,
                           PersonalMarkAttributionRepository attributionRepository,
                           ReviewCycleRepository reviewCycleRepository, WorkflowTransitionService workflowService,
                           AuthorizationService authorizationService,
                           OperationalEligibilityService operationalEligibilityService, HoldService holdService,
                           AuditService auditService, PublishingService publishingService,
                           UserRepository userRepository) {
        this.contentPlanRepository = contentPlanRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.participantRepository = participantRepository;
        this.predefinedRoleMarksRepository = predefinedRoleMarksRepository;
        this.attributionRepository = attributionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
        this.operationalEligibilityService = operationalEligibilityService;
        this.holdService = holdService;
        this.auditService = auditService;
        this.publishingService = publishingService;
        this.userRepository = userRepository;
    }

    private ContentPlan requirePlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    /**
     * ERD-CON-013 / SAD-DES-018: initial Editor assignment blocked prior to Shoot Approval (SAP).
     * Idempotent: re-assigning an Editor who already holds an active assignment on this plan returns the
     * existing row rather than inserting a duplicate (the chip-picker UI can safely re-POST a still-checked
     * box).
     */
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
        // Assignee-side eligibility, evaluated against EDITING (the stage being executed) - see
        // OperationalEligibilityService. Frontend candidate filtering is not authorization.
        operationalEligibilityService.requireEditExecutionEligible(editor, workflowInstance);
        Optional<EditingAssignment> existing =
                editingAssignmentRepository.findByContentPlanAndEditorAndActiveTrue(plan, editor);
        if (existing.isPresent()) {
            return existing.get();
        }
        EditingAssignment assignment = editingAssignmentRepository.save(new EditingAssignment(plan, editor, assigner));
        if (status == WorkflowStatus.SAP) {
            workflowService.transition(workflowInstance, WorkflowStatus.EA, assigner, Optional.empty(),
                    "ASSIGN_EDITOR", null);
        }
        auditService.record(assigner, Optional.empty(), "EDITING", "EDITOR_ASSIGNED", "editing_assignments",
                assignment.getId(), null);
        return assignment;
    }

    /** Removes an active editing assignment, mirroring the same status/Permission #6 window as assign. */
    @Transactional
    public void removeEditor(User actor, UUID contentPlanId, UUID editorUserId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        WorkflowStatus status = workflowInstance.getCurrentStatusCode();
        if (status != WorkflowStatus.SAP && status != WorkflowStatus.EA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Editing assignment can only be removed after Shoot Approval (ERD-CON-013)");
        }
        authorizationService.requireAuthority(actor, OperationalPermission.PERM_06_EDIT_ASSIGNMENT,
                LifecycleStage.EDITING, workflowInstance);
        EditingAssignment assignment = editingAssignmentRepository.findByContentPlan(plan).stream()
                .filter(EditingAssignment::isActive)
                .filter(a -> a.getEditor().getId().equals(editorUserId))
                .findFirst()
                .orElseThrow(() -> DomainException.notFound("No active editing assignment for this Editor"));
        assignment.end();
        editingAssignmentRepository.save(assignment);
        auditService.record(actor, Optional.empty(), "EDITING", "EDITOR_UNASSIGNED", "editing_assignments",
                assignment.getId(), null);
    }

    /**
     * Edit Lead (not in the frozen ERD - see ENG-036): {@code editorUserId == null} clears the
     * Lead. Otherwise it must be one of the plan's currently active Editors - the Lead dropdown is
     * a subset of Editor(s), never an independent selection.
     */
    @Transactional
    public void setEditLead(User actor, UUID contentPlanId, UUID editorUserId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        WorkflowStatus status = workflowInstance.getCurrentStatusCode();
        if (status != WorkflowStatus.SAP && status != WorkflowStatus.EA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Edit Lead can only be set after Shoot Approval (ERD-CON-013)");
        }
        authorizationService.requireAuthority(actor, OperationalPermission.PERM_06_EDIT_ASSIGNMENT,
                LifecycleStage.EDITING, workflowInstance);
        List<EditingAssignment> active = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        // The partial unique index (V16) checks per-statement, not deferred to commit - the old
        // Lead's clear must actually flush to the DB before the new Lead's set is issued, or
        // Postgres would momentarily see two active Leads and reject it.
        active.forEach(a -> a.setLead(false));
        editingAssignmentRepository.saveAll(active);
        editingAssignmentRepository.flush();
        if (editorUserId != null) {
            EditingAssignment target = active.stream()
                    .filter(a -> a.getEditor().getId().equals(editorUserId))
                    .findFirst()
                    .orElseThrow(() -> DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "Edit Lead must be one of the currently assigned Editors"));
            target.setLead(true);
            editingAssignmentRepository.save(target);
        }
        auditService.record(actor, Optional.empty(), "EDITING",
                editorUserId != null ? "EDIT_LEAD_SET" : "EDIT_LEAD_CLEARED", "editing_assignments",
                plan.getId(), null);
    }

    /**
     * Single-button "Assign Editor(s)" (ENG-041): assigns every newly-staged Editor and sets the
     * Edit Lead in one request. Composed by calling {@link #assignEditor} and {@link #setEditLead}
     * directly (self-invocation on the same bean bypasses their own {@code @Transactional} proxies)
     * so both steps commit or roll back together as one transaction, exactly as the user asked - not
     * two sequential AJAX calls under one button.
     */
    @Transactional
    public void assignEditTeam(User actor, UUID contentPlanId, List<User> editors, UUID leadUserId) {
        if (editors != null) {
            for (User editor : editors) {
                assignEditor(actor, contentPlanId, editor);
            }
        }
        setEditLead(actor, contentPlanId, leadUserId);
    }

    /**
     * ENG-046: one Edit Description shared by the whole Editor team on this plan (not per
     * individual assignee), editable any time by whoever holds PERM_06_EDIT_ASSIGNMENT (the same
     * authority that governs Edit Assignment itself) - not restricted to a particular workflow
     * status, since CEO/MM should be able to update instructions for an already-assigned team too.
     */
    @Transactional
    public ContentPlan updateEditDescription(User actor, UUID contentPlanId, String description) {
        ContentPlan plan = requirePlan(contentPlanId);
        authorizationService.requireAuthority(actor, OperationalPermission.PERM_06_EDIT_ASSIGNMENT,
                LifecycleStage.EDITING, plan.getWorkflowInstance());
        plan.setEditDescription(description);
        contentPlanRepository.save(plan);
        auditService.record(actor, Optional.empty(), "EDITING", "EDIT_DESCRIPTION_UPDATED", "content_plans",
                plan.getId(), null);
        return plan;
    }

    /**
     * Gated to an actively assigned Editor only, NOT native CEO/MM authority (see
     * docs/IMPLEMENTATION_DECISIONS.md ENG-013, revised by ENG-043: CEO/MM's native authority
     * covers management actions - Assign, Review decisions, monitoring - not hands-on execution
     * of an Employee's own task, so it deliberately does not bypass this check). Historically no
     * CEO-Granted Operational Permission existed for the edit-start/submit-for-review acts
     * themselves; PERM_19_EDIT_EXECUTION now also gates them explicitly (see
     * {@link #startEditing}/{@link #submitEditReview}, via OperationalEligibilityService) - this
     * active-assignee check remains a separate, additional requirement, not replaced by it.
     */
    private void requireActiveAssignee(User actor, ContentPlan plan) {
        boolean isAssignee = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getEditor().getId().equals(actor.getId()));
        if (!isAssignee) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    "Only an assigned Editor can perform this action");
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
        operationalEligibilityService.requireEditExecutionEligible(actor, workflowInstance);
        requireActiveAssignee(actor, plan);
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
        operationalEligibilityService.requireEditExecutionEligible(submitter, workflowInstance);
        requireActiveAssignee(submitter, plan);
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

    /**
     * Workflow redesign: Publisher team assignment is now folded directly into the SAME Approve
     * action as the Edit Review decision itself, rather than a separate step on a separate screen -
     * mirroring {@link ShootingService#decideShootReview}'s Editor fold-in, which itself mirrors
     * how Idea Review approval folds in the initial Shoot Team. {@code publisherUserIds} is
     * required on Approve only (ignored for Request Rework). Unlike Editor/Cameraperson
     * assignment, Publisher assignment has no Lead concept (explicit product decision - see
     * ENG-036/ENG-044) - just a required, non-empty Publisher(s) list. Reuses
     * {@link PublishingService#assignPublisherTeam} unchanged (same ENG-044 native-CEO/MM-only
     * authorization, same eligibility checks, same idempotent assignment logic) rather than
     * duplicating any of that business logic - called from within this same {@code @Transactional}
     * method so the Edit Review decision and the Publisher team assignment commit or roll back
     * together atomically. ERV -&gt; EAP -&gt; RFP still fire here as before; Publisher assignment
     * happens once status is RFP (assignPublisher's own window), still inside this one
     * transaction. Publishing itself only starts once the assigned Publisher clicks Start
     * Publishing (RFP -&gt; PUBG), unchanged - mirroring Edit's own EA -&gt; ED handoff.
     */
    @Transactional
    public ContentPlan decideEditReview(User reviewer, UUID contentPlanId, boolean approve, String reason,
                                         List<UUID> qualifyingRecipientUserIds, List<UUID> publisherUserIds) {
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
            if (publisherUserIds == null || publisherUserIds.isEmpty()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "At least one Publisher must be assigned before approval");
            }
            // Resolved up front (before any mutation below) so an invalid publisher id fails fast.
            List<User> publishers = new ArrayList<>();
            for (UUID publisherId : publisherUserIds) {
                publishers.add(userRepository.findById(publisherId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + publisherId)));
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

            publishingService.assignPublisherTeam(reviewer, contentPlanId, publishers);
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

    /**
     * Skip Stage (ENG-090): mirrors {@link ShootingService#skipShootStage} - a controlled,
     * permission-gated escape hatch usable from EA/ED/ERV (before or after an Editor ever submits
     * for review), no self-review-prohibited check (no execution participants exist to have been
     * one of), no performance mark attributed. Still collects the same required Publisher(s) the
     * normal Approve path would, via {@link PublishingService#assignPublisherTeam} - no Lead
     * field, exactly as Publisher Assignment already has none (ENG-036/ENG-044). Gated by
     * PERM_20_SKIP_STAGE, never by PERM_07_EDIT_REVIEW.
     */
    @Transactional
    public ContentPlan skipEditStage(User actor, UUID contentPlanId, String reason, List<UUID> publisherUserIds) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        WorkflowStatus status = workflowInstance.getCurrentStatusCode();
        if (status != WorkflowStatus.EA && status != WorkflowStatus.ED && status != WorkflowStatus.ERV) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Edit stage can only be skipped while Edit Assigned, Editing, or Under Review");
        }
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_20_SKIP_STAGE, LifecycleStage.EDITING, workflowInstance);
        holdService.requireNoOpenHold(workflowInstance);

        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A reason is mandatory to skip this stage");
        }
        if (publisherUserIds == null || publisherUserIds.isEmpty()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "At least one Publisher must be assigned to skip this stage");
        }
        // Resolved up front (before any mutation below) so an invalid publisher id fails fast.
        List<User> publishers = new ArrayList<>();
        for (UUID publisherId : publisherUserIds) {
            publishers.add(userRepository.findById(publisherId)
                    .orElseThrow(() -> DomainException.notFound("User not found: " + publisherId)));
        }

        if (status == WorkflowStatus.ERV) {
            reviewCycleRepository
                    .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, GateType.EDIT_REVIEW)
                    .stream().filter(c -> c.getDecidedAt() == null).findFirst()
                    .ifPresent(cycle -> {
                        cycle.decide(actor, "SKIPPED", reason, actingGrant.orElse(null));
                        reviewCycleRepository.save(cycle);
                    });
        }

        workflowService.transition(workflowInstance, WorkflowStatus.RFP, actor, actingGrant,
                "SKIP_EDIT_STAGE", reason);
        auditService.record(actor, actingGrant, "EDITING", "EDIT_STAGE_SKIPPED", "content_plans",
                plan.getId(), reason);

        publishingService.assignPublisherTeam(actor, contentPlanId, publishers);
        return plan;
    }

    private int nextCycleNumber(WorkflowInstance workflowInstance, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType)
                .stream().findFirst().map(c -> c.getCycleNumber() + 1).orElse(1);
    }
}
