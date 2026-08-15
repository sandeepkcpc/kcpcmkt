package com.kcpc.mkt.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTargetRequest(@NotNull UUID platformId, @NotNull UUID channelId, @NotBlank String targetName,
                                   @NotBlank String catalogueReason) {
}
