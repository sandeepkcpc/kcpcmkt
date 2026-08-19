package com.kcpc.mkt.web.mvc.dto;

import java.util.List;
import java.util.UUID;

/**
 * One Content ID's worth of Shoot/Edit Review feedback, newest decision first, for the employee
 * "My Review Feedback" section (ENG-062, extended to Video Editors in ENG-066) - groups every
 * historical ReviewCycle decision (rework cycles included) under its Content ID rather than one
 * flat row per decision, without collapsing or discarding any of the underlying history. Plain
 * class, not a record (ENG-031).
 */
public class ShootFeedbackGroup {

    private final UUID contentPlanId;
    private final String contentId;
    private final ShootFeedbackEntry latest;
    private final List<ShootFeedbackEntry> priorHistory;

    public ShootFeedbackGroup(UUID contentPlanId, String contentId, ShootFeedbackEntry latest,
                               List<ShootFeedbackEntry> priorHistory) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.latest = latest;
        this.priorHistory = priorHistory;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getContentId() {
        return contentId;
    }

    public ShootFeedbackEntry getLatest() {
        return latest;
    }

    public List<ShootFeedbackEntry> getPriorHistory() {
        return priorHistory;
    }

    public boolean isHasHistory() {
        return !priorHistory.isEmpty();
    }
}
