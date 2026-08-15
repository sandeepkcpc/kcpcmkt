package com.kcpc.mkt.performance.dto;

import com.kcpc.mkt.performance.domain.CreativePerformanceScorecard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScorecardResponse(UUID scorecardId, BigDecimal hookRatePercent, BigDecimal holdRatePercent,
                                 BigDecimal ctrPercent, boolean submitted, Instant submittedAt) {
    public static ScorecardResponse from(CreativePerformanceScorecard scorecard) {
        return new ScorecardResponse(scorecard.getId(), scorecard.getHookRatePercent(), scorecard.getHoldRatePercent(),
                scorecard.getCtrPercent(), scorecard.isSubmitted(), scorecard.getSubmittedAt());
    }
}
