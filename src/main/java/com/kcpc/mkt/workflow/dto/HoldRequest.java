package com.kcpc.mkt.workflow.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record HoldRequest(@NotBlank String reason, LocalDate expectedResumeDate) {
}
