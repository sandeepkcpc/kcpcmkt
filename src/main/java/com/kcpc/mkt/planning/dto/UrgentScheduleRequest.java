package com.kcpc.mkt.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UrgentScheduleRequest(@NotNull LocalDate plannedLiveDate, @NotNull LocalDate shootDate,
                                     @NotNull LocalDate editDate, @NotBlank String urgencyReason) {
}
