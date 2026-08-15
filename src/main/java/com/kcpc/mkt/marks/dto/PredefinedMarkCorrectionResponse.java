package com.kcpc.mkt.marks.dto;

import com.kcpc.mkt.marks.domain.PredefinedMarkCorrection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PredefinedMarkCorrectionResponse(
        UUID correctionId,
        UUID predefinedMarkId,
        UUID supersedesCorrectionId,
        BigDecimal priorCamerapersonMark,
        BigDecimal priorEditorMark,
        BigDecimal newCamerapersonMark,
        BigDecimal newEditorMark,
        String correctionReason,
        UUID correctedByUserId,
        Instant correctedAt) {

    public static PredefinedMarkCorrectionResponse from(PredefinedMarkCorrection c) {
        return new PredefinedMarkCorrectionResponse(
                c.getId(),
                c.getPredefinedMark().getId(),
                c.getSupersedesCorrection() != null ? c.getSupersedesCorrection().getId() : null,
                c.getPriorCamerapersonMark(),
                c.getPriorEditorMark(),
                c.getNewCamerapersonMark(),
                c.getNewEditorMark(),
                c.getCorrectionReason(),
                c.getCorrectedBy().getId(),
                c.getCorrectedAt());
    }
}
