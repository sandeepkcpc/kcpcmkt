package com.kcpc.mkt.workflow.dto;

import com.kcpc.mkt.workflow.domain.StageContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RescheduleRequest(@NotNull StageContext stageContext, LocalDate newShootDate, LocalDate newEditDate,
                                 LocalDate newLiveDate, @NotBlank String reason) {
}
