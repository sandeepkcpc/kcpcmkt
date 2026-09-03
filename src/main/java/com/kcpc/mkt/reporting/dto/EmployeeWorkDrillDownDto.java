package com.kcpc.mkt.reporting.dto;

import java.util.List;
import java.util.UUID;

/** Reports -&gt; KPI Dashboard -&gt; Overview -&gt; Current Work Ownership -&gt; "Open" drill-down for
 * one employee. {@code pendingItems} includes every currently-delayed item too (delay is a
 * supplementary flag on the same pending population, not a separate one); {@code delayedItems} is
 * the filtered subset. Read-only reporting data only - no action is ever exposed from this DTO. */
public class EmployeeWorkDrillDownDto {

    private final UUID employeeId;
    private final String employeeName;
    private final String roleName;
    private final List<EmployeeWorkItemRow> pendingItems;
    private final List<EmployeeWorkItemRow> delayedItems;

    public EmployeeWorkDrillDownDto(UUID employeeId, String employeeName, String roleName,
                                     List<EmployeeWorkItemRow> pendingItems, List<EmployeeWorkItemRow> delayedItems) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.roleName = roleName;
        this.pendingItems = pendingItems;
        this.delayedItems = delayedItems;
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

    public List<EmployeeWorkItemRow> getPendingItems() {
        return pendingItems;
    }

    public List<EmployeeWorkItemRow> getDelayedItems() {
        return delayedItems;
    }

    public int getPendingCount() {
        return pendingItems.size();
    }

    public int getDelayedCount() {
        return delayedItems.size();
    }
}
