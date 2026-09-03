package com.kcpc.mkt.reporting.dto;

import java.util.UUID;

/**
 * Reports -&gt; KPI Dashboard -&gt; Overview -&gt; Current Work Ownership: one row per employee
 * currently holding at least one active Shoot/Edit/Publishing assignment (governed by
 * {@code AssigneeActiveWindows}, the same "is this stage still this role's own active window"
 * rule Team Workload's Assignee Load already uses). {@code pendingCount} is person-wise (a
 * Content ID assigned to two Camerapersons contributes to both rows independently, never
 * deduplicated) and includes every currently-delayed item too - delay is a supplementary flag on
 * top of pending, not a separate population (see {@code delayedCount} &lt;= {@code pendingCount}).
 * {@code oldestDelayDays} is {@code null} when {@code delayedCount == 0}.
 */
public class CurrentWorkOwnershipRow {

    private final UUID employeeId;
    private final String employeeName;
    private final String roleName;
    private final long pendingCount;
    private final long delayedCount;
    private final Integer oldestDelayDays;

    public CurrentWorkOwnershipRow(UUID employeeId, String employeeName, String roleName, long pendingCount,
                                    long delayedCount, Integer oldestDelayDays) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.roleName = roleName;
        this.pendingCount = pendingCount;
        this.delayedCount = delayedCount;
        this.oldestDelayDays = oldestDelayDays;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getRoleName() {
        return roleName;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public long getDelayedCount() {
        return delayedCount;
    }

    public Integer getOldestDelayDays() {
        return oldestDelayDays;
    }
}
