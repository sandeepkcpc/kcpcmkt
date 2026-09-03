package com.kcpc.mkt.reporting.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ENG-087: Team Workload dashboard's full response - two deliberately separate aggregations
 * (see the individual card javadocs on the JSP): {@code stageCounts} answers "how much active
 * content is in each lifecycle stage right now" (a {@link com.kcpc.mkt.workflow.domain.WorkflowStatus}
 * bucket count, one Content ID counted once), {@code employeeRows} answers "how much actionable
 * work does each employee actually have" (one row per employee, derived from their own real
 * assignment/participation rows - never forced to match the stage bucket a plan happens to be in;
 * see {@link EmployeeWorkloadRow}'s own javadoc for why its Active Tasks is a distinct-Content-ID
 * count, not a sum, while Delayed/On Hold stay a plain sum). {@code employeeRows} (employee-wise
 * UI update) replaced the previous flat
 * {@code assigneeRows} (one row per employee PER STAGE, so the same employee could appear
 * multiple times) - the per-stage detail is still available, nested inside each
 * {@link EmployeeWorkloadRow#getStageBreakdown()}. Plain class, not a record: rendered directly
 * by a JSP, whose EL only recognizes getX() JavaBean accessors (ENG-031).
 */
public class TeamWorkloadResult {

    private final Map<String, Long> stageCounts;
    private final long totalActiveByStage;
    private final List<EmployeeWorkloadRow> employeeRows;
    private final long totalActiveAssignee;
    private final long totalDelayedAssignee;
    private final long totalOnHoldAssignee;
    private final Instant generatedAt;

    public TeamWorkloadResult(Map<String, Long> stageCounts, long totalActiveByStage,
                               List<EmployeeWorkloadRow> employeeRows, long totalActiveAssignee,
                               long totalDelayedAssignee, long totalOnHoldAssignee, Instant generatedAt) {
        this.stageCounts = stageCounts;
        this.totalActiveByStage = totalActiveByStage;
        this.employeeRows = employeeRows;
        this.totalActiveAssignee = totalActiveAssignee;
        this.totalDelayedAssignee = totalDelayedAssignee;
        this.totalOnHoldAssignee = totalOnHoldAssignee;
        this.generatedAt = generatedAt;
    }

    public Map<String, Long> getStageCounts() {
        return stageCounts;
    }

    public long getTotalActiveByStage() {
        return totalActiveByStage;
    }

    public List<EmployeeWorkloadRow> getEmployeeRows() {
        return employeeRows;
    }

    public long getTotalActiveAssignee() {
        return totalActiveAssignee;
    }

    public long getTotalDelayedAssignee() {
        return totalDelayedAssignee;
    }

    public long getTotalOnHoldAssignee() {
        return totalOnHoldAssignee;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
