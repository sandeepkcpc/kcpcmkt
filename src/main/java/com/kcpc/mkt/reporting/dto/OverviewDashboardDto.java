package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/** KPI Dashboard -&gt; Overview: the ~8 headline KPIs + Stage Bottleneck + Attention Needed +
 * Idea-&gt;Publish Funnel (spec §7-10). Every field is null/empty rather than a fabricated value
 * when the underlying data/denominator is unavailable. */
public class OverviewDashboardDto {

    private final long activeWip;
    private final long delayedDeliverables;
    private final OnTimeDeliveryResult onTimeDelivery;
    private final long publishedContent;
    private final Double avgEndToEndCycleTimeDays;
    private final BigDecimal reworkRatePercent;
    private final long pendingReviews;
    private final long performanceOverdue;
    private final List<StageHealthRow> stageHealth;
    private final List<AttentionItem> attentionItems;
    private final IdeaFunnelDto funnel;

    public OverviewDashboardDto(long activeWip, long delayedDeliverables, OnTimeDeliveryResult onTimeDelivery,
                                 long publishedContent, Double avgEndToEndCycleTimeDays, BigDecimal reworkRatePercent,
                                 long pendingReviews, long performanceOverdue, List<StageHealthRow> stageHealth,
                                 List<AttentionItem> attentionItems, IdeaFunnelDto funnel) {
        this.activeWip = activeWip;
        this.delayedDeliverables = delayedDeliverables;
        this.onTimeDelivery = onTimeDelivery;
        this.publishedContent = publishedContent;
        this.avgEndToEndCycleTimeDays = avgEndToEndCycleTimeDays;
        this.reworkRatePercent = reworkRatePercent;
        this.pendingReviews = pendingReviews;
        this.performanceOverdue = performanceOverdue;
        this.stageHealth = stageHealth;
        this.attentionItems = attentionItems;
        this.funnel = funnel;
    }

    public long getActiveWip() {
        return activeWip;
    }

    public long getDelayedDeliverables() {
        return delayedDeliverables;
    }

    public OnTimeDeliveryResult getOnTimeDelivery() {
        return onTimeDelivery;
    }

    public long getPublishedContent() {
        return publishedContent;
    }

    public Double getAvgEndToEndCycleTimeDays() {
        return avgEndToEndCycleTimeDays;
    }

    public BigDecimal getReworkRatePercent() {
        return reworkRatePercent;
    }

    public long getPendingReviews() {
        return pendingReviews;
    }

    public long getPerformanceOverdue() {
        return performanceOverdue;
    }

    public List<StageHealthRow> getStageHealth() {
        return stageHealth;
    }

    public List<AttentionItem> getAttentionItems() {
        return attentionItems;
    }

    public IdeaFunnelDto getFunnel() {
        return funnel;
    }
}
