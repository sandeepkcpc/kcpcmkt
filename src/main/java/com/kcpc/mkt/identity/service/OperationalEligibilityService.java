package com.kcpc.mkt.identity.service;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Single source of truth for "is this user eligible to be selected/assigned/execute Shoot, Edit,
 * or Publishing work" - permission-driven, never Business-Role-name-based, never satisfied by
 * CEO/MM native authority alone (see {@link AuthorizationService#hasExplicitPermissionGrant}).
 * Eligibility is always evaluated against the STAGE BEING EXECUTED, not the screen an assignment
 * happens to be made from: Shoot candidacy/execution is PERM_18 scoped to LifecycleStage.SHOOTING
 * even though initial Shoot assignment happens on the Planning screen - a Shoot execution grant
 * never needs to also cover PLANNING.
 * <p>
 * Used by: assignment candidate pickers (Shoot/Edit/Publisher), assignment/reassignment backend
 * validation, and Start/Submit execution checks. Publishing execution (PERM_08) already existed
 * before this service - it is wrapped here unchanged so all three stages share one call shape.
 */
@Service
public class OperationalEligibilityService {

    private final AuthorizationService authorizationService;

    public OperationalEligibilityService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public boolean isShootExecutionEligible(User user, WorkflowInstance item) {
        return eligible(user, OperationalPermission.PERM_18_SHOOT_EXECUTION, LifecycleStage.SHOOTING, item);
    }

    public boolean isEditExecutionEligible(User user, WorkflowInstance item) {
        return eligible(user, OperationalPermission.PERM_19_EDIT_EXECUTION, LifecycleStage.EDITING, item);
    }

    public boolean isPublishingExecutionEligible(User user, WorkflowInstance item) {
        return eligible(user, OperationalPermission.PERM_08_PUBLISHING_EXECUTION, LifecycleStage.PUBLISHING, item);
    }

    public void requireShootExecutionEligible(User user, WorkflowInstance item) {
        require(isShootExecutionEligible(user, item), "Shoot execution");
    }

    public void requireEditExecutionEligible(User user, WorkflowInstance item) {
        require(isEditExecutionEligible(user, item), "Edit execution");
    }

    public void requirePublishingExecutionEligible(User user, WorkflowInstance item) {
        require(isPublishingExecutionEligible(user, item), "Publishing execution");
    }

    /** Shoot Assignee/Reassignee candidate picker: active users holding a live PERM_18 grant for this item. */
    public List<User> shootExecutionCandidates(WorkflowInstance item) {
        return authorizationService.findActiveGranteesWithExplicitGrant(
                OperationalPermission.PERM_18_SHOOT_EXECUTION, LifecycleStage.SHOOTING, item);
    }

    /** Edit Assignee/Reassignee candidate picker: active users holding a live PERM_19 grant for this item. */
    public List<User> editExecutionCandidates(WorkflowInstance item) {
        return authorizationService.findActiveGranteesWithExplicitGrant(
                OperationalPermission.PERM_19_EDIT_EXECUTION, LifecycleStage.EDITING, item);
    }

    /** Publisher candidate picker: active users holding a live PERM_08 grant for this item. */
    public List<User> publishingExecutionCandidates(WorkflowInstance item) {
        return authorizationService.findActiveGranteesWithExplicitGrant(
                OperationalPermission.PERM_08_PUBLISHING_EXECUTION, LifecycleStage.PUBLISHING, item);
    }

    private boolean eligible(User user, OperationalPermission permission, LifecycleStage stage, WorkflowInstance item) {
        return user.isActive() && authorizationService.hasExplicitPermissionGrant(user, permission, stage, item);
    }

    private void require(boolean eligible, String actionDescription) {
        if (!eligible) {
            throw DomainException.forbidden(ErrorCode.PERM_OPERATIONAL_PERMISSION_REQUIRED,
                    actionDescription + " requires an explicit, currently valid permission grant covering this "
                            + "stage/item - CEO/MM native authority alone is not sufficient");
        }
    }
}
