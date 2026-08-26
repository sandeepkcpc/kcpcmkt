package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/** KPI Dashboard -&gt; Performance. V26: Performance is Meta-only (Instagram/Facebook); ranking
 * metric is Avg Hook Rate % (approved replacement for the removed CTR, the closest analog -
 * both are attention/engagement percentages), and the ORIGINAL vs REPOST volume comparison uses
 * Avg Views (approved replacement for the removed Impressions). */
public class PerformanceDashboardDto {

    private final long performancePending;
    private final long performanceOverdue;
    private final BigDecimal scorecardCompletionPercent;
    private final Double avgDelayInReportingDays;
    private final List<LabelValueRow> topContentTypeByHookRate;
    private final List<LabelValueRow> topPlatformByHookRate;
    private final List<LabelValueRow> topChannelByHookRate;
    private final LabelValueRow originalAvgHookRate;
    private final LabelValueRow repostAvgHookRate;
    private final LabelValueRow originalAvgViews;
    private final LabelValueRow repostAvgViews;

    public PerformanceDashboardDto(long performancePending, long performanceOverdue,
                                    BigDecimal scorecardCompletionPercent, Double avgDelayInReportingDays,
                                    List<LabelValueRow> topContentTypeByHookRate, List<LabelValueRow> topPlatformByHookRate,
                                    List<LabelValueRow> topChannelByHookRate, LabelValueRow originalAvgHookRate,
                                    LabelValueRow repostAvgHookRate, LabelValueRow originalAvgViews,
                                    LabelValueRow repostAvgViews) {
        this.performancePending = performancePending;
        this.performanceOverdue = performanceOverdue;
        this.scorecardCompletionPercent = scorecardCompletionPercent;
        this.avgDelayInReportingDays = avgDelayInReportingDays;
        this.topContentTypeByHookRate = topContentTypeByHookRate;
        this.topPlatformByHookRate = topPlatformByHookRate;
        this.topChannelByHookRate = topChannelByHookRate;
        this.originalAvgHookRate = originalAvgHookRate;
        this.repostAvgHookRate = repostAvgHookRate;
        this.originalAvgViews = originalAvgViews;
        this.repostAvgViews = repostAvgViews;
    }

    public long getPerformancePending() {
        return performancePending;
    }

    public long getPerformanceOverdue() {
        return performanceOverdue;
    }

    public BigDecimal getScorecardCompletionPercent() {
        return scorecardCompletionPercent;
    }

    public Double getAvgDelayInReportingDays() {
        return avgDelayInReportingDays;
    }

    public List<LabelValueRow> getTopContentTypeByHookRate() {
        return topContentTypeByHookRate;
    }

    public List<LabelValueRow> getTopPlatformByHookRate() {
        return topPlatformByHookRate;
    }

    public List<LabelValueRow> getTopChannelByHookRate() {
        return topChannelByHookRate;
    }

    public LabelValueRow getOriginalAvgHookRate() {
        return originalAvgHookRate;
    }

    public LabelValueRow getRepostAvgHookRate() {
        return repostAvgHookRate;
    }

    public LabelValueRow getOriginalAvgViews() {
        return originalAvgViews;
    }

    public LabelValueRow getRepostAvgViews() {
        return repostAvgViews;
    }
}
