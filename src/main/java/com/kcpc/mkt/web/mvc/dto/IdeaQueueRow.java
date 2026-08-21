package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * ENG-088: "/app/ideas" (CEO/MM Idea Queue branch) table row - every submitted Idea across the
 * whole team, normalized to the Idea-only status vocabulary (never a downstream
 * {@code WorkflowStatus} name) exactly the same way {@link MyIdeaRow} already does for the
 * Employee-facing "My Ideas" screen, plus the submitter's name and a server-computed
 * {@code canDecide} flag so the JSP never has to re-derive who may act on a row. Plain class, not
 * a record: rendered directly by a JSP, whose EL only recognizes getX() JavaBean accessors
 * (ENG-031).
 */
public class IdeaQueueRow {

    private final UUID ideaId;
    private final String businessIdeaCode;
    private final String title;
    private final UUID submittedByUserId;
    private final String submittedByName;
    private final Instant submittedAt;
    private final String statusLabel;
    private final String statusCssClass;
    private final boolean canDecide;

    public IdeaQueueRow(UUID ideaId, String businessIdeaCode, String title, UUID submittedByUserId,
                         String submittedByName, Instant submittedAt, String statusLabel, String statusCssClass,
                         boolean canDecide) {
        this.ideaId = ideaId;
        this.businessIdeaCode = businessIdeaCode;
        this.title = title;
        this.submittedByUserId = submittedByUserId;
        this.submittedByName = submittedByName;
        this.submittedAt = submittedAt;
        this.statusLabel = statusLabel;
        this.statusCssClass = statusCssClass;
        this.canDecide = canDecide;
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

    public UUID getSubmittedByUserId() {
        return submittedByUserId;
    }

    public String getSubmittedByName() {
        return submittedByName;
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

    public boolean isCanDecide() {
        return canDecide;
    }
}
