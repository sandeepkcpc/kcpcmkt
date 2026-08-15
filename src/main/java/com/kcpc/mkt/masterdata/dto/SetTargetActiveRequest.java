package com.kcpc.mkt.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SetTargetActiveRequest(@NotNull Boolean isActive, @NotBlank String catalogueReason) {
}
