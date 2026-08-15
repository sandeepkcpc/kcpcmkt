package com.kcpc.mkt.publishing.dto;

import com.kcpc.mkt.publishing.domain.PublicationEvidenceCorrection;

import java.time.Instant;
import java.util.UUID;

public record PublicationEvidenceCorrectionResponse(
        UUID correctionId,
        UUID eventId,
        UUID supersedesCorrectionId,
        String priorEvidenceUrl,
        String correctedEvidenceUrl,
        String correctionReason,
        UUID correctedByUserId,
        Instant correctedAt) {

    public static PublicationEvidenceCorrectionResponse from(PublicationEvidenceCorrection c) {
        return new PublicationEvidenceCorrectionResponse(
                c.getId(),
                c.getEvent().getId(),
                c.getSupersedesCorrection() != null ? c.getSupersedesCorrection().getId() : null,
                c.getPriorEvidenceUrl(),
                c.getCorrectedEvidenceUrl(),
                c.getMandatoryReason(),
                c.getCorrectedBy().getId(),
                c.getCorrectedAt());
    }
}
