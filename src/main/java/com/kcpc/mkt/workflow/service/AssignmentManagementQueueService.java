package com.kcpc.mkt.workflow.service;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.dto.AssignmentQueueRow;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * My Work -&gt; Assignment Management: an actionable queue, never a historical/broader assignment
 * list (that remains the CEO/MM Content Pipeline's job). A Content ID appears only when the
 * logged-in user can currently perform SOME Shoot/Edit assignment-management action on it right
 * now - populated from the same authorization/workflow-state rules the real action endpoints use,
 * never a hard-coded status list:
 * <ul>
 *   <li>PERM_04_SHOOT_ASSIGNMENT / PERM_06_EDIT_ASSIGNMENT: initial/current team setup, exactly the
 *       window {@link com.kcpc.mkt.planning.service.PlanningService#assignCameraperson}/
 *       {@link com.kcpc.mkt.production.service.EditingService#assignEditor} themselves enforce
 *       (Shoot: status SA - workflow redesign: an initial Shoot team is always already assigned at
 *       Idea Review approval time, so this is really the "still adjustable" window, not "not yet
 *       assigned"; Edit: status SAP or EA).</li>
 *   <li>PERM_11_REASSIGN: everything else the plan is still open for - reassignment
 *       ({@link com.kcpc.mkt.workflow.service.AdminActionService#reassign}) has no stage gate of
 *       its own beyond "not Cancelled/Completed/Rejected", so its queue-relevant window here is
 *       simply the complement of the initial-assignment window while the plan remains open.</li>
 * </ul>
 * The moment neither condition holds any more for a plan, it drops out of the queue automatically
 * (nothing is cached/flagged - this always reflects live state).
 */
@Service
public class AssignmentManagementQueueService {

    private final ContentPlanRepository contentPlanRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final AuthorizationService authorizationService;

    public AssignmentManagementQueueService(ContentPlanRepository contentPlanRepository,
                                             ShootingAssignmentRepository shootingAssignmentRepository,
                                             EditingAssignmentRepository editingAssignmentRepository,
                                             AuthorizationService authorizationService) {
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.authorizationService = authorizationService;
    }

    private boolean allowed(User user, OperationalPermission permission, LifecycleStage stage,
                             WorkflowInstance workflowInstance) {
        try {
            authorizationService.requireAuthority(user, permission, stage, workflowInstance);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    private boolean isOpen(WorkflowStatus status) {
        return status != WorkflowStatus.CAN && status != WorkflowStatus.COMP && status != WorkflowStatus.RJ;
    }

    public List<AssignmentQueueRow> shootQueue(User user) {
        return contentPlanRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(plan -> {
                    WorkflowInstance wi = plan.getWorkflowInstance();
                    WorkflowStatus status = wi.getCurrentStatusCode();
                    List<ShootingAssignment> active = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
                    boolean canInitialAssign = status == WorkflowStatus.SA
                            && allowed(user, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, LifecycleStage.PLANNING, wi);
                    boolean canReassign = status != WorkflowStatus.SA && isOpen(status)
                            && allowed(user, OperationalPermission.PERM_11_REASSIGN, LifecycleStage.ADMINISTRATIVE, wi);
                    if (!canInitialAssign && !canReassign) {
                        return null;
                    }
                    String actionLabel = active.isEmpty() ? "Set Up Shoot Team"
                            : (canInitialAssign ? "Manage Assignment" : "Reassign Team");
                    String lead = active.stream().filter(ShootingAssignment::isLead)
                            .map(a -> a.getCameraperson().getFullName()).findFirst().orElse(null);
                    return new AssignmentQueueRow(plan.getId(), plan.getContentId(), plan.getIdea().getTitle(), status,
                            plan.getPlannedShootDate(), active.stream().map(a -> a.getCameraperson().getFullName()).toList(),
                            lead, actionLabel);
                })
                .filter(row -> row != null)
                .toList();
    }

    public List<AssignmentQueueRow> editQueue(User user) {
        return contentPlanRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(plan -> {
                    WorkflowInstance wi = plan.getWorkflowInstance();
                    WorkflowStatus status = wi.getCurrentStatusCode();
                    List<EditingAssignment> active = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
                    boolean canInitialAssign = (status == WorkflowStatus.SAP || status == WorkflowStatus.EA)
                            && allowed(user, OperationalPermission.PERM_06_EDIT_ASSIGNMENT, LifecycleStage.EDITING, wi);
                    boolean canReassign = status != WorkflowStatus.SAP && status != WorkflowStatus.EA && isOpen(status)
                            && allowed(user, OperationalPermission.PERM_11_REASSIGN, LifecycleStage.ADMINISTRATIVE, wi);
                    if (!canInitialAssign && !canReassign) {
                        return null;
                    }
                    String actionLabel = active.isEmpty() ? "Set Up Edit Team"
                            : (canInitialAssign ? "Manage Assignment" : "Reassign Team");
                    String lead = active.stream().filter(EditingAssignment::isLead)
                            .map(a -> a.getEditor().getFullName()).findFirst().orElse(null);
                    return new AssignmentQueueRow(plan.getId(), plan.getContentId(), plan.getIdea().getTitle(), status,
                            plan.getPlannedEditDate(), active.stream().map(a -> a.getEditor().getFullName()).toList(),
                            lead, actionLabel);
                })
                .filter(row -> row != null)
                .toList();
    }
}
