package com.kcpc.mkt.publishing.dto;

import jakarta.validation.constraints.NotBlank;

/** API-OP-041 request body. */
public record CorrectEvidenceUrlRequest(
        @NotBlank String correctedEvidenceUrl,
        @NotBlank String correctionReason) {
}
