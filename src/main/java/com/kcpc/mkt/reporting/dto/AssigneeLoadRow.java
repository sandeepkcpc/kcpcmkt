package com.kcpc.mkt.reporting.dto;

import java.util.List;
import java.util.UUID;

/**
 * ENG-087: Team Workload dashboard's per-(employee, stage) workload unit - one active employee's
 * actual current assignments/participation within exactly one stage, counting - never a
 * lifecycle-stage count (that's {@code stageCounts} on {@link TeamWorkloadResult}, a deliberately
 * separate aggregation). {@code stage} (added for the employee-wise UI update) is the stage this
 * specific row's counts belong to ("Shoot"/"Edit"/"Publishing"/"Model") - {@link
 * EmployeeWorkloadRow} sums these per employee's Delayed/On Hold for the main table (Active Tasks
 * is instead a distinct-Content-ID count across every row's own {@code items} - see that class's
 * javadoc), and keeps the surviving ones as that employee's own stage-wise drill-down.
 * {@code items} (Content ID drill-down) is one {@link WorkloadContentItem} per surviving
 * assignment/participation record counted into {@code activeTasks} - captured from the SAME
 * records already being iterated to compute the counts, so {@code items.size() == activeTasks}
 * always, by construction, never a second query and never independently deduplicated (a Content
 * ID can legitimately repeat here if the underlying data genuinely produced two distinct surviving
 * records for it - see TeamWorkloadService's own investigation note on this). Plain class, not a
 * record: rendered directly by a JSP, whose EL only recognizes getX() JavaBean accessors (ENG-031).
 */
public class AssigneeLoadRow {

    private final UUID userId;
    private final String assigneeName;
    private final String businessRoleName;
    private final String stage;
    private final long activeTasks;
    private final long delayedTasks;
    private final long onHoldTasks;
    private final List<WorkloadContentItem> items;

    public AssigneeLoadRow(UUID userId, String assigneeName, String businessRoleName, String stage,
                            long activeTasks, long delayedTasks, long onHoldTasks, List<WorkloadContentItem> items) {
        this.userId = userId;
        this.assigneeName = assigneeName;
        this.businessRoleName = businessRoleName;
        this.stage = stage;
        this.activeTasks = activeTasks;
        this.delayedTasks = delayedTasks;
        this.onHoldTasks = onHoldTasks;
        this.items = items;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public String getBusinessRoleName() {
        return businessRoleName;
    }

    public String getStage() {
        return stage;
    }

    public long getActiveTasks() {
        return activeTasks;
    }

    public long getDelayedTasks() {
        return delayedTasks;
    }

    public long getOnHoldTasks() {
        return onHoldTasks;
    }

    public List<WorkloadContentItem> getItems() {
        return items;
    }
}
