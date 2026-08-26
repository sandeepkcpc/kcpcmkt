package com.kcpc.mkt.performance.dto;

import java.math.BigDecimal;

/** V26: direct-entry Meta model. {@code views} has no N/A flag - see {@code EffectiveScorecardMetrics}. */
public record ScorecardDraftRequest(BigDecimal hookRatePercent, boolean hookRateIsNa,
                                     BigDecimal holdRatePercent, boolean holdRateIsNa,
                                     Long views,
                                     BigDecimal averageViewDurationSeconds, boolean avgViewDurationIsNa) {
}
