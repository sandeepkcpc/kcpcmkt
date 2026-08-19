package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;

/**
 * One decided {@code ReviewCycle} (SHOOT_REVIEW or EDIT_REVIEW), re-labeled for the employee-
 * facing "My Review Feedback" section (ENG-062, extended to Video Editors in ENG-066) - fields are
 * gate-agnostic, so this same class is shared by both the Cameraperson and Editor screens rather
 * than duplicated. Plain class, not a record: rendered directly by a JSP, whose EL only recognizes
 * getX() JavaBean accessors (ENG-031).
 */
public class ShootFeedbackEntry {

    private final String decisionLabel;
    private final String decisionCssClass;
    private final String reason;
    private final String reviewerName;
    private final boolean reviewerIsLead;
    private final Instant decidedAt;

    public ShootFeedbackEntry(String decisionLabel, String decisionCssClass, String reason, String reviewerName,
                               boolean reviewerIsLead, Instant decidedAt) {
        this.decisionLabel = decisionLabel;
        this.decisionCssClass = decisionCssClass;
        this.reason = reason;
        this.reviewerName = reviewerName;
        this.reviewerIsLead = reviewerIsLead;
        this.decidedAt = decidedAt;
    }

    public String getDecisionLabel() {
        return decisionLabel;
    }

    public String getDecisionCssClass() {
        return decisionCssClass;
    }

    public String getReason() {
        return reason;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public boolean isReviewerIsLead() {
        return reviewerIsLead;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
