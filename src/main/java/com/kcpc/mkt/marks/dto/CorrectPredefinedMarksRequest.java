package com.kcpc.mkt.marks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** API-OP-033 request body. */
public record CorrectPredefinedMarksRequest(
        @NotNull BigDecimal newCamerapersonMarks,
        @NotNull BigDecimal newEditorMarks,
        @NotNull BigDecimal newModelMarks,
        @NotBlank String correctionReason) {
}
