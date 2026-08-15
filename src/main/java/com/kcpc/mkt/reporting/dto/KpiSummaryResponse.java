package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;

/**
 * BFD §7: a curated subset of the 30 governed KPIs, computed directly from current data.
 * See docs/IMPLEMENTATION_STATUS.md "Known gap — KPI coverage" for which KPI-IDs remain
 * unimplemented (mostly duration/average-based KPIs requiring dedicated time-series aggregation
 * queries not yet built) and docs/IMPLEMENTATION_DECISIONS.md for the reasoning.
 */
public record KpiSummaryResponse(
        long kpi001PendingWork,
        long kpi005PendingApprovals,
        long kpi007PerformancePendingWork,
        long kpi010TasksCompleted,
        long kpi011TasksCancelled,
        long kpi012PublishedContent,
        long kpi017IdeasSubmitted,
        long kpi018IdeasApproved,
        long kpi019IdeasRejected,
        BigDecimal kpi020IdeaApprovalRatePercent,
        long kpi022ApprovalsByManager,
        long kpi023ApprovalsByCeo,
        BigDecimal kpi024ReworkRatePercent,
        BigDecimal kpi026ContentCompletionRatePercent
) {
}
