package com.kcpc.mkt.reporting.dto;

import java.util.List;
import java.util.UUID;

/**
 * Team Workload dashboard - one row per employee in the "Assignee Load" table (spec: "each
 * employee appears only once"), aggregated from that employee's own per-(stage, employee)
 * {@link AssigneeLoadRow}s - never a second, independently-computed source. {@code delayedTasks}/
 * {@code onHoldTasks} are a genuine SUM over those stage rows, unchanged. {@code activeTasks} is
 * NOT a sum - it is the count of DISTINCT Content Plan ids across every stage row's own
 * {@code items} (see {@link com.kcpc.mkt.reporting.service.TeamWorkloadService#teamWorkloadDashboard}),
 * since the same Content ID
 * can carry more than one of this employee's roles at once (e.g. Model + Cameraperson on the same
 * Content ID while Shoot is active) - counting each role's own stage row would double-count that
 * one Content ID as multiple "active tasks." This means {@code activeTasks} can legitimately be
 * LESS than the sum of the {@code activeTasks} shown across {@code stageBreakdown}'s own rows.
 * {@code stageBreakdown} carries only the stages where this employee actually has non-zero
 * workload (spec: a stage with zero workload may be omitted from the expanded section). Plain
 * class, not a record: rendered directly by a JSP, whose EL only recognizes getX() JavaBean
 * accessors (ENG-031 precedent).
 */
public class EmployeeWorkloadRow {

    private final UUID userId;
    private final String assigneeName;
    private final String businessRoleName;
    private final long activeTasks;
    private final long delayedTasks;
    private final long onHoldTasks;
    private final List<AssigneeLoadRow> stageBreakdown;

    public EmployeeWorkloadRow(UUID userId, String assigneeName, String businessRoleName, long activeTasks,
                                long delayedTasks, long onHoldTasks, List<AssigneeLoadRow> stageBreakdown) {
        this.userId = userId;
        this.assigneeName = assigneeName;
        this.businessRoleName = businessRoleName;
        this.activeTasks = activeTasks;
        this.delayedTasks = delayedTasks;
        this.onHoldTasks = onHoldTasks;
        this.stageBreakdown = stageBreakdown;
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

    public List<AssigneeLoadRow> getStageBreakdown() {
        return stageBreakdown;
    }
}
