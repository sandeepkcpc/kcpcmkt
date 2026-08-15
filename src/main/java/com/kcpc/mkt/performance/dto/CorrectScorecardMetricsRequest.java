package com.kcpc.mkt.performance.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * API-OP-046 request body. Every metric field is optional - a correction may touch any subset of
 * metrics (ERD-TBL-028 mirrors the nullable metric columns of creative_performance_scorecards);
 * only correctionReason is mandatory.
 */
public record CorrectScorecardMetricsRequest(
        Integer correctedViews3sec,
        Boolean correctedViews3secIsNa,
        Integer correctedPlays,
        BigDecimal correctedWatchTimeSeconds,
        Boolean correctedWatchTimeIsNa,
        BigDecimal correctedVideoLengthSeconds,
        Boolean correctedVideoLengthIsNa,
        Integer correctedLinkClicks,
        Boolean correctedClicksIsNa,
        Integer correctedImpressions,
        @NotBlank String correctionReason) {
}
