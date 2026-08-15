package com.kcpc.mkt.workflow.dto;

import com.kcpc.mkt.workflow.domain.ReopenPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReopenCompletedRequest(@NotNull ReopenPurpose purpose, @NotBlank String reason) {
}
