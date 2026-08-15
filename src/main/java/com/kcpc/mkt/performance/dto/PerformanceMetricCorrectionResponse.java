package com.kcpc.mkt.performance.dto;

import com.kcpc.mkt.performance.domain.PerformanceMetricCorrection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PerformanceMetricCorrectionResponse(
        UUID correctionId,
        UUID scorecardId,
        UUID supersedesCorrectionId,
        Integer priorViews3sec, Integer newViews3sec,
        Integer priorPlays, Integer newPlays,
        BigDecimal priorWatchTime, BigDecimal newWatchTime,
        BigDecimal priorVideoLength, BigDecimal newVideoLength,
        Integer priorClicks, Integer newClicks,
        Integer priorImpressions, Integer newImpressions,
        Boolean priorViews3secIsNa, Boolean newViews3secIsNa,
        Boolean priorWatchTimeIsNa, Boolean newWatchTimeIsNa,
        Boolean priorVideoLengthIsNa, Boolean newVideoLengthIsNa,
        Boolean priorClicksIsNa, Boolean newClicksIsNa,
        String correctionReason,
        UUID correctedByUserId,
        Instant correctedAt) {

    public static PerformanceMetricCorrectionResponse from(PerformanceMetricCorrection c) {
        return new PerformanceMetricCorrectionResponse(
                c.getId(), c.getScorecard().getId(),
                c.getSupersedesCorrection() != null ? c.getSupersedesCorrection().getId() : null,
                c.getPriorViews3sec(), c.getNewViews3sec(),
                c.getPriorPlays(), c.getNewPlays(),
                c.getPriorWatchTime(), c.getNewWatchTime(),
                c.getPriorVideoLength(), c.getNewVideoLength(),
                c.getPriorClicks(), c.getNewClicks(),
                c.getPriorImpressions(), c.getNewImpressions(),
                c.getPriorViews3secIsNa(), c.getNewViews3secIsNa(),
                c.getPriorWatchTimeIsNa(), c.getNewWatchTimeIsNa(),
                c.getPriorVideoLengthIsNa(), c.getNewVideoLengthIsNa(),
                c.getPriorClicksIsNa(), c.getNewClicksIsNa(),
                c.getMandatoryReason(), c.getCorrectedBy().getId(), c.getCorrectedAt());
    }
}
