package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/** KPI Dashboard -&gt; Performance (spec §33-37). Ranking metric is explicitly Avg CTR % throughout
 * (never a vague "Top Performing X", never invented Engagement/Reach). */
public class PerformanceDashboardDto {

    private final long performancePending;
    private final long performanceOverdue;
    private final BigDecimal scorecardCompletionPercent;
    private final Double avgDelayInReportingDays;
    private final List<LabelValueRow> topContentTypeByCtr;
    private final List<LabelValueRow> topPlatformByCtr;
    private final List<LabelValueRow> topChannelByCtr;
    private final LabelValueRow originalAvgCtr;
    private final LabelValueRow repostAvgCtr;
    private final LabelValueRow originalAvgImpressions;
    private final LabelValueRow repostAvgImpressions;

    public PerformanceDashboardDto(long performancePending, long performanceOverdue,
                                    BigDecimal scorecardCompletionPercent, Double avgDelayInReportingDays,
                                    List<LabelValueRow> topContentTypeByCtr, List<LabelValueRow> topPlatformByCtr,
                                    List<LabelValueRow> topChannelByCtr, LabelValueRow originalAvgCtr,
                                    LabelValueRow repostAvgCtr, LabelValueRow originalAvgImpressions,
                                    LabelValueRow repostAvgImpressions) {
        this.performancePending = performancePending;
        this.performanceOverdue = performanceOverdue;
        this.scorecardCompletionPercent = scorecardCompletionPercent;
        this.avgDelayInReportingDays = avgDelayInReportingDays;
        this.topContentTypeByCtr = topContentTypeByCtr;
        this.topPlatformByCtr = topPlatformByCtr;
        this.topChannelByCtr = topChannelByCtr;
        this.originalAvgCtr = originalAvgCtr;
        this.repostAvgCtr = repostAvgCtr;
        this.originalAvgImpressions = originalAvgImpressions;
        this.repostAvgImpressions = repostAvgImpressions;
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

    public List<LabelValueRow> getTopContentTypeByCtr() {
        return topContentTypeByCtr;
    }

    public List<LabelValueRow> getTopPlatformByCtr() {
        return topPlatformByCtr;
    }

    public List<LabelValueRow> getTopChannelByCtr() {
        return topChannelByCtr;
    }

    public LabelValueRow getOriginalAvgCtr() {
        return originalAvgCtr;
    }

    public LabelValueRow getRepostAvgCtr() {
        return repostAvgCtr;
    }

    public LabelValueRow getOriginalAvgImpressions() {
        return originalAvgImpressions;
    }

    public LabelValueRow getRepostAvgImpressions() {
        return repostAvgImpressions;
    }
}
