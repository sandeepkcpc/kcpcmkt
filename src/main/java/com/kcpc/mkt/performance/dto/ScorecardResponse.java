package com.kcpc.mkt.performance.dto;

import com.kcpc.mkt.performance.domain.CreativePerformanceScorecard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** V26: reports the direct-entry Meta values as stored (not corrected/effective - see
 * {@code EffectiveScorecardMetrics} for the correction-resolved read projection). */
public record ScorecardResponse(UUID scorecardId, BigDecimal hookRatePercent, boolean hookRateIsNa,
                                 BigDecimal holdRatePercent, boolean holdRateIsNa, Long views,
                                 BigDecimal averageViewDurationSeconds, boolean avgViewDurationIsNa,
                                 boolean submitted, Instant submittedAt) {
    public static ScorecardResponse from(CreativePerformanceScorecard scorecard) {
        return new ScorecardResponse(scorecard.getId(), scorecard.getMetaHookRatePercent(),
                scorecard.isMetaHookRateIsNa(), scorecard.getMetaHoldRatePercent(), scorecard.isMetaHoldRateIsNa(),
                scorecard.getMetaViews(), scorecard.getMetaAverageViewDurationSeconds(),
                scorecard.isMetaAvgViewDurationIsNa(), scorecard.isSubmitted(), scorecard.getSubmittedAt());
    }
}
