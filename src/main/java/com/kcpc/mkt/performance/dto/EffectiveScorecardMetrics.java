package com.kcpc.mkt.performance.dto;

import java.math.BigDecimal;

/**
 * The current operational value of every metric on a submitted {@code CreativePerformanceScorecard}
 * after applying "latest correction per metric wins" (mirrors the Evidence Correction governance
 * principle: the original sealed scorecard row is never mutated; this is a read-side projection
 * over it plus its {@code performance_metric_corrections} chain). Used both to display the current
 * effective value in the Correct-a-Metric UI and in the Performance summary, so a correction is
 * reflected immediately everywhere.
 *
 * <p>V26: the four direct-entry Meta fields (Hook Rate, Hold Rate, Views, Average View Duration) -
 * entered as-is from Meta Ads Manager, never derived/recomputed. Views has no N/A flag (approved:
 * always collectible for an eligible Meta record); the other three may be N/A for a non-video
 * (PHOTOGRAPHY) output.
 *
 * <p>Deliberately a plain class with JavaBean getters, not a record: it is read via JSP EL
 * (deliverable-detail.jsp), and Jasper's EL resolver here does not resolve record-style accessors
 * as bean properties.
 */
public class EffectiveScorecardMetrics {

    private final BigDecimal hookRatePercent;
    private final boolean hookRateIsNa;
    private final BigDecimal holdRatePercent;
    private final boolean holdRateIsNa;
    private final Long views;
    private final BigDecimal averageViewDurationSeconds;
    private final boolean avgViewDurationIsNa;

    public EffectiveScorecardMetrics(BigDecimal hookRatePercent, boolean hookRateIsNa,
                                      BigDecimal holdRatePercent, boolean holdRateIsNa, Long views,
                                      BigDecimal averageViewDurationSeconds, boolean avgViewDurationIsNa) {
        this.hookRatePercent = hookRatePercent;
        this.hookRateIsNa = hookRateIsNa;
        this.holdRatePercent = holdRatePercent;
        this.holdRateIsNa = holdRateIsNa;
        this.views = views;
        this.averageViewDurationSeconds = averageViewDurationSeconds;
        this.avgViewDurationIsNa = avgViewDurationIsNa;
    }

    public BigDecimal getHookRatePercent() {
        return hookRatePercent;
    }

    public boolean isHookRateIsNa() {
        return hookRateIsNa;
    }

    public BigDecimal getHoldRatePercent() {
        return holdRatePercent;
    }

    public boolean isHoldRateIsNa() {
        return holdRateIsNa;
    }

    public Long getViews() {
        return views;
    }

    public BigDecimal getAverageViewDurationSeconds() {
        return averageViewDurationSeconds;
    }

    public boolean isAvgViewDurationIsNa() {
        return avgViewDurationIsNa;
    }
}
