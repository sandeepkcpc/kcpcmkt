package com.kcpc.mkt.reporting.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One item in an employee's Current Work Ownership drill-down (Reports -&gt; KPI Dashboard -&gt;
 * Overview) - either a Pending Work row or a Delayed Work row (same shape; the JSP shows only the
 * columns relevant to whichever tab is active). {@code stageLabel} is the coarse Shoot/Edit/
 * Publishing bucket this item was found in; {@code statusLabel} is the plan's own human-readable
 * current status ({@link com.kcpc.mkt.workflow.domain.WorkflowStatus#getStatusName()}), never a
 * raw enum code. {@code delayDays} is {@code null} for an on-time item.
 */
public class EmployeeWorkItemRow {

    private final UUID contentPlanId;
    private final String contentId;
    private final String contentTitle;
    private final String stageLabel;
    private final String statusLabel;
    private final LocalDate plannedDueDate;
    private final String priorityLabel;
    private final Integer delayDays;

    public EmployeeWorkItemRow(UUID contentPlanId, String contentId, String contentTitle, String stageLabel,
                                String statusLabel, LocalDate plannedDueDate, String priorityLabel, Integer delayDays) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.contentTitle = contentTitle;
        this.stageLabel = stageLabel;
        this.statusLabel = statusLabel;
        this.plannedDueDate = plannedDueDate;
        this.priorityLabel = priorityLabel;
        this.delayDays = delayDays;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getContentId() {
        return contentId;
    }

    public String getContentTitle() {
        return contentTitle;
    }

    public String getStageLabel() {
        return stageLabel;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public LocalDate getPlannedDueDate() {
        return plannedDueDate;
    }

    public String getPriorityLabel() {
        return priorityLabel;
    }

    public Integer getDelayDays() {
        return delayDays;
    }
}
