package com.kcpc.mkt.reporting.dto;

/**
 * One row of the Stage Bottleneck / Stage Health table (Overview and Workflow &amp; SLA), shared
 * by both screens via the same {@code KpiDashboardService} computation so they can never disagree.
 * {@code withinSlaPercent} is the approved point-in-time "Within SLA %" formula:
 * {@code (active - delayed) / active * 100}, {@code null} when active == 0 (never a fabricated 0
 * or 100). Historical delivery compliance is represented separately (On-Time Delivery / cycle
 * time), not by this column.
 */
public class StageHealthRow {

    private final String stage;
    private final long active;
    private final long delayed;
    private final Double withinSlaPercent;
    private final Double avgAgeDays;
    private final Long oldestItemAgeDays;
    private final String oldestItemContentId;

    public StageHealthRow(String stage, long active, long delayed, Double withinSlaPercent, Double avgAgeDays,
                           Long oldestItemAgeDays, String oldestItemContentId) {
        this.stage = stage;
        this.active = active;
        this.delayed = delayed;
        this.withinSlaPercent = withinSlaPercent;
        this.avgAgeDays = avgAgeDays;
        this.oldestItemAgeDays = oldestItemAgeDays;
        this.oldestItemContentId = oldestItemContentId;
    }

    public String getStage() {
        return stage;
    }

    public long getActive() {
        return active;
    }

    public long getDelayed() {
        return delayed;
    }

    public Double getWithinSlaPercent() {
        return withinSlaPercent;
    }

    public Double getAvgAgeDays() {
        return avgAgeDays;
    }

    public Long getOldestItemAgeDays() {
        return oldestItemAgeDays;
    }

    public String getOldestItemContentId() {
        return oldestItemContentId;
    }
}
