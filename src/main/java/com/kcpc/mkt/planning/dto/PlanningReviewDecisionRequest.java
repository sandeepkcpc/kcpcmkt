package com.kcpc.mkt.planning.dto;

import jakarta.validation.constraints.NotNull;

public record PlanningReviewDecisionRequest(@NotNull boolean approve, String reason) {
}
