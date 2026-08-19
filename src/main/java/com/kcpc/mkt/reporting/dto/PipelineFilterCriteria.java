package com.kcpc.mkt.reporting.dto;

import java.time.LocalDate;

/**
 * ENG-071: Content Pipeline dashboard filter/sort request, carried from
 * {@code LandingMvcController.pipeline()}'s query params into
 * {@link com.kcpc.mkt.reporting.service.PipelineDashboardService#filterAndSort}. Internal-only
 * carrier - never rendered directly by any JSP - so a record is fine (ENG-031's getter-only
 * constraint applies only to JSP-EL-bound view models). Every field is optional (null/blank =
 * "no filter on this dimension"); matching semantics are documented on {@code filterAndSort}.
 * Each Planned/Actual date column gets its own independent range (ENG-071 - a per-column date
 * filter popup per column, replacing ENG-070's single combined Planned/Actual range).
 */
public record PipelineFilterCriteria(
        String search,
        String sku,
        String idea,
        String priority,
        String cameraperson,
        String model,
        String videoEditor,
        String platform,
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
