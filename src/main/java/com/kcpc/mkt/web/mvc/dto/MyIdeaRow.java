package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * "/app/ideas" (Employee "My Ideas" branch) table row - the logged-in Employee's own idea,
 * normalized with a simplified status label/color and the latest review feedback text (ENG-059).
 * Plain class, not a record: rendered directly by a JSP, whose EL only recognizes getX() JavaBean
 * accessors, not a record's canonical accessors (ENG-031).
 */
public class MyIdeaRow {

    private final UUID ideaId;
    private final String businessIdeaCode;
    private final String title;
    private final String description;
    private final Instant submittedAt;
    private final String statusLabel;
    private final String statusCssClass;
    private final String feedback;

    public MyIdeaRow(UUID ideaId, String businessIdeaCode, String title, String description, Instant submittedAt,
                      String statusLabel, String statusCssClass, String feedback) {
        this.ideaId = ideaId;
        this.businessIdeaCode = businessIdeaCode;
        this.title = title;
        this.description = description;
        this.submittedAt = submittedAt;
        this.statusLabel = statusLabel;
        this.statusCssClass = statusCssClass;
        this.feedback = feedback;
    }

    public UUID getIdeaId() {
        return ideaId;
    }

    public String getBusinessIdeaCode() {
        return businessIdeaCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusCssClass() {
        return statusCssClass;
    }

    public String getFeedback() {
        return feedback;
    }
}
