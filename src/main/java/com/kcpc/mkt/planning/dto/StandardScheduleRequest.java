package com.kcpc.mkt.planning.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record StandardScheduleRequest(@NotNull LocalDate plannedLiveDate, LocalDate shootDateOverride,
                                       LocalDate editDateOverride) {
}
