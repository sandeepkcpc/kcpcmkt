package com.kcpc.mkt;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.BusinessRole;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.reporting.service.PipelineDashboardService;
import com.kcpc.mkt.reporting.service.StageDelayPolicy;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure, DB-free regression coverage for the Stage Health / Content Pipeline delayed-count
 * discrepancy diagnosed in docs/KPI_DATA_RECONCILIATION_REPORT.md §1: before this fix,
 * {@code PipelineDashboardService.delayDays} carried its own independent switch that silently
 * omitted SAP/EAP/PP/PFUP, so a plan in any of those statuses could never be shown as delayed in
 * Content Pipeline regardless of its actual planned date - the entire root cause of "Shoot: KPI 6
 * vs Pipeline 3", "Performance: KPI 4 vs Pipeline 0". Both {@link StageDelayPolicy} and
 * {@link PipelineDashboardService#delayDays} are pure static methods with no repository
 * dependency, so this needs no Spring context/database - deterministic dates/data throughout, per
 * the audit brief's testing requirement.
 */
class StageDelayPolicyReconciliationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    /** The statuses PipelineDashboardService's old switch silently omitted entirely. */
    private static final Set<WorkflowStatus> PREVIOUSLY_BROKEN_STATUSES =
            EnumSet.of(WorkflowStatus.SAP, WorkflowStatus.EAP, WorkflowStatus.PP, WorkflowStatus.PFUP);

    private ContentPlan planWithDates(LocalDate liveDate, LocalDate shootDate, LocalDate editDate) {
        BusinessRole role = new BusinessRole("Test Role", AccessClass.EMPLOYEE);
        User user = new User("Test User", "stagedelaytest@example.com", "hash", role);
        WorkflowInstance wi = new WorkflowInstance(WorkflowStatus.IS);
        Idea idea = new Idea(wi, "IDEA-TEST", "Title", null, null, null, user);
        ContentPlan plan = new ContentPlan(idea, wi, "C-TEST-0001");
        plan.setPlanningScheduleStandard(liveDate, shootDate, editDate);
        return plan;
    }

    // ---------------------------------------------------------------- the exact bug, reproduced and fixed

    @Test
    void previouslyBrokenStatusesAreNowCorrectlyFlaggedDelayedByBothStageDelayPolicyAndPipeline() {
        LocalDate overdue = TODAY.minusDays(3);
        ContentPlan plan = planWithDates(overdue, overdue, overdue);
        for (WorkflowStatus status : PREVIOUSLY_BROKEN_STATUSES) {
            assertThat(StageDelayPolicy.isDelayed(status, plan, TODAY))
                    .as("StageDelayPolicy for " + status).isTrue();
            assertThat(PipelineDashboardService.delayDays(status, plan, TODAY))
                    .as("PipelineDashboardService.delayDays for " + status + " (was always null before this fix)")
                    .isNotNull();
        }
    }

    @Test
    void previouslyBrokenStatusesWithFutureDatesAreNotDelayed() {
        LocalDate future = TODAY.plusDays(3);
        ContentPlan plan = planWithDates(future, future, future);
        for (WorkflowStatus status : PREVIOUSLY_BROKEN_STATUSES) {
            assertThat(StageDelayPolicy.isDelayed(status, plan, TODAY)).as(status.name()).isFalse();
            assertThat(PipelineDashboardService.delayDays(status, plan, TODAY)).as(status.name()).isNull();
        }
    }

    // ---------------------------------------------------------------- boundary: due today is not yet delayed

    @Test
    void targetDateEqualToTodayIsNotDelayed() {
        ContentPlan plan = planWithDates(TODAY, TODAY, TODAY);
        for (WorkflowStatus status : List.of(WorkflowStatus.SA, WorkflowStatus.EA, WorkflowStatus.RFP,
                WorkflowStatus.SAP, WorkflowStatus.PP)) {
            assertThat(StageDelayPolicy.isDelayed(status, plan, TODAY)).as(status.name()).isFalse();
            assertThat(PipelineDashboardService.delayDays(status, plan, TODAY)).as(status.name()).isNull();
        }
    }

    @Test
    void targetDateOneDayBeforeTodayIsDelayed() {
        LocalDate yesterday = TODAY.minusDays(1);
        ContentPlan plan = planWithDates(yesterday, yesterday, yesterday);
        assertThat(StageDelayPolicy.isDelayed(WorkflowStatus.SA, plan, TODAY)).isTrue();
        assertThat(PipelineDashboardService.delayDays(WorkflowStatus.SA, plan, TODAY)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- full reconciliation across every status

    @Test
    void stageDelayPolicyAndPipelineAgreeExactlyForEveryStatus() {
        LocalDate overdue = TODAY.minusDays(5);
        ContentPlan plan = planWithDates(overdue, overdue, overdue);
        for (WorkflowStatus status : WorkflowStatus.values()) {
            boolean governed = StageDelayPolicy.isDelayed(status, plan, TODAY);
            boolean pipeline = PipelineDashboardService.delayDays(status, plan, TODAY) != null;
            assertThat(pipeline).as("Pipeline vs StageDelayPolicy for " + status).isEqualTo(governed);
        }
    }

    @Test
    void currentApprovedTargetUsesTheCorrectFieldPerStatus() {
        LocalDate liveDate = LocalDate.of(2026, 9, 1);
        LocalDate shootDate = LocalDate.of(2026, 8, 20);
        LocalDate editDate = LocalDate.of(2026, 8, 25);
        ContentPlan plan = planWithDates(liveDate, shootDate, editDate);

        for (WorkflowStatus status : List.of(WorkflowStatus.SA, WorkflowStatus.SIP, WorkflowStatus.SRV)) {
            assertThat(StageDelayPolicy.currentApprovedTarget(status, plan)).as(status.name()).isEqualTo(shootDate);
        }
        for (WorkflowStatus status : List.of(WorkflowStatus.SAP, WorkflowStatus.EA, WorkflowStatus.ED, WorkflowStatus.ERV)) {
            assertThat(StageDelayPolicy.currentApprovedTarget(status, plan)).as(status.name()).isEqualTo(editDate);
        }
        for (WorkflowStatus status : List.of(WorkflowStatus.EAP, WorkflowStatus.RFP, WorkflowStatus.PUBG,
                WorkflowStatus.PP, WorkflowStatus.PFUP)) {
            assertThat(StageDelayPolicy.currentApprovedTarget(status, plan)).as(status.name()).isEqualTo(liveDate);
        }
    }
}
