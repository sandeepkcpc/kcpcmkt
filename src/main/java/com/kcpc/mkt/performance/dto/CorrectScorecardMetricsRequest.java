package com.kcpc.mkt.performance.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * API-OP-046 request body. Every metric field is optional - a correction may touch any subset of
 * metrics (ERD-TBL-028 mirrors the nullable metric columns of creative_performance_scorecards);
 * only correctionReason is mandatory. V26: the four direct-entry Meta fields.
 */
public record CorrectScorecardMetricsRequest(
        BigDecimal correctedHookRatePercent,
        Boolean correctedHookRateIsNa,
        BigDecimal correctedHoldRatePercent,
        Boolean correctedHoldRateIsNa,
        Long correctedViews,
        BigDecimal correctedAverageViewDurationSeconds,
        Boolean correctedAvgViewDurationIsNa,
        @NotBlank String correctionReason) {
}
