package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;

/**
 * "/app/my-work" My Completed Work / History row - an Employee's own past involvement in a stage
 * that has since moved on, without any next-stage operational detail (ENG-038). Plain class, not
 * a record: rendered directly by a JSP, whose EL only recognizes getX() JavaBean accessors, not a
 * record's canonical accessors (ENG-031).
 */
public class CompletedWorkItem {

    private final String contentId;
    private final String stageWorked;
    private final Instant completedOn;
    private final String finalResult;

    public CompletedWorkItem(String contentId, String stageWorked, Instant completedOn, String finalResult) {
        this.contentId = contentId;
        this.stageWorked = stageWorked;
        this.completedOn = completedOn;
        this.finalResult = finalResult;
    }

    public String getContentId() {
        return contentId;
    }

    public String getStageWorked() {
        return stageWorked;
    }

    public Instant getCompletedOn() {
        return completedOn;
    }

    public String getFinalResult() {
        return finalResult;
    }
}
