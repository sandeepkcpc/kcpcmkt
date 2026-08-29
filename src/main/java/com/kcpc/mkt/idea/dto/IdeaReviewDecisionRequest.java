package com.kcpc.mkt.idea.dto;

import com.kcpc.mkt.idea.domain.IdeaReviewDecision;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record IdeaReviewDecisionRequest(
        @NotNull IdeaReviewDecision decision,
        String reason,
        BigDecimal cameramanMark,
        BigDecimal editorMark,
        BigDecimal modelMark,
        PlanningApprovalRequest planning
) {
}
