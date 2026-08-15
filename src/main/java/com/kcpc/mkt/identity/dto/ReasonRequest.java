package com.kcpc.mkt.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ReasonRequest(@NotBlank String reason) {
}
