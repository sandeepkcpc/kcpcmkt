package com.kcpc.mkt.web.mvc.dto;

import com.kcpc.mkt.reporting.dto.PipelinePlatformSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * "/app/my-work" Upcoming Work row (ENG-097): a Publisher's own {@code PublishingAssignment}
 * whose Content Plan has NOT yet entered the Publishing active workflow window ({@code RFP}/
 * {@code PUBG}) and has not been closed out either - i.e. genuinely still pre-Publishing (Shoot/
 * Edit in progress, or freshly Planning-assigned). Publisher(s) can now be assigned at Idea Review
 * approval time (see {@code IdeaService#approve}), long before the content reaches Publishing -
 * this row exists so that assignment is visible from the moment it's made, without waiting for
 * Publishing to actually activate and without being misclassified as "completed" (that
 * misclassification, in the old two-bucket Active/Completed split, is the bug this DTO fixes).
 *
 * <p>Deliberately a separate, smaller shape from {@link ActiveWorkItem} - Upcoming has no status/
 * action concept of its own (Publishing execution hasn't started), only "which stage is the
 * content actually in right now" (see {@code LandingMvcController#stageLabel}). {@code delayDays}
 * (added for the My Work -&gt; Dashboard tab) reuses the EXACT SAME "Planned Live Date before today"
 * formula {@link ActiveWorkItem} already uses - never a second/different delay calculation - so an
 * Upcoming row that's already past its Planned Live Date can still be flagged, without waiting for
 * Publishing to activate first. Plain class, not a record: rendered directly by a JSP, whose EL
 * only recognizes getX() JavaBean accessors, not a record's canonical accessors (ENG-031).
 */
public class UpcomingWorkItem {

    private final UUID contentPlanId;
    private final String contentId;
    private final String title;
    private final String priority;
    private final String priorityCssClass;
    private final LocalDate plannedDate;
    private final String currentStage;
    private final String targetsSummary;
    private final String driveLink;
    private final Integer delayDays;
    private final List<PipelinePlatformSummary> platformSummaries;

    public UpcomingWorkItem(UUID contentPlanId, String contentId, String title, String priority,
                             String priorityCssClass, LocalDate plannedDate, String currentStage,
                             String targetsSummary, String driveLink, Integer delayDays,
                             List<PipelinePlatformSummary> platformSummaries) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.title = title;
        this.priority = priority;
        this.priorityCssClass = priorityCssClass;
        this.plannedDate = plannedDate;
        this.currentStage = currentStage;
        this.targetsSummary = targetsSummary;
        this.driveLink = driveLink;
        this.delayDays = delayDays;
        this.platformSummaries = platformSummaries;
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

    public String getPriority() {
        return priority;
    }

    public String getPriorityCssClass() {
        return priorityCssClass;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    /** Which stage the Content Plan is actually in right now (Shoot/Edit/Publishing) - never the assignment's own role/stage. */
    public String getCurrentStage() {
        return currentStage;
    }

    /** "resolved / total" Publication Target count, same shape as {@link ActiveWorkItem#getTargetsSummary()}. */
    public String getTargetsSummary() {
        return targetsSummary;
    }

    public String getDriveLink() {
        return driveLink;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    /** Same "delayDays != null" convention as {@link ActiveWorkItem#isDelayed()}. */
    public boolean isDelayed() {
        return delayDays != null;
    }

    /** Platforms column (renders via the shared fragments/pipeline-platform-chip.jspf) - same
     * {@link PipelinePlatformSummary} data/rules as Content Pipeline and Content Detail, built by
     * {@code PipelineDashboardService#buildPlatformSummariesForPlan}. */
    public List<PipelinePlatformSummary> getPlatformSummaries() {
        return platformSummaries;
    }
}
