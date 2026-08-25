package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;

/**
 * The governed per-Publishing-cycle On-Time Delivery result (spec §17-20): one binary observation
 * per Publishing cycle (original + each repost), never per event and never per Platform x Channel
 * target. {@code percent} is {@code null} when {@code eligibleCycles == 0} (never a fabricated 0%
 * or 100%). Computed once by {@code KpiDashboardService} and reused as-is everywhere this number is
 * shown (Overview headline, Workflow &amp; SLA headline) - never recalculated per screen.
 */
public class OnTimeDeliveryResult {

    private final long eligibleCycles;
    private final long onTimeCycles;
    private final BigDecimal percent;

    public OnTimeDeliveryResult(long eligibleCycles, long onTimeCycles, BigDecimal percent) {
        this.eligibleCycles = eligibleCycles;
        this.onTimeCycles = onTimeCycles;
        this.percent = percent;
    }

    public long getEligibleCycles() {
        return eligibleCycles;
    }

    public long getOnTimeCycles() {
        return onTimeCycles;
    }

    public BigDecimal getPercent() {
        return percent;
    }
}
