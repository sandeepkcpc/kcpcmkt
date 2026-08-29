package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;

import java.time.LocalDate;

/**
 * The single Java-side source of truth for "what is this Content Plan's current approved target
 * date, and is it delayed" - reused identically by {@link KpiDashboardService} (Reports -&gt; KPI
 * Dashboard: Overview / Workflow &amp; SLA -&gt; Stage Health, Average Delay, Delay Aging) and
 * {@link PipelineDashboardService} (Content Pipeline -&gt; Delayed only), and therefore also by
 * {@link TeamWorkloadService}, which calls {@link PipelineDashboardService#delayDays} directly.
 *
 * <p>Before this class existed, {@code KpiDashboardService} and {@code PipelineDashboardService}
 * each carried their own independent copy of this switch, and Pipeline's copy silently omitted 7
 * of the 15 non-terminal status codes (falling through to "never delayed" for any plan in one of
 * them) - see docs/KPI_DATA_RECONCILIATION_REPORT.md §1 for the full reconciliation evidence. This
 * class exists so that gap cannot reappear: there is now exactly one place this logic is written.
 */
public final class StageDelayPolicy {

    private StageDelayPolicy() {
    }

    /** Null for terminal/unrecognized statuses. */
    public static LocalDate currentApprovedTarget(WorkflowStatus status, ContentPlan plan) {
        return switch (status) {
            case SA, SIP, SRV -> plan.getPlannedShootDate();
            case SAP, EA, ED, ERV -> plan.getPlannedEditDate();
            case EAP, RFP, PUBG, PP, PFUP -> plan.getPlannedLiveDate();
            default -> null; // COMP, CAN, RJ, IS, PA, RET (not applicable)
        };
    }

    /** {@code true} only when a governed target date exists AND it has already passed. */
    public static boolean isDelayed(WorkflowStatus status, ContentPlan plan, LocalDate today) {
        LocalDate target = currentApprovedTarget(status, plan);
        return target != null && target.isBefore(today);
    }
}
