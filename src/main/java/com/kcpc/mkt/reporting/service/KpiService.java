package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.reporting.dto.KpiSummaryResponse;
import com.kcpc.mkt.reporting.dto.KpiValue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BFD §7: the 30 governed KPIs (KPI-001..030), grouped exactly as the SRS/BFD define them -
 * Operational (001-007, SRS-REQ-071), Productivity (008-011, SRS-REQ-072), Content & Published
 * Unit (012-020, SRS-REQ-073), Approval & Review (021-024, SRS-REQ-074), Delay/SLA/On-Time
 * (025-030, SRS-REQ-075). Computed on demand from operational tables (BR-039) - there are no
 * persistent KPI evaluation tables. All date comparisons use the IST business date.
 *
 * <p>Every KPI's population, numerator/denominator, and date basis is grounded in the exact BFD
 * §7 wording; non-obvious judgment calls (e.g. which timestamp anchors a duration KPI, or what
 * "approved" means for KPI-013/014) are documented inline and in
 * docs/IMPLEMENTATION_DECISIONS.md (ENG-027).
 */
@Service
public class KpiService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    /** BR-039: each active deliverable's "Current Approved Planned Date" is stage-context - the
     * date it is currently working towards, not a fixed single column. */
    private static final String STAGE_PLANNED_DATE_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV','PLAP','SA','SIP','SRV') THEN cp.planned_shoot_date "
                    + "WHEN wi.current_status_code IN ('SAP','EA','ED','ERV') THEN cp.planned_edit_date "
                    + "WHEN wi.current_status_code IN ('EAP','RFP','PUBG','PP','PFUP') THEN cp.planned_live_date "
                    + "ELSE NULL END";

    /** Defect fix: Planning-phase codes (PL/PLRV/PLAP) were previously bucketed into 'Shoot' here.
     * Boundaries now match the already-shipped, already-correct Content Detail stepper
     * (deliverable-detail.jsp's cdStageIndex 0..4: Planning/Shoot/Edit/Publishing/Performance) so
     * the two screens never disagree about which stage a status belongs to - PLAP/SAP stay grouped
     * with Shoot there (shoot-side approval gates), not Planning/Edit. */
    private static final String STAGE_LABEL_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV') THEN 'Planning' "
                    + "WHEN wi.current_status_code IN ('PLAP','SA','SIP','SRV','SAP') THEN 'Shoot' "
                    + "WHEN wi.current_status_code IN ('EA','ED','ERV') THEN 'Edit' "
                    + "WHEN wi.current_status_code IN ('EAP','RFP','PUBG') THEN 'Publishing' "
                    + "WHEN wi.current_status_code IN ('PP','PFUP') THEN 'Performance' "
                    + "ELSE 'Other' END";

    @PersistenceContext
    private EntityManager entityManager;

    private final AuthorizationService authorizationService;

    public KpiService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    private long count(String jpql) {
        return entityManager.createQuery(jpql, Long.class).getSingleResult();
    }

    /** Legacy bespoke summary (kept for the existing GET /api/v1/kpis/summary caller). */
    @Transactional(readOnly = true)
    public KpiSummaryResponse summary(User requester) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_15_TEAM_KPI_VIEW, null, null);

        long pendingWork = count("select count(w) from WorkflowInstance w where w.currentStatusCode not in "
                + "(com.kcpc.mkt.workflow.domain.WorkflowStatus.COMP, com.kcpc.mkt.workflow.domain.WorkflowStatus.CAN, "
                + "com.kcpc.mkt.workflow.domain.WorkflowStatus.RJ, com.kcpc.mkt.workflow.domain.WorkflowStatus.RET)");
        long pendingApprovals = count("select count(r) from ReviewCycle r where r.decidedAt is null");
        long performancePending = count("select count(o) from PerformanceObligation o where o.completed = false");
        long tasksCompleted = count("select count(w) from WorkflowInstance w where "
                + "w.currentStatusCode = com.kcpc.mkt.workflow.domain.WorkflowStatus.COMP");
        long tasksCancelled = count("select count(w) from WorkflowInstance w where "
                + "w.currentStatusCode = com.kcpc.mkt.workflow.domain.WorkflowStatus.CAN");
        long publishedContent = count("select count(distinct e.contentPlan) from ActualPublicationEvent e where "
                + "e.eventType = com.kcpc.mkt.publishing.domain.PublicationEventType.ORIGINAL");
        long ideasSubmitted = count("select count(i) from Idea i");
        long ideasApproved = count("select count(r) from ReviewCycle r where "
                + "r.gateType = com.kcpc.mkt.workflow.domain.GateType.IDEA_REVIEW and r.decision = 'APPROVED'");
        long ideasRejected = count("select count(r) from ReviewCycle r where "
                + "r.gateType = com.kcpc.mkt.workflow.domain.GateType.IDEA_REVIEW and r.decision = 'REJECTED'");
        BigDecimal ideaApprovalRate = rate(ideasApproved, ideasApproved + ideasRejected);

        long approvalsByManager = count("select count(r) from ReviewCycle r where r.decidedAt is not null and "
                + "r.reviewer.businessRole.accessClassCode = com.kcpc.mkt.identity.domain.AccessClass.MARKETING_MANAGER");
        long approvalsByCeo = count("select count(r) from ReviewCycle r where r.decidedAt is not null and "
                + "r.reviewer.businessRole.accessClassCode = com.kcpc.mkt.identity.domain.AccessClass.CEO_OWNER");

        long productionReviews = count("select count(r) from ReviewCycle r where r.decidedAt is not null and "
                + "r.gateType in (com.kcpc.mkt.workflow.domain.GateType.PLANNING_REVIEW, "
                + "com.kcpc.mkt.workflow.domain.GateType.SHOOT_REVIEW, com.kcpc.mkt.workflow.domain.GateType.EDIT_REVIEW)");
        long reworkReviews = count("select count(r) from ReviewCycle r where r.decision = 'REQUEST_REWORK' and "
                + "r.gateType in (com.kcpc.mkt.workflow.domain.GateType.PLANNING_REVIEW, "
                + "com.kcpc.mkt.workflow.domain.GateType.SHOOT_REVIEW, com.kcpc.mkt.workflow.domain.GateType.EDIT_REVIEW)");
        BigDecimal reworkRate = rate(reworkReviews, productionReviews);

        long everStarted = count("select count(w) from WorkflowInstance w");
        BigDecimal completionRate = rate(tasksCompleted, everStarted);

        return new KpiSummaryResponse(pendingWork, pendingApprovals, performancePending, tasksCompleted,
                tasksCancelled, publishedContent, ideasSubmitted, ideasApproved, ideasRejected, ideaApprovalRate,
                approvalsByManager, approvalsByCeo, reworkRate, completionRate);
    }

    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------------------------------
    // API-OP-058: Query 30 Governed KPIs

    @Transactional(readOnly = true)
    public List<KpiValue> queryGovernedKpis(User requester, String category, String kpiCode,
                                             LocalDate startDate, LocalDate endDate) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_15_TEAM_KPI_VIEW, null, null);

        List<KpiValue> all = new ArrayList<>();
        all.addAll(operationalKpis());
        all.addAll(productivityKpis(startDate, endDate));
        all.addAll(contentUnitKpis(startDate, endDate));
        all.addAll(approvalReviewKpis(startDate, endDate));
        all.addAll(delaySlaKpis(startDate, endDate));

        return all.stream()
                .filter(k -> category == null || category.isBlank() || categoryOf(k.getCode()).equalsIgnoreCase(category))
                .filter(k -> kpiCode == null || kpiCode.isBlank() || k.getCode().equalsIgnoreCase(kpiCode))
                .toList();
    }

    private static String categoryOf(String code) {
        int n = Integer.parseInt(code.substring(4));
        if (n <= 7) return "OPERATIONAL";
        if (n <= 11) return "PRODUCTIVITY";
        if (n <= 20) return "CONTENT_UNITS";
        if (n <= 24) return "APPROVAL_REVIEW";
        return "DELAY_SLA";
    }

    // --- 7.1 Operational KPIs (001-007) ------------------------------------------------------

    private List<KpiValue> operationalKpis() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<KpiValue> out = new ArrayList<>();

        long pendingWork = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where wi.current_status_code not in ('COMP','CAN')");
        out.add(KpiValue.scalar("KPI-001", "Pending Work", "OPERATIONAL", String.valueOf(pendingWork)));

        long delayedWork = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where wi.current_status_code not in ('COMP','CAN') and " + STAGE_PLANNED_DATE_CASE + " < :today",
                Map.of("today", today));
        out.add(KpiValue.scalar("KPI-002", "Delayed Work", "OPERATIONAL", String.valueOf(delayedWork)));

        long upcomingShoots = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id where wi.current_status_code not in ('COMP','CAN') "
                + "and cp.planned_shoot_date between :today and :horizon",
                Map.of("today", today, "horizon", today.plusDays(7)));
        out.add(KpiValue.scalar("KPI-003", "Upcoming Shoots (next 7 days)", "OPERATIONAL", String.valueOf(upcomingShoots)));

        long upcomingPublishing = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id where wi.current_status_code not in ('COMP','CAN') "
                + "and cp.planned_live_date between :today and :horizon",
                Map.of("today", today, "horizon", today.plusDays(7)));
        out.add(KpiValue.scalar("KPI-004", "Upcoming Publishing (next 7 days)", "OPERATIONAL", String.valueOf(upcomingPublishing)));

        long pendingApprovals = count("select count(r) from ReviewCycle r where r.decidedAt is null");
        out.add(KpiValue.scalar("KPI-005", "Pending Approvals", "OPERATIONAL", String.valueOf(pendingApprovals)));

        out.add(KpiValue.distribution("KPI-006", "Editor Workload", "OPERATIONAL", groupCounts(
                "select u.full_name, count(*) from editing_assignments ea join users u on u.user_id = ea.editor_user_id "
                        + "where ea.is_active = true group by u.full_name", Map.of())));

        long performancePendingWork = count("select count(o) from PerformanceObligation o where o.completed = false");
        out.add(KpiValue.scalar("KPI-007", "Performance Pending Work", "OPERATIONAL", String.valueOf(performancePendingWork)));

        return out;
    }

    // --- 7.2 Productivity KPIs (008-011) -----------------------------------------------------

    /**
     * ENG-027: KPI-008/009 are explicitly "over monthly periods" in BFD - default to the current
     * IST calendar month when no explicit startDate/endDate is supplied; KPI-010/011 default to
     * all-time (no BFD period qualifier) unless the caller supplies one.
     */
    private List<KpiValue> productivityKpis(LocalDate startDate, LocalDate endDate) {
        List<KpiValue> out = new ArrayList<>();
        LocalDate periodStart = startDate != null ? startDate : YearMonth.now(BUSINESS_ZONE).atDay(1);
        LocalDate periodEnd = endDate != null ? endDate : YearMonth.now(BUSINESS_ZONE).atEndOfMonth();

        // KPI-008: production tasks completed per employee - Shoot/Edit Mark attributions
        // (qualifying-contributor completion evidence) plus Planning plans this employee
        // prepared that were approved (PLAP) in the period.
        Map<String, Long> employeeProductivity = new LinkedHashMap<>();
        groupCounts("select u.full_name, count(*) from personal_mark_attributions pma "
                        + "join users u on u.user_id = pma.recipient_user_id "
                        + "where pma.attributed_at::date between :from and :to group by u.full_name",
                Map.of("from", periodStart, "to", periodEnd)).forEach((k, v) -> employeeProductivity.merge(k, v, Long::sum));
        groupCounts("select u.full_name, count(distinct pp.content_plan_id) from planning_preparers pp "
                        + "join users u on u.user_id = pp.preparer_user_id "
                        + "join workflow_transition_history wth on wth.workflow_instance_id = "
                        + "  (select cp.workflow_instance_id from content_plans cp where cp.content_plan_id = pp.content_plan_id) "
                        + "  and wth.to_status_code = 'PLAP' "
                        + "where wth.transition_timestamp::date between :from and :to group by u.full_name",
                Map.of("from", periodStart, "to", periodEnd)).forEach((k, v) -> employeeProductivity.merge(k, v, Long::sum));
        out.add(KpiValue.distribution("KPI-008", "Employee Productivity (period)", "PRODUCTIVITY", employeeProductivity));

        long managerProductivity = scalarLong("select count(*) from review_cycles r "
                + "join users u on u.user_id = r.reviewer_user_id join business_roles br on br.business_role_id = u.business_role_id "
                + "where r.decided_at is not null and br.access_class_code = 'MARKETING_MANAGER' "
                + "and r.decided_at::date between :from and :to", Map.of("from", periodStart, "to", periodEnd));
        out.add(KpiValue.scalar("KPI-009", "Manager Productivity (period)", "PRODUCTIVITY", String.valueOf(managerProductivity)));

        String dateFilter = startDate != null || endDate != null
                ? " and wi.first_completed_at::date between :from and :to" : "";
        long tasksCompleted = scalarLong("select count(*) from workflow_instances wi "
                + "where wi.first_completed_at is not null" + dateFilter,
                startDate != null || endDate != null
                        ? Map.of("from", periodStart, "to", periodEnd) : Map.of());
        out.add(KpiValue.scalar("KPI-010", "Tasks Completed", "PRODUCTIVITY", String.valueOf(tasksCompleted)));

        String cancelFilter = startDate != null || endDate != null
                ? " and cr.cancelled_at::date between :from and :to" : "";
        long tasksCancelled = scalarLong("select count(*) from cancellation_records cr" + cancelFilter,
                startDate != null || endDate != null
                        ? Map.of("from", periodStart, "to", periodEnd) : Map.of());
        out.add(KpiValue.scalar("KPI-011", "Tasks Cancelled", "PRODUCTIVITY", String.valueOf(tasksCancelled)));

        return out;
    }

    // --- 7.3 Content & Published Unit KPIs (012-020) ----------------------------------------

    private List<KpiValue> contentUnitKpis(LocalDate startDate, LocalDate endDate) {
        List<KpiValue> out = new ArrayList<>();

        long publishedContent = count("select count(distinct e.contentPlan) from ActualPublicationEvent e where "
                + "e.eventType = com.kcpc.mkt.publishing.domain.PublicationEventType.ORIGINAL");
        out.add(KpiValue.scalar("KPI-012", "Published Content", "CONTENT_UNITS", String.valueOf(publishedContent)));

        // ENG-027: "produced and approved" = the output's parent plan has passed Planning Review
        // approval (reached SA or later) - the gate outputs are approved through.
        long reelsProduced = scalarLong("select count(*) from planned_outputs po join content_plans cp "
                + "on cp.content_plan_id = po.content_plan_id join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where po.output_type = 'REEL' and wi.current_status_code not in ('PL','PLRV')");
        out.add(KpiValue.scalar("KPI-013", "Reels Produced", "CONTENT_UNITS", String.valueOf(reelsProduced)));

        long photographyProduced = scalarLong("select count(*) from planned_outputs po join content_plans cp "
                + "on cp.content_plan_id = po.content_plan_id join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where po.output_type = 'PHOTOGRAPHY' and wi.current_status_code not in ('PL','PLRV')");
        out.add(KpiValue.scalar("KPI-014", "Photography Produced", "CONTENT_UNITS", String.valueOf(photographyProduced)));

        out.add(KpiValue.distribution("KPI-015", "Publication Distribution", "CONTENT_UNITS", groupCounts(
                "select p.platform_name || ' / ' || cc.channel_handle, count(*) from actual_publication_events e "
                        + "join publication_targets pt on pt.publication_target_id = e.publication_target_id "
                        + "join platforms p on p.platform_id = pt.platform_id "
                        + "join company_channels cc on cc.channel_id = pt.channel_id "
                        + "where e.event_type = 'ORIGINAL' group by p.platform_name, cc.channel_handle", Map.of())));

        out.add(KpiValue.distribution("KPI-016", "Content by Type", "CONTENT_UNITS", groupCounts(
                "select output_type, count(*) from planned_outputs group by output_type", Map.of())));

        long ideasSubmitted = count("select count(i) from Idea i");
        out.add(KpiValue.scalar("KPI-017", "Ideas Submitted", "CONTENT_UNITS", String.valueOf(ideasSubmitted)));

        long ideasApproved = count("select count(r) from ReviewCycle r where "
                + "r.gateType = com.kcpc.mkt.workflow.domain.GateType.IDEA_REVIEW and r.decision = 'APPROVED'");
        out.add(KpiValue.scalar("KPI-018", "Ideas Approved", "CONTENT_UNITS", String.valueOf(ideasApproved)));

        long ideasRejected = count("select count(r) from ReviewCycle r where "
                + "r.gateType = com.kcpc.mkt.workflow.domain.GateType.IDEA_REVIEW and r.decision = 'REJECTED'");
        out.add(KpiValue.scalar("KPI-019", "Ideas Rejected", "CONTENT_UNITS", String.valueOf(ideasRejected)));

        BigDecimal approvalRate = rate(ideasApproved, ideasApproved + ideasRejected);
        out.add(KpiValue.scalar("KPI-020", "Idea Approval Rate", "CONTENT_UNITS", formatPercent(approvalRate)));

        return out;
    }

    // --- 7.4 Approval & Review KPIs (021-024) ------------------------------------------------

    private List<KpiValue> approvalReviewKpis(LocalDate startDate, LocalDate endDate) {
        List<KpiValue> out = new ArrayList<>();

        Double avgTurnaroundDays = scalarDouble(
                "select avg(extract(epoch from (decided_at - submitted_at)) / 86400.0) "
                        + "from review_cycles where decided_at is not null", Map.of());
        out.add(KpiValue.scalar("KPI-021", "Approval Turnaround Time", "APPROVAL_REVIEW", formatDays(avgTurnaroundDays)));

        long approvalsByManager = count("select count(r) from ReviewCycle r where r.decidedAt is not null and "
                + "r.reviewer.businessRole.accessClassCode = com.kcpc.mkt.identity.domain.AccessClass.MARKETING_MANAGER");
        out.add(KpiValue.scalar("KPI-022", "Approvals by Manager", "APPROVAL_REVIEW", String.valueOf(approvalsByManager)));

        long approvalsByCeo = count("select count(r) from ReviewCycle r where r.decidedAt is not null and "
                + "r.reviewer.businessRole.accessClassCode = com.kcpc.mkt.identity.domain.AccessClass.CEO_OWNER");
        out.add(KpiValue.scalar("KPI-023", "Approvals by CEO", "APPROVAL_REVIEW", String.valueOf(approvalsByCeo)));

        long productionReviews = count("select count(r) from ReviewCycle r where r.decidedAt is not null and "
                + "r.gateType in (com.kcpc.mkt.workflow.domain.GateType.PLANNING_REVIEW, "
                + "com.kcpc.mkt.workflow.domain.GateType.SHOOT_REVIEW, com.kcpc.mkt.workflow.domain.GateType.EDIT_REVIEW)");
        long reworkReviews = count("select count(r) from ReviewCycle r where r.decision = 'REQUEST_REWORK' and "
                + "r.gateType in (com.kcpc.mkt.workflow.domain.GateType.PLANNING_REVIEW, "
                + "com.kcpc.mkt.workflow.domain.GateType.SHOOT_REVIEW, com.kcpc.mkt.workflow.domain.GateType.EDIT_REVIEW)");
        out.add(KpiValue.scalar("KPI-024", "Rework Rate", "APPROVAL_REVIEW", formatPercent(rate(reworkReviews, productionReviews))));

        return out;
    }

    // --- 7.5 Delay & SLA KPIs (025-030) ------------------------------------------------------

    private List<KpiValue> delaySlaKpis(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<KpiValue> out = new ArrayList<>();

        Double avgDelayDays = scalarDouble("select avg((:today ::date - " + STAGE_PLANNED_DATE_CASE + ")) "
                + "from content_plans cp join workflow_instances wi on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where wi.current_status_code not in ('COMP','CAN') and " + STAGE_PLANNED_DATE_CASE + " < :today",
                Map.of("today", today));
        out.add(KpiValue.scalar("KPI-025", "Average Delay (days)", "DELAY_SLA", formatDays(avgDelayDays)));

        long totalPlans = scalarLong("select count(*) from content_plans");
        long completedPlans = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id where wi.current_status_code = 'COMP'");
        out.add(KpiValue.scalar("KPI-026", "Content Completion Rate", "DELAY_SLA", formatPercent(rate(completedPlans, totalPlans))));

        Double avgTurnaround = scalarDouble("select avg(extract(epoch from (wi.first_completed_at - cp.created_at)) / 86400.0) "
                + "from content_plans cp join workflow_instances wi on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where wi.first_completed_at is not null", Map.of());
        out.add(KpiValue.scalar("KPI-027", "Content Turnaround Time", "DELAY_SLA", formatDays(avgTurnaround)));

        // Defect fix: this previously took max(actual_publication_timestamp) over EVERY
        // actual_publication_events row for a plan, including REPOST events - unlike its sibling
        // KPI-030 below (and PipelineDashboardService's "Actual Live Date"/platform-status logic),
        // which both treat only an ORIGINAL event as "the real publish moment". A REPOST can carry
        // a timestamp unrelated to the plan's current Shoot cycle, decoupling last_pub from
        // first_sap and producing an impossible negative cycle time. Filtering to ORIGINAL aligns
        // this with the rest of the codebase; the outer >= guard is defense-in-depth so a
        // chronologically-impossible row is EXCLUDED from the average rather than corrupting it -
        // never a fabricated/corrected number, just real data that can't be a valid cycle.
        Double avgShootToPublish = scalarDouble(
                "select avg(extract(epoch from (last_pub.max_ts - sap.first_sap)) / 86400.0) from ("
                        + "  select workflow_instance_id, min(transition_timestamp) as first_sap "
                        + "  from workflow_transition_history where to_status_code = 'SAP' group by workflow_instance_id"
                        + ") sap join content_plans cp on cp.workflow_instance_id = sap.workflow_instance_id "
                        + "join (select content_plan_id, max(actual_publication_timestamp) as max_ts "
                        + "      from actual_publication_events where event_type = 'ORIGINAL' group by content_plan_id) last_pub "
                        + "  on last_pub.content_plan_id = cp.content_plan_id "
                        + "where last_pub.max_ts >= sap.first_sap", Map.of());
        out.add(KpiValue.scalar("KPI-028", "Shoot-to-Publish Cycle Time", "DELAY_SLA", formatDays(avgShootToPublish)));

        out.add(KpiValue.distribution("KPI-029", "Delay Distribution (by stage)", "DELAY_SLA", groupCounts(
                "select " + STAGE_LABEL_CASE + ", count(*) from content_plans cp "
                        + "join workflow_instances wi on wi.workflow_instance_id = cp.workflow_instance_id "
                        + "where wi.current_status_code not in ('COMP','CAN') and " + STAGE_PLANNED_DATE_CASE + " < :today "
                        + "group by " + STAGE_LABEL_CASE, Map.of("today", today))));

        long applicableEvents = scalarLong("select count(*) from actual_publication_events e "
                + "join content_plans cp on cp.content_plan_id = e.content_plan_id where e.event_type = 'ORIGINAL'");
        long onTimeEvents = scalarLong("select count(*) from actual_publication_events e "
                + "join content_plans cp on cp.content_plan_id = e.content_plan_id "
                + "where e.event_type = 'ORIGINAL' and e.actual_publication_timestamp::date <= cp.planned_live_date");
        out.add(KpiValue.scalar("KPI-030", "On-Time Delivery Rate", "DELAY_SLA", formatPercent(rate(onTimeEvents, applicableEvents))));

        return out;
    }

    // ------------------------------------------------------------------------------------------
    // API-OP-056: Get Team Workload Visibility (Permission #14)

    @Transactional(readOnly = true)
    public Map<String, Object> teamWorkload(User requester) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW, null, null);

        Map<String, Long> byStage = groupCounts("select " + STAGE_LABEL_CASE + ", count(*) from content_plans cp "
                + "join workflow_instances wi on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where wi.current_status_code not in ('COMP','CAN') group by " + STAGE_LABEL_CASE, Map.of());

        Map<String, Long> assigneeLoad = new LinkedHashMap<>();
        groupCounts("select u.full_name, count(*) from shooting_assignments sa join users u on u.user_id = sa.cameraperson_user_id "
                + "where sa.is_active = true group by u.full_name", Map.of()).forEach(assigneeLoad::put);
        groupCounts("select u.full_name, count(*) from editing_assignments ea join users u on u.user_id = ea.editor_user_id "
                + "where ea.is_active = true group by u.full_name", Map.of()).forEach((k, v) -> assigneeLoad.merge(k, v, Long::sum));

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        long delayedCount = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where wi.current_status_code not in ('COMP','CAN') and " + STAGE_PLANNED_DATE_CASE + " < :today",
                Map.of("today", today));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeTasksByStage", byStage);
        result.put("assigneeActiveLoad", assigneeLoad);
        result.put("delayedCount", delayedCount);
        return result;
    }

    // ------------------------------------------------------------------------------------------
    // API-OP-057: Get Team Operational KPIs (Permission #15)

    @Transactional(readOnly = true)
    public Map<String, Object> teamKpis(User requester, LocalDate startDate, LocalDate endDate) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_15_TEAM_KPI_VIEW, null, null);

        long completed = scalarLong("select count(*) from workflow_instances where current_status_code = 'COMP'");
        long inProgress = scalarLong("select count(*) from workflow_instances where current_status_code not in "
                + "('COMP','CAN','RJ','RET')");
        long totalPlans = scalarLong("select count(*) from content_plans");
        BigDecimal completionRate = rate(completed, totalPlans);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        long delayTotal = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                + "on wi.workflow_instance_id = cp.workflow_instance_id "
                + "where wi.current_status_code not in ('COMP','CAN') and " + STAGE_PLANNED_DATE_CASE + " < :today",
                Map.of("today", today));

        long applicableEvents = scalarLong("select count(*) from actual_publication_events where event_type = 'ORIGINAL'");
        long onTimeEvents = scalarLong("select count(*) from actual_publication_events e join content_plans cp "
                + "on cp.content_plan_id = e.content_plan_id "
                + "where e.event_type = 'ORIGINAL' and e.actual_publication_timestamp::date <= cp.planned_live_date");
        BigDecimal onTimeRate = rate(onTimeEvents, applicableEvents);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completed", completed);
        result.put("inProgress", inProgress);
        result.put("completionRate", formatPercent(completionRate));
        result.put("delayTotal", delayTotal);
        result.put("onTimeRate", formatPercent(onTimeRate));
        // Display-only "Data as of" timestamp for the Team > Performance screen header - same
        // query-time-snapshot convention TeamWorkloadResult#generatedAt already uses, not a stored
        // last-refresh concept; no KPI value/formula above is affected by adding this.
        result.put("generatedAt", java.time.Instant.now());
        return result;
    }

    // ------------------------------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private Query nativeQuery(String sql, Map<String, Object> params) {
        Query q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q;
    }

    private long scalarLong(String sql) {
        return scalarLong(sql, Map.of());
    }

    private long scalarLong(String sql, Map<String, Object> params) {
        Object result = nativeQuery(sql, params).getSingleResult();
        return ((Number) result).longValue();
    }

    private Double scalarDouble(String sql, Map<String, Object> params) {
        Object result = nativeQuery(sql, params).getSingleResult();
        return result == null ? null : ((Number) result).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> groupCounts(String sql, Map<String, Object> params) {
        List<Object[]> rows = nativeQuery(sql, params).getResultList();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = String.valueOf(row[0]);
            long value = ((Number) row[1]).longValue();
            result.put(key, value);
        }
        return result;
    }

    private static String formatPercent(BigDecimal rate) {
        return rate == null ? "N/A" : rate + "%";
    }

    private static String formatDays(Double days) {
        return days == null ? "N/A" : BigDecimal.valueOf(days).setScale(2, RoundingMode.HALF_UP) + " days";
    }
}
