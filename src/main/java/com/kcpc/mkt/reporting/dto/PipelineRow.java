package com.kcpc.mkt.reporting.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * CEO Content Pipeline dashboard row (docs/changes/CEO_CONTENT_PIPELINE_18_COLUMN_CHANGE.md,
 * dates section superseded by the Planned/Actual split below). One row per Content ID regardless
 * of how many child records (Planned Outputs, assignees, targets, publication events) it has -
 * multi-valued fields arrive pre-joined as display strings.
 *
 * <p>Plain class with {@code getX()} accessors, not a {@code record}: read directly by JSP EL -
 * see {@link KpiValue}'s class doc for why a record's no-prefix accessors break classic JSP EL
 * property resolution (ENG-031).
 */
public class PipelineRow {

    private final UUID contentPlanId;
    private final String contentId;
    private final String sku;
    private final String ideaTitle;
    private final String referenceLink;
    private final boolean referenceLinkIsUrl;
    private final String category;
    private final String channels;
    private final String actor;
    private final String cameraPersons;
    private final String models;
    private final String videoEditors;
    private final String driveLink;
    private final LocalDate plannedShootDate;
    private final LocalDate plannedEditDate;
    private final LocalDate plannedLiveDate;
    private final String actualShootDate;
    private final String actualEditDate;
    private final String actualLiveDate;
    private final String platforms;
    private final String performanceState;
    private final boolean performanceLinkEligible;
    private final String status;

    public PipelineRow(UUID contentPlanId, String contentId, String sku, String ideaTitle, String referenceLink,
                        boolean referenceLinkIsUrl, String category, String channels, String actor,
                        String cameraPersons, String models, String videoEditors, String driveLink,
                        LocalDate plannedShootDate, LocalDate plannedEditDate, LocalDate plannedLiveDate,
                        String actualShootDate, String actualEditDate, String actualLiveDate, String platforms,
                        String performanceState, boolean performanceLinkEligible, String status) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.sku = sku;
        this.ideaTitle = ideaTitle;
        this.referenceLink = referenceLink;
        this.referenceLinkIsUrl = referenceLinkIsUrl;
        this.category = category;
        this.channels = channels;
        this.actor = actor;
        this.cameraPersons = cameraPersons;
        this.models = models;
        this.videoEditors = videoEditors;
        this.driveLink = driveLink;
        this.plannedShootDate = plannedShootDate;
        this.plannedEditDate = plannedEditDate;
        this.plannedLiveDate = plannedLiveDate;
        this.actualShootDate = actualShootDate;
        this.actualEditDate = actualEditDate;
        this.actualLiveDate = actualLiveDate;
        this.platforms = platforms;
        this.performanceState = performanceState;
        this.performanceLinkEligible = performanceLinkEligible;
        this.status = status;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getContentId() {
        return contentId;
    }

    public String getSku() {
        return sku;
    }

    public String getIdeaTitle() {
        return ideaTitle;
    }

    public String getReferenceLink() {
        return referenceLink;
    }

    public boolean isReferenceLinkIsUrl() {
        return referenceLinkIsUrl;
    }

    public String getCategory() {
        return category;
    }

    public String getChannels() {
        return channels;
    }

    public String getActor() {
        return actor;
    }

    public String getCameraPersons() {
        return cameraPersons;
    }

    public String getModels() {
        return models;
    }

    public String getVideoEditors() {
        return videoEditors;
    }

    public String getDriveLink() {
        return driveLink;
    }

    public LocalDate getPlannedShootDate() {
        return plannedShootDate;
    }

    public LocalDate getPlannedEditDate() {
        return plannedEditDate;
    }

    public LocalDate getPlannedLiveDate() {
        return plannedLiveDate;
    }

    public String getActualShootDate() {
        return actualShootDate;
    }

    public String getActualEditDate() {
        return actualEditDate;
    }

    public String getActualLiveDate() {
        return actualLiveDate;
    }

    public String getPlatforms() {
        return platforms;
    }

    public String getPerformanceState() {
        return performanceState;
    }

    public boolean isPerformanceLinkEligible() {
        return performanceLinkEligible;
    }

    public String getStatus() {
        return status;
    }
}
