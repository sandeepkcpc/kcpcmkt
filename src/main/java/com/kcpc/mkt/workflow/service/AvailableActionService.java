package com.kcpc.mkt.workflow.service;

import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.workflow.domain.ContentCanonicalStage;
import com.kcpc.mkt.workflow.domain.TaskStage;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Single source of truth for "is this admin action actually valid right now" (workflow-state and
 * assignment eligibility only - never permission) - used by BOTH Content Detail's Action Center
 * (UI visibility, {@code DeliverableMvcController}) and the real POST handlers
 * ({@link AdminActionService}), so a visible Action Center button always means the backend expects
 * the action to succeed (subject to normal form validation), never a UI-only rule that can diverge
 * from what the backend will actually accept.
 */
@Service
public class AvailableActionService {

    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;

    public AvailableActionService(ShootingAssignmentRepository shootingAssignmentRepository,
                                   EditingAssignmentRepository editingAssignmentRepository) {
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
    }

    public boolean isNotClosed(WorkflowInstance workflowInstance) {
        WorkflowStatus status = workflowInstance.getCurrentStatusCode();
        return status != WorkflowStatus.CAN && status != WorkflowStatus.COMP && status != WorkflowStatus.RJ;
    }

    /** Reschedule's only real backend rule (AdminActionService#reschedule) is "not closed" - no
     * per-stage restriction exists anywhere in the domain, so none is invented here. */
    public boolean isReschedulable(WorkflowInstance workflowInstance) {
        return isNotClosed(workflowInstance);
    }

    /** ERD-CON-006: blocked once the deliverable has EVER been Completed, not just "not currently
     * Completed" - mirrors AdminActionService#cancel exactly. */
    public boolean isCancellable(ContentPlan plan) {
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        return isNotClosed(workflowInstance) && !workflowInstance.everCompleted();
    }

    /**
     * Reassign eligibility, per taskStage.
     * <p>SHOOTING: valid while the canonical stage is still Planning or Shoot - the only window in
     * which the Shoot team is ever actually mutable (PlanningService#assignCameraperson/
     * removeCameraperson themselves only operate at PL, and nothing in the domain marks a
     * ShootingAssignment "finalized" before Edit begins) - AND an active ShootingAssignment
     * currently exists to reassign.
     * <p>EDITING: valid while the canonical stage is Edit - EditingService#assignEditor's own
     * window is SAP/EA, but assigning at SAP atomically transitions the workflow to EA within the
     * same call, so by the time an EditingAssignment actually exists, the canonical stage is always
     * already Edit - AND an active EditingAssignment currently exists to reassign.
     * <p>Once the canonical stage moves past its own task stage (the Shoot team once Edit begins;
     * the Edit team once Publishing/Performance begins), that assignment is historically finalized
     * and no longer reassignable through this action - it never carries forward as a stale action
     * into a later stage.
     */
    public boolean isReassignEligible(ContentPlan plan, TaskStage taskStage) {
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (!isNotClosed(workflowInstance)) {
            return false;
        }
        ContentCanonicalStage stage = ContentCanonicalStage.forStatus(workflowInstance.getCurrentStatusCode());
        return switch (taskStage) {
            case SHOOTING -> (stage == ContentCanonicalStage.PLANNING || stage == ContentCanonicalStage.SHOOTING)
                    && !shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).isEmpty();
            case EDITING -> stage == ContentCanonicalStage.EDITING
                    && !editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).isEmpty();
        };
    }

    public boolean isAnyReassignEligible(ContentPlan plan) {
        return isReassignEligible(plan, TaskStage.SHOOTING) || isReassignEligible(plan, TaskStage.EDITING);
    }

    /** The Task Stage options Content Detail's Reassign form should actually offer right now -
     * never the unconditional full {@code TaskStage.values()} - so a visible dropdown option always
     * means submitting it is expected to succeed, same contract as the button itself. */
    public List<TaskStage> eligibleReassignTaskStages(ContentPlan plan) {
        return Arrays.stream(TaskStage.values()).filter(ts -> isReassignEligible(plan, ts)).toList();
    }

    /** Reopen Completed (Permission #8/#9) - AdminActionService#reopenCompleted's own rule is
     * COMP-only, mirrored here so the Action Center and the backend never diverge. */
    public boolean isReopenEligible(WorkflowInstance workflowInstance) {
        return workflowInstance.getCurrentStatusCode() == WorkflowStatus.COMP;
    }
}
