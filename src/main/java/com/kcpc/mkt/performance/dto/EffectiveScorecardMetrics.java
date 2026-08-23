package com.kcpc.mkt.performance.dto;

import java.math.BigDecimal;

/**
 * The current operational value of every metric on a submitted {@code CreativePerformanceScorecard}
 * after applying "latest correction per metric wins" (mirrors the Evidence Correction governance
 * principle: the original sealed scorecard row is never mutated; this is a read-side projection
 * over it plus its {@code performance_metric_corrections} chain). Used both to display the current
 * effective value in the Correct-a-Metric UI and to recompute Hook/Hold/CTR for display so a
 * correction is reflected in the Performance summary immediately.
 *
 * <p>Deliberately a plain class with JavaBean getters, not a record: it is read via JSP EL
 * (deliverable-detail.jsp), and Jasper's EL resolver here does not resolve record-style accessors
 * as bean properties.
 */
public class EffectiveScorecardMetrics {

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

    public EffectiveScorecardMetrics(Integer views3sec, boolean views3secIsNa, Integer plays,
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
