package com.kcpc.mkt.reporting.dto;

import java.util.UUID;

/**
 * ENG-087: Team Workload dashboard's "Assignee Load" table - one row per active employee of an
 * operationally-assignable Business Role (Camera Person/Video Editor/Publisher/Model), counting
 * their actual current assignments/participation - never a lifecycle-stage count (that's
 * {@code stageCounts} on {@link TeamWorkloadResult}, a deliberately separate aggregation). Plain
 * class, not a record: rendered directly by a JSP, whose EL only recognizes getX() JavaBean
 * accessors (ENG-031).
 */
public class AssigneeLoadRow {

    private final UUID userId;
    private final String assigneeName;
    private final String businessRoleName;
    private final long activeTasks;
    private final long delayedTasks;
    private final long onHoldTasks;

    public AssigneeLoadRow(UUID userId, String assigneeName, String businessRoleName, long activeTasks,
                            long delayedTasks, long onHoldTasks) {
        this.userId = userId;
        this.assigneeName = assigneeName;
        this.businessRoleName = businessRoleName;
        this.activeTasks = activeTasks;
        this.delayedTasks = delayedTasks;
        this.onHoldTasks = onHoldTasks;
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

    public long getActiveTasks() {
        return activeTasks;
    }

    public long getDelayedTasks() {
        return delayedTasks;
    }

    public long getOnHoldTasks() {
        return onHoldTasks;
    }
}
