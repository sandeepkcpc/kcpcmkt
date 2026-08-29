package com.kcpc.mkt.planning.dto;

import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPriority;
import com.kcpc.mkt.planning.domain.PlanningMode;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ContentPlanResponse(UUID contentPlanId, UUID ideaId, String contentId, String categoryText,
                                   ContentPriority contentPriority, String skuReference, boolean skuNotApplicable,
                                   LocalDate plannedLiveDate, PlanningMode planningMode, String urgencyReason,
                                   LocalDate plannedShootDate, LocalDate plannedEditDate, String folderLink,
                                   boolean fullyPlanned, WorkflowStatus status) {
    public static ContentPlanResponse from(ContentPlan plan) {
        return new ContentPlanResponse(plan.getId(), plan.getIdea().getId(), plan.getContentId(),
                plan.getCategoryText(), plan.getContentPriority(), plan.getSkuReference(), plan.isSkuNotApplicable(),
                plan.getPlannedLiveDate(), plan.getPlanningMode(), plan.getUrgencyReason(),
                plan.getPlannedShootDate(), plan.getPlannedEditDate(), plan.getFolderLink(),
                plan.isFullyPlanned(), plan.getWorkflowInstance().getCurrentStatusCode());
    }
}
