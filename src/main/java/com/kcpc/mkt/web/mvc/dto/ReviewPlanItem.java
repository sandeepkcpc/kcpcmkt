package com.kcpc.mkt.web.mvc.dto;

import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.reporting.dto.PipelineRow;

/**
 * Manager Reviews Workspace (Planning/Shoot/Edit tabs) queue row + detail source: pairs a
 * {@code ContentPlan} with its already-built {@link PipelineRow} (from
 * {@code PipelineDashboardService#buildRows}) so the JSP can read PipelineRow's derived display
 * fields (contentId, ideaTitle, priority, planned/actual dates, cameraPersons/videoEditors/models,
 * delay, driveLink) AND the handful of raw ContentPlan fields PipelineRow doesn't carry
 * (planningMode, urgencyReason) without a second large DTO. Plain class, not a record: rendered
 * directly by a JSP, whose EL only recognizes getX() JavaBean accessors (ENG-031).
 */
public class ReviewPlanItem {

    private final ContentPlan plan;
    private final PipelineRow row;

    public ReviewPlanItem(ContentPlan plan, PipelineRow row) {
        this.plan = plan;
        this.row = row;
    }

    public ContentPlan getPlan() {
        return plan;
    }

    public PipelineRow getRow() {
        return row;
    }
}
