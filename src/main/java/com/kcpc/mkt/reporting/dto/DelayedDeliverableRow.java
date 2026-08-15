package com.kcpc.mkt.reporting.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * API-OP-060 / ERD-VW-001 (active_deliverable_delay_views) projection row.
 *
 * <p>Plain class with {@code getX()} accessors, not a {@code record}: read directly by JSP EL
 * (the Delayed Deliverables report screen) - see {@link KpiValue}'s class doc for why a record's
 * no-prefix accessors break classic JSP EL property resolution (ENG-031).
 */
public class DelayedDeliverableRow {

    private final UUID contentPlanId;
    private final String contentId;
    private final String stage;
    private final String priority;
    private final LocalDate plannedDate;
    private final long delayDays;

    public DelayedDeliverableRow(UUID contentPlanId, String contentId, String stage, String priority,
                                  LocalDate plannedDate, long delayDays) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.stage = stage;
        this.priority = priority;
        this.plannedDate = plannedDate;
        this.delayDays = delayDays;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getContentId() {
        return contentId;
    }

    public String getStage() {
        return stage;
    }

    public String getPriority() {
        return priority;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public long getDelayDays() {
        return delayDays;
    }
}
