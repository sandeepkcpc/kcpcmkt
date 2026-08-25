package com.kcpc.mkt.workflow.dto;

import com.kcpc.mkt.workflow.domain.WorkflowStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One actionable row in the My Work -> Assignment Management queue (Shoot or Edit) - a Content ID
 * where the logged-in user can currently perform SOME assignment-management action right now
 * (initial team setup via PERM_04/PERM_06, or reassignment via PERM_11), never a historical or
 * merely-existing-assignment list. See AssignmentManagementQueueService for the eligibility rule.
 * <p>
 * A plain class with JavaBean getters (not a record) so JSP EL's BeanELResolver can read its
 * properties directly (record-style accessors, e.g. {@code contentId()}, aren't resolved by ${q.contentId}).
 */
public class AssignmentQueueRow {

    private final UUID planId;
    private final String contentId;
    private final String title;
    private final WorkflowStatus status;
    private final LocalDate relevantDate;
    private final List<String> currentAssigneeNames;
    private final String leadName;
    private final String actionLabel;

    public AssignmentQueueRow(UUID planId, String contentId, String title, WorkflowStatus status,
                               LocalDate relevantDate, List<String> currentAssigneeNames, String leadName,
                               String actionLabel) {
        this.planId = planId;
        this.contentId = contentId;
        this.title = title;
        this.status = status;
        this.relevantDate = relevantDate;
        this.currentAssigneeNames = currentAssigneeNames;
        this.leadName = leadName;
        this.actionLabel = actionLabel;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getContentId() {
        return contentId;
    }

    public String getTitle() {
        return title;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public LocalDate getRelevantDate() {
        return relevantDate;
    }

    public List<String> getCurrentAssigneeNames() {
        return currentAssigneeNames;
    }

    public String getLeadName() {
        return leadName;
    }

    public String getActionLabel() {
        return actionLabel;
    }
}
