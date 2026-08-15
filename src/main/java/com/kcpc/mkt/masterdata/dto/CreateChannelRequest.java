package com.kcpc.mkt.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChannelRequest(@NotBlank String channelHandle, @NotBlank String catalogueReason) {
}
