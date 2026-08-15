package com.kcpc.mkt.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePlatformRequest(String platformName, Boolean isActive, @NotBlank String catalogueReason) {
}
