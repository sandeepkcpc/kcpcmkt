package com.kcpc.mkt.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateChannelRequest(String channelHandle, Boolean isActive, @NotBlank String catalogueReason) {
}
