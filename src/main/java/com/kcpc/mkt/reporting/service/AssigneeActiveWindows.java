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

    /** Model/Talent has no Start/Review-gated window of its own (BRS-REQ: talent participation, not
     * a workflow-assignable stage) - "active" is simply "not yet in a closed-out status," the exact
     * set {@link TeamWorkloadService#isActiveStatus} already excludes. */
    public static final Set<WorkflowStatus> CLOSED_OUT = EnumSet.of(
            WorkflowStatus.COMP, WorkflowStatus.CAN, WorkflowStatus.RJ, WorkflowStatus.RET);

    private AssigneeActiveWindows() {
    }
}
