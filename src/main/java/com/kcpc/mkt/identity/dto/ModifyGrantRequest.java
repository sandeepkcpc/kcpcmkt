package com.kcpc.mkt.identity.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record ModifyGrantRequest(Instant newEffectiveUntil, @NotBlank String reason) {
}
