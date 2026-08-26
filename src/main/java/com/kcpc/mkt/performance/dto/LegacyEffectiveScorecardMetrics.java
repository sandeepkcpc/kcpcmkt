package com.kcpc.mkt.performance.dto;

import java.math.BigDecimal;

/**
 * Read-side projection for a PRE-V26 scorecard ({@code usesMetaMetricModel == false}) - the
 * original 6-field model plus derived Hook/Hold/CTR, correction-resolved exactly as before this
 * change. Historical records are never migrated/reinterpreted into the new Meta model (see
 * docs/KPI_DATA_RECONCILIATION_REPORT.md-style migration note on {@code CreativePerformanceScorecard}),
 * so their existing correction/effective-value display keeps working unchanged - this is that old
 * logic, kept verbatim under a new name so it's clearly distinct from {@link EffectiveScorecardMetrics}
 * (the new Meta-only model's equivalent).
 *
 * <p>Deliberately a plain class with JavaBean getters (JSP EL), same reasoning as
 * {@link EffectiveScorecardMetrics}.
 */
public class LegacyEffectiveScorecardMetrics {

    private final Integer views3sec;
    private final boolean views3secIsNa;
    private final Integer plays;
    private final BigDecimal averageWatchTimeSeconds;
    private final boolean watchTimeIsNa;
    private final BigDecimal videoLengthSeconds;
    private final boolean videoLengthIsNa;
    private final Integer linkClicks;
    private final boolean clicksIsNa;
    private final Integer impressions;
    private final BigDecimal hookRatePercent;
    private final BigDecimal holdRatePercent;
    private final BigDecimal ctrPercent;

    public LegacyEffectiveScorecardMetrics(Integer views3sec, boolean views3secIsNa, Integer plays,
                                            BigDecimal averageWatchTimeSeconds, boolean watchTimeIsNa,
                                            BigDecimal videoLengthSeconds, boolean videoLengthIsNa,
                                            Integer linkClicks, boolean clicksIsNa, Integer impressions,
                                            BigDecimal hookRatePercent, BigDecimal holdRatePercent, BigDecimal ctrPercent) {
        this.views3sec = views3sec;
        this.views3secIsNa = views3secIsNa;
        this.plays = plays;
        this.averageWatchTimeSeconds = averageWatchTimeSeconds;
        this.watchTimeIsNa = watchTimeIsNa;
        this.videoLengthSeconds = videoLengthSeconds;
        this.videoLengthIsNa = videoLengthIsNa;
        this.linkClicks = linkClicks;
        this.clicksIsNa = clicksIsNa;
        this.impressions = impressions;
        this.hookRatePercent = hookRatePercent;
        this.holdRatePercent = holdRatePercent;
        this.ctrPercent = ctrPercent;
    }

    public Integer getViews3sec() {
        return views3sec;
    }

    public boolean isViews3secIsNa() {
        return views3secIsNa;
    }

    public Integer getPlays() {
        return plays;
    }

    public BigDecimal getAverageWatchTimeSeconds() {
        return averageWatchTimeSeconds;
    }

    public boolean isWatchTimeIsNa() {
        return watchTimeIsNa;
    }

    public BigDecimal getVideoLengthSeconds() {
        return videoLengthSeconds;
    }

    public boolean isVideoLengthIsNa() {
        return videoLengthIsNa;
    }

    public Integer getLinkClicks() {
        return linkClicks;
    }

    public boolean isClicksIsNa() {
        return clicksIsNa;
    }

    public Integer getImpressions() {
        return impressions;
    }

    public BigDecimal getHookRatePercent() {
        return hookRatePercent;
    }

    public BigDecimal getHoldRatePercent() {
        return holdRatePercent;
    }

    public BigDecimal getCtrPercent() {
        return ctrPercent;
    }
}
