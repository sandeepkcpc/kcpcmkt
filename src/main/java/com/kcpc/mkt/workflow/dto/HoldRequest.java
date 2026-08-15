package com.kcpc.mkt.workflow.dto;

import jakarta.validation.constraints.NotBlank;

public record HoldRequest(@NotBlank String reason) {
}
