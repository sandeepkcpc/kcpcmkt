package com.kcpc.mkt.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePlatformRequest(@NotBlank String platformName, @NotBlank String catalogueReason) {
}
