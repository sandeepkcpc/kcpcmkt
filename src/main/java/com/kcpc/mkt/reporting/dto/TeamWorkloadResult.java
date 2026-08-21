package com.kcpc.mkt.reporting.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ENG-087: Team Workload dashboard's full response - two deliberately separate aggregations
 * (see the individual card javadocs on the JSP): {@code stageCounts} answers "how much active
 * content is in each lifecycle stage right now" (a {@link com.kcpc.mkt.workflow.domain.WorkflowStatus}
 * bucket count, one Content ID counted once), {@code assigneeRows} answers "how much actionable
 * work does each employee actually have" (their own live assignment/participation count - never
 * derived from, or forced to match, the stage bucket a plan happens to be in). Plain class, not a
 * record: rendered directly by a JSP, whose EL only recognizes getX() JavaBean accessors (ENG-031).
 */
public class TeamWorkloadResult {

    private final Map<String, Long> stageCounts;
    private final long totalActiveByStage;
    private final List<AssigneeLoadRow> assigneeRows;
    private final long totalActiveAssignee;
    private final long totalDelayedAssignee;
    private final long totalOnHoldAssignee;
    private final Instant generatedAt;

    public TeamWorkloadResult(Map<String, Long> stageCounts, long totalActiveByStage, List<AssigneeLoadRow> assigneeRows,
                               long totalActiveAssignee, long totalDelayedAssignee, long totalOnHoldAssignee,
                               Instant generatedAt) {
        this.stageCounts = stageCounts;
        this.totalActiveByStage = totalActiveByStage;
        this.assigneeRows = assigneeRows;
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

    public List<AssigneeLoadRow> getAssigneeRows() {
        return assigneeRows;
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
