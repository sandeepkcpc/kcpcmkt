package com.kcpc.mkt.reporting.dto;

import java.util.List;

/** KPI Dashboard -&gt; Workflow &amp; SLA (spec §11-16). */
public class WorkflowSlaDashboardDto {

    private final List<StageHealthRow> stageHealth;
    private final Double planningTurnaroundDays;
    private final Double shootToPublishCycleTimeDays;
    private final Double endToEndCycleTimeDays;
    private final OnTimeDeliveryResult onTimeDelivery;
    private final Double averageDelayDays;
    private final List<DelayAgingBucket> delayAging;
    private final OnHoldSummaryDto onHoldSummary;
    private final long reopenedCount;
    private final long repostPublicationCount;

    public WorkflowSlaDashboardDto(List<StageHealthRow> stageHealth, Double planningTurnaroundDays,
                                    Double shootToPublishCycleTimeDays, Double endToEndCycleTimeDays,
                                    OnTimeDeliveryResult onTimeDelivery, Double averageDelayDays,
                                    List<DelayAgingBucket> delayAging, OnHoldSummaryDto onHoldSummary,
                                    long reopenedCount, long repostPublicationCount) {
        this.stageHealth = stageHealth;
        this.planningTurnaroundDays = planningTurnaroundDays;
        this.shootToPublishCycleTimeDays = shootToPublishCycleTimeDays;
        this.endToEndCycleTimeDays = endToEndCycleTimeDays;
        this.onTimeDelivery = onTimeDelivery;
        this.averageDelayDays = averageDelayDays;
        this.delayAging = delayAging;
        this.onHoldSummary = onHoldSummary;
        this.reopenedCount = reopenedCount;
        this.repostPublicationCount = repostPublicationCount;
    }

    public List<StageHealthRow> getStageHealth() {
        return stageHealth;
    }

    public Double getPlanningTurnaroundDays() {
        return planningTurnaroundDays;
    }

    public Double getShootToPublishCycleTimeDays() {
        return shootToPublishCycleTimeDays;
    }

    public Double getEndToEndCycleTimeDays() {
        return endToEndCycleTimeDays;
    }

    public OnTimeDeliveryResult getOnTimeDelivery() {
        return onTimeDelivery;
    }

    public Double getAverageDelayDays() {
        return averageDelayDays;
    }

    public List<DelayAgingBucket> getDelayAging() {
        return delayAging;
    }

    public OnHoldSummaryDto getOnHoldSummary() {
        return onHoldSummary;
    }

    public long getReopenedCount() {
        return reopenedCount;
    }

    public long getRepostPublicationCount() {
        return repostPublicationCount;
    }
}
