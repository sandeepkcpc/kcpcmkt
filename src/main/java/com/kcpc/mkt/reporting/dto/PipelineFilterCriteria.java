package com.kcpc.mkt.reporting.dto;

import java.time.LocalDate;

/**
 * ENG-071: Content Pipeline dashboard filter/sort request, carried from
 * {@code LandingMvcController.pipeline()}'s query params into
 * {@link com.kcpc.mkt.reporting.service.PipelineDashboardService#filterAndSort}. Internal-only
 * carrier - never rendered directly by any JSP - so a record is fine (ENG-031's getter-only
 * constraint applies only to JSP-EL-bound view models). Every field is optional (null/blank =
 * "no filter on this dimension"); matching semantics are documented on {@code filterAndSort}.
 * Each Planned/Actual date column gets its own independent range (ENG-071). {@code stage}
 * (ENG-073) is a coarse top-level tab filter (All/Needs Attention/Planning/Shoot/Edit/Publishing/
 * Performance/Completed) grouping multiple raw statuses at once - orthogonal to {@code status},
 * which still supports an exact single-status match if ever driven directly by URL.
 */
public record PipelineFilterCriteria(
        String search,
        String stage,
        String sku,
        String idea,
        String priority,
        String cameraperson,
        String model,
        String videoEditor,
        String channel,
        String status,
        String performanceState,
        boolean delayedOnly,
        LocalDate plannedShootFrom,
        LocalDate plannedShootTo,
        LocalDate plannedEditFrom,
        LocalDate plannedEditTo,
        LocalDate plannedLiveFrom,
        LocalDate plannedLiveTo,
        LocalDate actualShootFrom,
        LocalDate actualShootTo,
        LocalDate actualEditFrom,
        LocalDate actualEditTo,
        LocalDate actualLiveFrom,
        LocalDate actualLiveTo,
        String sortBy,
        String sortDir) {
}
