package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.workflow.domain.WorkflowStatus;

import java.time.LocalDate;
import java.util.UUID;

public record MyTaskResponse(UUID contentPlanId, String contentId, String role, WorkflowStatus status,
                              LocalDate plannedShootDate, LocalDate plannedEditDate, LocalDate plannedLiveDate) {
}
