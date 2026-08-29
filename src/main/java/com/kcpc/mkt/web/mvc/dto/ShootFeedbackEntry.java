package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;

/**
 * One decided {@code ReviewCycle} (SHOOT_REVIEW or EDIT_REVIEW - never Publishing,
 * which has no review gate) - fields are gate-agnostic, so this same class is shared across the
 * Cameraperson/Editor Task Detail screens AND (ENG-082) the CEO/MM Content Detail page's combined
 * Review Feedback History rather than duplicated per gate. Plain class, not a record:
 * rendered directly by a JSP, whose EL only recognizes getX() JavaBean accessors (ENG-031).
 * {@code reviewStage}/{@code cycleNumber} (ENG-082) let a caller show MULTIPLE gates' history
 * together, labeled - the two original callers (Shoot/Edit Task Detail, each single-gate) just
 * pass their own fixed gate label and the cycle's {@code cycleNumber}.
 */
public class ShootFeedbackEntry {

    private final String reviewStage;
    private final int cycleNumber;
    private final String decisionLabel;
    private final String decisionCssClass;
    private final String reason;
    private final String reviewerName;
    private final boolean reviewerIsLead;
    private final Instant decidedAt;

    public ShootFeedbackEntry(String reviewStage, int cycleNumber, String decisionLabel, String decisionCssClass,
                               String reason, String reviewerName, boolean reviewerIsLead, Instant decidedAt) {
        this.reviewStage = reviewStage;
        this.cycleNumber = cycleNumber;
        this.decisionLabel = decisionLabel;
        this.decisionCssClass = decisionCssClass;
        this.reason = reason;
        this.reviewerName = reviewerName;
        this.reviewerIsLead = reviewerIsLead;
        this.decidedAt = decidedAt;
    }

    public String getReviewStage() {
        return reviewStage;
    }

    public int getCycleNumber() {
        return cycleNumber;
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
