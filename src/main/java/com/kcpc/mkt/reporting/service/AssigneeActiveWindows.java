package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.workflow.domain.WorkflowStatus;

import java.util.EnumSet;
import java.util.Set;

/**
 * Single source of truth for "is this stage still this role's own active window" - shared by
 * {@link TeamWorkloadService}'s Assignee Load panel and {@link AssigneeWorkloadCountService}'s
 * assignee-picker active-task counts, so the two can never disagree (an assignee-selection
 * dropdown's "N Active Tasks" must always mean exactly what Team Workload already means by it,
 * never a second, independently-drifting definition). An assignment whose Content Plan has already
 * moved past that role's own stage doesn't count as active for that role, even if the assignment
 * row itself was never explicitly ended (e.g. a Cameraperson's Shoot assignment stops counting once
 * the plan reaches Edit).
 */
public final class AssigneeActiveWindows {

    public static final Set<WorkflowStatus> SHOOT = EnumSet.of(
            WorkflowStatus.SA, WorkflowStatus.SIP, WorkflowStatus.SRV);
    public static final Set<WorkflowStatus> EDIT = EnumSet.of(
            WorkflowStatus.EA, WorkflowStatus.ED, WorkflowStatus.ERV);
    public static final Set<WorkflowStatus> PUBLISHING = EnumSet.of(
            WorkflowStatus.RFP, WorkflowStatus.PUBG);

    /** The plan-level "not yet closed out" set - used to pre-filter which Content Plans are even
     * considered for Team Workload at all ({@link TeamWorkloadService#isActiveStatus}). NOT a
     * per-role active window on its own: Model/Talent's own Active Tasks count is gated by
     * {@link #SHOOT} specifically (see {@code TeamWorkloadService#modelRow} - Model's work is tied
     * to the Shoot stage, so it stops counting once Shoot is completed or skipped, exactly like
     * Cameraperson's own Shoot row), not by this broader "not yet closed" set. */
    public static final Set<WorkflowStatus> CLOSED_OUT = EnumSet.of(
            WorkflowStatus.COMP, WorkflowStatus.CAN, WorkflowStatus.RJ, WorkflowStatus.RET);

    private AssigneeActiveWindows() {
    }
}
