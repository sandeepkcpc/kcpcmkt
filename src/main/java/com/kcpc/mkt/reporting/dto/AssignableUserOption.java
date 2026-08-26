package com.kcpc.mkt.reporting.dto;

import java.util.UUID;

/**
 * A candidate in an assignee-selection UI (Shoot/Edit/Publisher/Model pickers), paired with their
 * current active-task count - the same "active" definition {@code TeamWorkloadService}'s Assignee
 * Load panel already uses (see {@code AssigneeActiveWindows}), so a picker's displayed count and
 * Team Workload's can never disagree. A drop-in stand-in for {@code User} in these model
 * attributes: {@link #getId()}/{@link #getFullName()} mirror {@code User}'s own accessors so
 * existing JSP EL ({@code ${u.id}}, {@code ${u.fullName}}) needs no changes anywhere it already
 * appears - only the new {@link #getActiveTaskCount()}/{@link #getActiveTaskLabel()} are additive.
 */
public class AssignableUserOption {

    private final UUID id;
    private final String fullName;
    private final long activeTaskCount;

    public AssignableUserOption(UUID id, String fullName, long activeTaskCount) {
        this.id = id;
        this.fullName = fullName;
        this.activeTaskCount = activeTaskCount;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public long getActiveTaskCount() {
        return activeTaskCount;
    }

    /** JSP-ready "N Active Task(s)" label - singular only at exactly 1, matching the singular/
     * plural convention {@code DisplayNumber.days()} already uses elsewhere in this codebase. */
    public String getActiveTaskLabel() {
        return activeTaskCount + " Active Task" + (activeTaskCount == 1 ? "" : "s");
    }
}
