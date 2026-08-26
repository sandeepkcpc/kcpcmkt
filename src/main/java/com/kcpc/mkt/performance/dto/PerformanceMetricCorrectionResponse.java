package com.kcpc.mkt.performance.dto;

import com.kcpc.mkt.performance.domain.PerformanceMetricCorrection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** V26: the four direct-entry Meta metrics. */
public record PerformanceMetricCorrectionResponse(
        UUID correctionId,
        UUID scorecardId,
        UUID supersedesCorrectionId,
        BigDecimal priorHookRate, BigDecimal newHookRate,
        Boolean priorHookRateIsNa, Boolean newHookRateIsNa,
        BigDecimal priorHoldRate, BigDecimal newHoldRate,
        Boolean priorHoldRateIsNa, Boolean newHoldRateIsNa,
        Long priorViews, Long newViews,
        BigDecimal priorAvgViewDuration, BigDecimal newAvgViewDuration,
        Boolean priorAvgViewDurationIsNa, Boolean newAvgViewDurationIsNa,
        String correctionReason,
        UUID correctedByUserId,
        Instant correctedAt) {

    public static PerformanceMetricCorrectionResponse from(PerformanceMetricCorrection c) {
        return new PerformanceMetricCorrectionResponse(
                c.getId(), c.getScorecard().getId(),
                c.getSupersedesCorrection() != null ? c.getSupersedesCorrection().getId() : null,
                c.getPriorMetaHookRate(), c.getNewMetaHookRate(),
                c.getPriorMetaHookRateIsNa(), c.getNewMetaHookRateIsNa(),
                c.getPriorMetaHoldRate(), c.getNewMetaHoldRate(),
                c.getPriorMetaHoldRateIsNa(), c.getNewMetaHoldRateIsNa(),
                c.getPriorMetaViews(), c.getNewMetaViews(),
                c.getPriorMetaAvgViewDuration(), c.getNewMetaAvgViewDuration(),
                c.getPriorMetaAvgViewDurationIsNa(), c.getNewMetaAvgViewDurationIsNa(),
                c.getMandatoryReason(), c.getCorrectedBy().getId(), c.getCorrectedAt());
    }
}
