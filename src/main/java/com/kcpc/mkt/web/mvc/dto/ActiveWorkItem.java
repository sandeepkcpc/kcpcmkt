package com.kcpc.mkt.web.mvc.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "/app/my-work" Active Work row - one of the logged-in Employee's own currently-active Shoot,
 * Edit, or Publish assignments, normalized to one shape so all three stages render through the
 * same table (ENG-057/058/068). Plain class, not a record: rendered directly by a JSP, whose EL
 * only recognizes getX() JavaBean accessors, not a record's canonical accessors (ENG-031).
 */
public class ActiveWorkItem {

    private final UUID contentPlanId;
    private final String contentId;
    private final String title;
    private final String roleLabel;
    private final String priority;
    private final String priorityCssClass;
    private final LocalDate plannedDate;
    private final String leadName;
    private final boolean shootLead;
    private final String models;
    private final String statusLabel;
    private final String statusCssClass;
    private final Integer delayDays;
    private final String actionLabel;
    private final String driveLink;
    private final String targetsSummary;
    private final boolean onHold;
    private final String stage;
    private final boolean repost;
    private final boolean executionBlocked;

    public ActiveWorkItem(UUID contentPlanId, String contentId, String title, String roleLabel, String priority,
                           String priorityCssClass, LocalDate plannedDate, String leadName, boolean shootLead,
                           String models, String statusLabel, String statusCssClass, Integer delayDays,
                           String actionLabel, String driveLink, String targetsSummary, boolean onHold,
                           String stage, boolean repost, boolean executionBlocked) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.title = title;
        this.roleLabel = roleLabel;
        this.priority = priority;
        this.priorityCssClass = priorityCssClass;
        this.plannedDate = plannedDate;
        this.leadName = leadName;
        this.shootLead = shootLead;
        this.models = models;
        this.statusLabel = statusLabel;
        this.statusCssClass = statusCssClass;
        this.delayDays = delayDays;
        this.actionLabel = actionLabel;
        this.driveLink = driveLink;
        this.targetsSummary = targetsSummary;
        this.onHold = onHold;
        this.stage = stage;
        this.repost = repost;
        this.executionBlocked = executionBlocked;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getContentId() {
        return contentId;
    }

    public String getTitle() {
        return title;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public String getPriority() {
        return priority;
    }

    public String getPriorityCssClass() {
        return priorityCssClass;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public String getLeadName() {
        return leadName;
    }

    public boolean isShootLead() {
        return shootLead;
    }

    public String getModels() {
        return models;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusCssClass() {
        return statusCssClass;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    public boolean isDelayed() {
        return delayDays != null;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public String getDriveLink() {
        return driveLink;
    }

    /** ENG-068: "resolved/total" Publication Target count - null for Shoot/Edit rows, only ever set for Publisher rows. */
    public String getTargetsSummary() {
        return targetsSummary;
    }

    /**
     * BR-063 Hold/Resume: an open {@code WorkHoldRecord} exists for this row's workflow instance.
     * The row stays visible in My Work either way (never moved to History) - the JSP suppresses
     * the primary action button while true, since an ordinary Employee never holds the native
     * authority Resume requires (they see a read-only On Hold state, not a Resume control).
     */
    public boolean isOnHold() {
        return onHold;
    }

    /** SHOOT / EDIT / PUBLISH - the stage badge, independent of the executor's own roleLabel/Business Role. */
    public String getStage() {
        return stage;
    }

    /** True only for a Publishing row currently in a reopened (repost) cycle - see PublishingService#currentPublishingCycleStart. */
    public boolean isRepost() {
        return repost;
    }

    /**
     * Permission-driven workflow: true when this row's active assignment is real, but the
     * assignee's own execution permission (PERM_18/19/08) no longer covers it - e.g. revoked after
     * assignment. The task stays visible (never hidden/deleted); the JSP suppresses the execution
     * action and shows a "permission removed / reassignment required" message instead.
     */
    public boolean isExecutionBlocked() {
        return executionBlocked;
    }
}
