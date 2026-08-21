package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "/app/my-work" Completed Work row - an Employee's own past involvement in a stage that has
 * since moved on, without any next-stage operational detail (ENG-038). Plain class, not a record:
 * rendered directly by a JSP, whose EL only recognizes getX() JavaBean accessors, not a record's
 * canonical accessors (ENG-031).
 */
public class CompletedWorkItem {

    private final UUID contentPlanId;
    private final UUID assignmentId;
    private final String contentId;
    private final String title;
    private final String stageWorked;
    private final LocalDate stageDate;
    private final String models;
    private final Instant completedOn;
    private final String finalResult;
    private final String remarks;

    public CompletedWorkItem(UUID contentPlanId, UUID assignmentId, String contentId, String title, String stageWorked,
                              LocalDate stageDate, String models, Instant completedOn, String finalResult,
                              String remarks) {
        this.contentPlanId = contentPlanId;
        this.assignmentId = assignmentId;
        this.contentId = contentId;
        this.title = title;
        this.stageWorked = stageWorked;
        this.stageDate = stageDate;
        this.models = models;
        this.completedOn = completedOn;
        this.finalResult = finalResult;
        this.remarks = remarks;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public String getContentId() {
        return contentId;
    }

    public String getTitle() {
        return title;
    }

    public String getStageWorked() {
        return stageWorked;
    }

    public LocalDate getStageDate() {
        return stageDate;
    }

    public String getModels() {
        return models;
    }

    public Instant getCompletedOn() {
        return completedOn;
    }

    public String getFinalResult() {
        return finalResult;
    }

    public String getRemarks() {
        return remarks;
    }
}
