package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.performance.domain.CreativePerformanceScorecard;
import com.kcpc.mkt.performance.domain.PerformanceMetricCorrection;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.CreativePerformanceScorecardRepository;
import com.kcpc.mkt.performance.repository.PerformanceMetricCorrectionRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.performance.service.PerformanceService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.NaActionType;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.domain.PublicationEvidenceCorrection;
import com.kcpc.mkt.publishing.domain.PublicationTargetNaRecord;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.publishing.repository.PublicationEvidenceCorrectionRepository;
import com.kcpc.mkt.publishing.repository.PublicationTargetNaRecordRepository;
import com.kcpc.mkt.reporting.dto.AttentionItem;
import com.kcpc.mkt.reporting.dto.ContentPublishingDashboardDto;
import com.kcpc.mkt.reporting.dto.DelayAgingBucket;
import com.kcpc.mkt.reporting.dto.IdeaFunnelDto;
import com.kcpc.mkt.reporting.dto.LabelCountRow;
import com.kcpc.mkt.reporting.dto.LabelValueRow;
import com.kcpc.mkt.reporting.dto.OnHoldSummaryDto;
import com.kcpc.mkt.reporting.dto.OnTimeDeliveryResult;
import com.kcpc.mkt.reporting.dto.OverviewDashboardDto;
import com.kcpc.mkt.reporting.dto.PerformanceDashboardDto;
import com.kcpc.mkt.reporting.dto.QualityReviewsDashboardDto;
import com.kcpc.mkt.reporting.dto.ReviewStageRow;
import com.kcpc.mkt.reporting.dto.StageHealthRow;
import com.kcpc.mkt.reporting.dto.TargetCompletionDto;
import com.kcpc.mkt.reporting.dto.WorkflowSlaDashboardDto;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReopenPurpose;
import com.kcpc.mkt.workflow.domain.ReopenRecord;
import com.kcpc.mkt.workflow.domain.RescheduleRecord;
import com.kcpc.mkt.workflow.domain.StageContext;
import com.kcpc.mkt.workflow.domain.WorkHoldRecord;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory;
import com.kcpc.mkt.workflow.repository.ReopenRecordRepository;
import com.kcpc.mkt.workflow.repository.RescheduleRecordRepository;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centralized KPI Dashboard calculation layer (Reports -&gt; KPI Dashboard: Overview / Workflow
 * &amp; SLA / Content &amp; Publishing / Quality &amp; Reviews / Performance). Every governed
 * formula lives here exactly once - the same method backs both the Overview headline and any
 * detail screen showing the same number (spec §43), and every KPI's numerator/denominator/date
 * basis/exclusions are documented inline at the method that computes it.
 * <p>
 * KPI attribution is never Business-Role-driven (spec §3/§38): every calculation below reads
 * actual assignment/contribution/event records, never {@code User.businessRole}. Viewing this
 * dashboard requires PERM_15_TEAM_KPI_VIEW (checked once per top-level view method); which
 * employee's historical work counts toward a KPI is never filtered by their CURRENT permission
 * state (spec §41/§42) - revoking an execution permission today never erases valid past
 * contribution from historical reports.
 */
@Service
public class KpiDashboardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<String> NON_TERMINAL_EXCLUSIONS = Set.of("COMP", "CAN", "RJ");
    private static final List<String> REVIEW_GATE_NAMES =
            List.of("PLANNING_REVIEW", "SHOOT_REVIEW", "EDIT_REVIEW");

    @PersistenceContext
    private EntityManager entityManager;

    private final AuthorizationService authorizationService;
    private final ContentPlanRepository contentPlanRepository;
    private final IdeaRepository ideaRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;
    private final ReopenRecordRepository reopenRecordRepository;
    private final RescheduleRecordRepository rescheduleRecordRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final ActualPublicationEventRepository eventRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final PublicationTargetNaRecordRepository naRecordRepository;
    private final PublicationEvidenceCorrectionRepository evidenceCorrectionRepository;
    private final PerformanceObligationRepository obligationRepository;
    private final CreativePerformanceScorecardRepository scorecardRepository;
    private final PerformanceMetricCorrectionRepository metricCorrectionRepository;
    private final PerformanceService performanceService;

    public KpiDashboardService(AuthorizationService authorizationService, ContentPlanRepository contentPlanRepository,
                                IdeaRepository ideaRepository, WorkHoldRecordRepository workHoldRecordRepository,
                                ReopenRecordRepository reopenRecordRepository,
                                RescheduleRecordRepository rescheduleRecordRepository,
                                WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                ActualPublicationEventRepository eventRepository,
                                PlannedOutputRepository plannedOutputRepository,
                                PlannedOutputPublicationTargetMappingRepository mappingRepository,
                                PublicationTargetNaRecordRepository naRecordRepository,
                                PublicationEvidenceCorrectionRepository evidenceCorrectionRepository,
                                PerformanceObligationRepository obligationRepository,
                                CreativePerformanceScorecardRepository scorecardRepository,
                                PerformanceMetricCorrectionRepository metricCorrectionRepository,
                                PerformanceService performanceService) {
        this.authorizationService = authorizationService;
        this.contentPlanRepository = contentPlanRepository;
        this.ideaRepository = ideaRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
        this.reopenRecordRepository = reopenRecordRepository;
        this.rescheduleRecordRepository = rescheduleRecordRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.eventRepository = eventRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.mappingRepository = mappingRepository;
        this.naRecordRepository = naRecordRepository;
        this.evidenceCorrectionRepository = evidenceCorrectionRepository;
        this.obligationRepository = obligationRepository;
        this.scorecardRepository = scorecardRepository;
        this.metricCorrectionRepository = metricCorrectionRepository;
        this.performanceService = performanceService;
    }

    private void requireViewAuthority(User requester) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_15_TEAM_KPI_VIEW, null, null);
    }

    // ================================================================================ shared context

    /** Everything every view needs about "current state" - fetched once per request, reused across
     * every section of whichever view is being built, so two sections can never disagree. */
    private final class DashboardContext {
        final LocalDate today = LocalDate.now(BUSINESS_ZONE);
        final LocalDate rangeStart;
        final LocalDate rangeEnd;
        final LocalDate eligibleEnd; // min(rangeEnd, today) - future deadlines are never judged yet
        final List<ContentPlan> allPlans;
        final List<ContentPlan> activePlans; // status not in COMP/CAN/RJ
        final Map<UUID, List<WorkflowTransitionHistory>> transitionsByInstance;
        final Map<UUID, List<ReopenRecord>> publishingReopensByInstance;
        final Map<UUID, List<RescheduleRecord>> reschedulesByInstance;

        DashboardContext(LocalDate rangeStart, LocalDate rangeEnd) {
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.eligibleEnd = rangeEnd.isAfter(today) ? today : rangeEnd;
            this.allPlans = contentPlanRepository.findAllByOrderByCreatedAtDesc();
            this.activePlans = allPlans.stream()
                    .filter(p -> !NON_TERMINAL_EXCLUSIONS.contains(p.getWorkflowInstance().getCurrentStatusCode().name()))
                    .toList();
            Set<UUID> allInstanceIds = allPlans.stream().map(p -> p.getWorkflowInstance().getId()).collect(Collectors.toSet());
            this.transitionsByInstance = allInstanceIds.isEmpty() ? Map.of()
                    : transitionHistoryRepository.findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(allInstanceIds)
                    .stream().collect(Collectors.groupingBy(t -> t.getWorkflowInstance().getId()));
            this.publishingReopensByInstance = allInstanceIds.isEmpty() ? Map.of()
                    : reopenRecordRepository.findByWorkflowInstance_IdInAndReopenPurposeOrderByReopenedAtAsc(
                            allInstanceIds, ReopenPurpose.PUBLISHING_REOPEN)
                    .stream().collect(Collectors.groupingBy(r -> r.getWorkflowInstance().getId()));
            this.reschedulesByInstance = allInstanceIds.isEmpty() ? Map.of()
                    : rescheduleRecordRepository.findByWorkflowInstance_IdInOrderByRescheduledAtAsc(allInstanceIds)
                    .stream().collect(Collectors.groupingBy(r -> r.getWorkflowInstance().getId()));
        }

        List<WorkflowTransitionHistory> transitionsFor(ContentPlan plan) {
            return transitionsByInstance.getOrDefault(plan.getWorkflowInstance().getId(), List.of());
        }

        List<ReopenRecord> publishingReopensFor(ContentPlan plan) {
            return publishingReopensByInstance.getOrDefault(plan.getWorkflowInstance().getId(), List.of());
        }

        List<RescheduleRecord> reschedulesFor(ContentPlan plan) {
            return reschedulesByInstance.getOrDefault(plan.getWorkflowInstance().getId(), List.of());
        }
    }

    private DashboardContext buildContext(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate start = startDate != null ? startDate : today.minusDays(29);
        LocalDate end = endDate != null ? endDate : today;
        return new DashboardContext(start, end);
    }

    private boolean inRange(Instant instant, LocalDate start, LocalDate end) {
        if (instant == null) {
            return false;
        }
        LocalDate d = instant.atZone(BUSINESS_ZONE).toLocalDate();
        return !d.isBefore(start) && !d.isAfter(end);
    }

    private static String stageLabel(WorkflowStatus status) {
        return switch (status) {
            case PL, PLRV -> "Planning";
            case PLAP, SA, SIP, SRV, SAP -> "Shoot";
            case EA, ED, ERV -> "Edit";
            case EAP, RFP, PUBG -> "Publishing";
            case PP, PFUP -> "Performance";
            default -> "Other";
        };
    }

    private static LocalDate currentApprovedTarget(ContentPlan plan) {
        WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
        return switch (status) {
            case PL, PLRV, PLAP, SA, SIP, SRV -> plan.getPlannedShootDate();
            case SAP, EA, ED, ERV -> plan.getPlannedEditDate();
            case EAP, RFP, PUBG, PP, PFUP -> plan.getPlannedLiveDate();
            default -> null;
        };
    }

    // ================================================================================ Stage Health
    // (spec §8 Stage Bottleneck Summary / §11 Workflow & SLA stage table) - the SAME computation
    // backs both screens (§43). Active/Delayed reuse the exact same current-approved-target logic
    // AdminReportingService#delayedDeliverables already uses (StageSqlFragments). Age = time since
    // this plan most recently transitioned INTO its current exact status (a rework round that sends
    // a plan back to an earlier status correctly resets its age in that stage). Within SLA % is the
    // approved point-in-time snapshot formula: (Active - Delayed) / Active * 100, null when Active
    // == 0 - never confused with historical On-Time Delivery.

    private List<StageHealthRow> stageHealth(DashboardContext ctx) {
        Map<String, List<ContentPlan>> byStage = ctx.activePlans.stream()
                .collect(Collectors.groupingBy(p -> stageLabel(p.getWorkflowInstance().getCurrentStatusCode()),
                        LinkedHashMap::new, Collectors.toList()));
        List<StageHealthRow> rows = new ArrayList<>();
        for (String stage : List.of("Planning", "Shoot", "Edit", "Publishing", "Performance")) {
            List<ContentPlan> plans = byStage.getOrDefault(stage, List.of());
            long active = plans.size();
            long delayed = 0;
            List<Double> ageDaysList = new ArrayList<>();
            double oldestAge = -1;
            String oldestContentId = null;
            for (ContentPlan plan : plans) {
                LocalDate target = currentApprovedTarget(plan);
                if (target != null && target.isBefore(ctx.today)) {
                    delayed++;
                }
                Instant enteredCurrentStatus = mostRecentEntryIntoCurrentStatus(plan, ctx);
                if (enteredCurrentStatus != null) {
                    double ageDays = ChronoUnit.SECONDS.between(enteredCurrentStatus, Instant.now()) / 86400.0;
                    ageDaysList.add(ageDays);
                    if (ageDays > oldestAge) {
                        oldestAge = ageDays;
                        oldestContentId = plan.getContentId();
                    }
                }
            }
            Double withinSla = active == 0 ? null
                    : BigDecimal.valueOf((active - delayed) * 100.0 / active).setScale(1, RoundingMode.HALF_UP).doubleValue();
            Double avgAge = ageDaysList.isEmpty() ? null
                    : ageDaysList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Long oldestAgeDays = oldestAge < 0 ? null : Math.round(oldestAge);
            rows.add(new StageHealthRow(stage, active, delayed, withinSla, avgAge, oldestAgeDays, oldestContentId));
        }
        return rows;
    }

    private Instant mostRecentEntryIntoCurrentStatus(ContentPlan plan, DashboardContext ctx) {
        WorkflowStatus current = plan.getWorkflowInstance().getCurrentStatusCode();
        List<WorkflowTransitionHistory> transitions = ctx.transitionsFor(plan);
        Instant latest = null;
        for (WorkflowTransitionHistory t : transitions) {
            if (t.getToStatusCode() == current) {
                latest = t.getTransitionTimestamp();
            }
        }
        return latest;
    }

    // ================================================================================ Publishing cycles
    // (spec §17-20, approved algorithm) - one binary SLA observation per Publishing cycle, never
    // per event, never per Platform x Channel target.

    private record PublishingCycle(Instant windowStart, Instant windowEndExclusive, LocalDate deadline) {
    }

    /** Original-cycle deadline: the approved Planned Live Date effective immediately before the
     * first PUBLISHING_REOPEN (approved exactly as specified - 4 branches, any stageContext
     * considered since stageContext never restricts which date fields a reschedule changes). */
    private LocalDate resolveOriginalCycleDeadline(ContentPlan plan, List<ReopenRecord> publishingReopensAsc,
                                                    List<RescheduleRecord> allReschedulesAsc) {
        if (publishingReopensAsc.isEmpty()) {
            return plan.getPlannedLiveDate();
        }
        Instant firstReopenAt = publishingReopensAsc.get(0).getReopenedAt();
        RescheduleRecord latestPreReopen = null;
        RescheduleRecord earliestPostReopen = null;
        for (RescheduleRecord r : allReschedulesAsc) {
            if (r.getRescheduledAt().isBefore(firstReopenAt)) {
                latestPreReopen = r;
            } else if (earliestPostReopen == null) {
                earliestPostReopen = r;
            }
        }
        if (latestPreReopen != null) {
            return latestPreReopen.getNewPlannedLiveDate();
        }
        if (earliestPostReopen != null) {
            return earliestPostReopen.getPriorPlannedLiveDate();
        }
        return plan.getPlannedLiveDate();
    }

    /** Repost-cycle deadline: latest approved stageContext=PUBLISHING reschedule inside
     * [windowStart, windowEndExclusive) - null ("Target Pending") if none exists yet. */
    private LocalDate resolveRepostCycleDeadline(Instant windowStart, Instant windowEndExclusive,
                                                  List<RescheduleRecord> allReschedulesAsc) {
        LocalDate latest = null;
        for (RescheduleRecord r : allReschedulesAsc) {
            if (r.getStageContext() != StageContext.PUBLISHING) {
                continue;
            }
            Instant rt = r.getRescheduledAt();
            if (!rt.isBefore(windowStart) && (windowEndExclusive == null || rt.isBefore(windowEndExclusive))) {
                latest = r.getNewPlannedLiveDate();
            }
        }
        return latest;
    }

    /** Every Publishing cycle this plan has ever had (original first, then each repost in order). */
    private List<PublishingCycle> publishingCyclesFor(ContentPlan plan, DashboardContext ctx) {
        List<ReopenRecord> reopens = ctx.publishingReopensFor(plan);
        List<RescheduleRecord> reschedules = ctx.reschedulesFor(plan);
        List<PublishingCycle> cycles = new ArrayList<>();
        Instant firstReopenAt = reopens.isEmpty() ? null : reopens.get(0).getReopenedAt();
        cycles.add(new PublishingCycle(null, firstReopenAt,
                resolveOriginalCycleDeadline(plan, reopens, reschedules)));
        for (int i = 0; i < reopens.size(); i++) {
            Instant windowStart = reopens.get(i).getReopenedAt();
            Instant windowEnd = i + 1 < reopens.size() ? reopens.get(i + 1).getReopenedAt() : null;
            cycles.add(new PublishingCycle(windowStart, windowEnd,
                    resolveRepostCycleDeadline(windowStart, windowEnd, reschedules)));
        }
        return cycles;
    }

    /** Earliest PUBLICATION_SCOPE_RESOLVED transition (PublishingService's exact trigger command)
     * within [windowStart, windowEndExclusive) - the moment this specific cycle's Publishing Scope
     * fully resolved (all required targets live-or-N/A), or null if not yet resolved in that window. */
    private Instant scopeResolvedWithin(ContentPlan plan, Instant windowStart, Instant windowEndExclusive,
                                         DashboardContext ctx) {
        for (WorkflowTransitionHistory t : ctx.transitionsFor(plan)) {
            if (t.getToStatusCode() != WorkflowStatus.PP
                    || !"PUBLICATION_SCOPE_RESOLVED".equals(t.getTriggerCommand())) {
                continue;
            }
            Instant ts = t.getTransitionTimestamp();
            if (windowStart != null && ts.isBefore(windowStart)) {
                continue;
            }
            if (windowEndExclusive != null && !ts.isBefore(windowEndExclusive)) {
                continue;
            }
            return ts;
        }
        return null;
    }

    /** The governed per-cycle On-Time Delivery result (spec §17-20) across every plan's every
     * Publishing cycle whose deadline falls in [ctx.rangeStart, ctx.eligibleEnd]. */
    private OnTimeDeliveryResult onTimeDelivery(DashboardContext ctx) {
        long eligible = 0;
        long onTime = 0;
        for (ContentPlan plan : ctx.allPlans) {
            for (PublishingCycle cycle : publishingCyclesFor(plan, ctx)) {
                if (cycle.deadline() == null) {
                    continue; // Target Pending - excluded until a valid target exists
                }
                if (cycle.deadline().isBefore(ctx.rangeStart) || cycle.deadline().isAfter(ctx.eligibleEnd)) {
                    continue; // not due in the selected (and not-yet-future) period
                }
                eligible++;
                Instant resolvedAt = scopeResolvedWithin(plan, cycle.windowStart(), cycle.windowEndExclusive(), ctx);
                if (resolvedAt != null
                        && !resolvedAt.atZone(BUSINESS_ZONE).toLocalDate().isAfter(cycle.deadline())) {
                    onTime++;
                }
            }
        }
        BigDecimal percent = eligible == 0 ? null
                : BigDecimal.valueOf(onTime * 100.0 / eligible).setScale(1, RoundingMode.HALF_UP);
        return new OnTimeDeliveryResult(eligible, onTime, percent);
    }

    /** Original-cycle PUBLICATION_SCOPE_RESOLVED timestamp - the same cycle-0 boundary
     * {@link #onTimeDelivery} already evaluates, reused here so Avg End-to-End Cycle Time can never
     * disagree with it about when a plan's original publishing scope actually resolved. */
    private Instant originalCycleResolvedAt(ContentPlan plan, DashboardContext ctx) {
        List<ReopenRecord> reopens = ctx.publishingReopensFor(plan);
        Instant firstReopenAt = reopens.isEmpty() ? null : reopens.get(0).getReopenedAt();
        return scopeResolvedWithin(plan, null, firstReopenAt, ctx);
    }

    // ================================================================================ OVERVIEW (spec §7-10)

    @Transactional(readOnly = true)
    public OverviewDashboardDto overview(User requester, LocalDate startDate, LocalDate endDate) {
        requireViewAuthority(requester);
        DashboardContext ctx = buildContext(startDate, endDate);

        long activeWip = ctx.activePlans.size();
        long delayed = ctx.activePlans.stream().filter(p -> {
            LocalDate target = currentApprovedTarget(p);
            return target != null && target.isBefore(ctx.today);
        }).count();

        OnTimeDeliveryResult onTime = onTimeDelivery(ctx);

        long publishedContent = publishedContentCount(ctx.rangeStart, ctx.rangeEnd);

        Double avgEndToEnd = avgEndToEndCycleTimeDays(ctx);

        BigDecimal reworkRate = productionReworkRate(ctx.rangeStart, ctx.rangeEnd);
        long pendingReviews = pendingReviewsCount();
        long performanceOverdue = performanceOverdueCount(ctx.today);

        List<StageHealthRow> stageHealth = stageHealth(ctx);
        List<AttentionItem> attention = attentionItems(ctx, stageHealth, performanceOverdue);
        IdeaFunnelDto funnel = ideaFunnel(ctx);

        return new OverviewDashboardDto(activeWip, delayed, onTime, publishedContent, avgEndToEnd, reworkRate,
                pendingReviews, performanceOverdue, stageHealth, attention, funnel);
    }

    /** Avg End-to-End Cycle Time (spec correction, approved): Idea.submittedAt -&gt; the ORIGINAL
     * cycle's PUBLICATION_SCOPE_RESOLVED timestamp - completion of the publishing scope, not first
     * publication. Period-scoped by when that resolution happened. Single source of truth, reused
     * identically by Overview and Workflow &amp; SLA. */
    private Double avgEndToEndCycleTimeDays(DashboardContext ctx) {
        List<Double> cycleTimes = new ArrayList<>();
        for (ContentPlan plan : ctx.allPlans) {
            Instant resolvedAt = originalCycleResolvedAt(plan, ctx);
            if (resolvedAt == null || !inRange(resolvedAt, ctx.rangeStart, ctx.rangeEnd)) {
                continue;
            }
            Idea idea = ideaByWorkflowInstance(plan.getWorkflowInstance().getId());
            if (idea == null || idea.getSubmittedAt() == null) {
                continue;
            }
            cycleTimes.add(ChronoUnit.SECONDS.between(idea.getSubmittedAt(), resolvedAt) / 86400.0);
        }
        return cycleTimes.isEmpty() ? null : cycleTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private Idea ideaByWorkflowInstance(UUID workflowInstanceId) {
        List<Idea> matches = entityManager
                .createQuery("select i from Idea i where i.workflowInstance.id = :wiId", Idea.class)
                .setParameter("wiId", workflowInstanceId).setMaxResults(1).getResultList();
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** KPI-024's exact rate shape (rework / decided productions reviews), date-ranged by decidedAt -
     * reused as-is by Overview and Quality &amp; Reviews so the two screens can never disagree. */
    private BigDecimal productionReworkRate(LocalDate start, LocalDate end) {
        long decided = scalarLong("select count(*) from review_cycles where gate_type = any(:gates) "
                        + "and decided_at is not null and decided_at::date between :from and :to",
                Map.of("gates", REVIEW_GATE_NAMES.toArray(new String[0]), "from", start, "to", end));
        long rework = scalarLong("select count(*) from review_cycles where gate_type = any(:gates) "
                        + "and decision = 'REQUEST_REWORK' and decided_at::date between :from and :to",
                Map.of("gates", REVIEW_GATE_NAMES.toArray(new String[0]), "from", start, "to", end));
        return rate(rework, decided);
    }

    /** Always a current-state snapshot (like Active WIP), never date-ranged - "how many reviews are
     * pending right now." Idea/Planning/Shoot/Edit gates only - Publishing has no ReviewCycle gate. */
    private long pendingReviewsCount() {
        return scalarLong("select count(*) from review_cycles where decided_at is null", Map.of());
    }

    /** Distinct Content Plans with an ORIGINAL publication event whose actual timestamp falls in
     * range - single source of truth reused by Overview and Content &amp; Publishing. */
    private long publishedContentCount(LocalDate start, LocalDate end) {
        return scalarLong("select count(distinct content_plan_id) from actual_publication_events "
                        + "where event_type = 'ORIGINAL' and actual_publication_timestamp::date between :from and :to",
                Map.of("from", start, "to", end));
    }

    private long performanceOverdueCount(LocalDate today) {
        return scalarLong("select count(*) from performance_obligations where is_completed = false "
                + "and performance_due_date < :today", Map.of("today", today));
    }

    /** spec §9: real data only, clickable through to the existing operational screen - never a
     * duplicated detail view inside the KPI Dashboard itself. */
    private List<AttentionItem> attentionItems(DashboardContext ctx, List<StageHealthRow> stageHealth,
                                                long performanceOverdue) {
        List<AttentionItem> items = new ArrayList<>();
        StageHealthRow planning = stageHealth.stream().filter(r -> "Planning".equals(r.getStage())).findFirst().orElse(null);
        if (planning != null && planning.getDelayed() > 0) {
            items.add(new AttentionItem("items delayed in Planning", planning.getDelayed(),
                    "/app/reports/delayed?stage=Planning"));
        }
        long pendingOver2Days = scalarLong("select count(*) from review_cycles where decided_at is null "
                + "and submitted_at < :cutoff", Map.of("cutoff", Instant.now().minus(2, ChronoUnit.DAYS)));
        if (pendingOver2Days > 0) {
            items.add(new AttentionItem("reviews pending longer than 2 days", pendingOver2Days, "/app/reviews"));
        }
        if (performanceOverdue > 0) {
            items.add(new AttentionItem("performance scorecards overdue", performanceOverdue,
                    "/app/reports/kpis?view=performance"));
        }
        long onHold = workHoldRecordRepository.findByResumedAtIsNull().size();
        if (onHold > 0) {
            items.add(new AttentionItem("content items on hold", onHold, "/app/reports/kpis?view=workflow"));
        }
        long highPriorityDelayed = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                        + "on wi.workflow_instance_id = cp.workflow_instance_id "
                        + "where wi.current_status_code not in ('COMP','CAN','RJ') and cp.content_priority = 'HIGH' "
                        + "and " + StageSqlFragments.STAGE_PLANNED_DATE_CASE + " < :today",
                Map.of("today", ctx.today));
        if (highPriorityDelayed > 0) {
            items.add(new AttentionItem("high priority items delayed", highPriorityDelayed,
                    "/app/reports/delayed?priority=HIGH"));
        }
        return items;
    }

    /** spec §10 (cohort-consistency fix): every count in this funnel is the SAME submitted-in-range
     * Idea cohort, tracked forward to its CURRENT outcome - never a separate "decided in range"
     * population (mixing submitted-cohort and decided-cohort dates could make Approved + Retained +
     * Rejected exceed Submitted). Each submitted idea lands in exactly one of Approved/Retained/
     * Rejected/still-pending: Approved = a Content Plan now exists for it (permanent proof of
     * approval, even if the idea was Retained-then-reopened-then-approved on a later cycle);
     * Retained/Rejected = its CURRENT workflow status, never a historical decision-row count (which
     * could double-count an idea that was Retained then later reopened and Approved). Approval Rate
     * still excludes Retained from its denominator (matches the existing, already-governed KPI-020
     * shape). */
    private IdeaFunnelDto ideaFunnel(DashboardContext ctx) {
        long submitted = scalarLong("select count(*) from ideas where submitted_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long approved = scalarLong("select count(*) from ideas i join content_plans cp on cp.idea_id = i.idea_id "
                        + "where i.submitted_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long retained = scalarLong("select count(*) from ideas i join workflow_instances wi "
                        + "on wi.workflow_instance_id = i.workflow_instance_id "
                        + "where wi.current_status_code = 'RET' and i.submitted_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long rejected = scalarLong("select count(*) from ideas i join workflow_instances wi "
                        + "on wi.workflow_instance_id = i.workflow_instance_id "
                        + "where wi.current_status_code = 'RJ' and i.submitted_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        // "Planned" = the approved idea's plan has reached Planning Review or later (matches the
        // existing KPI-013/014 "produced" precedent: past PL/PLRV means Planning Details were
        // actually submitted, not just a bare plan shell created on approval).
        long planned = scalarLong("select count(*) from content_plans cp join workflow_instances wi "
                        + "on wi.workflow_instance_id = cp.workflow_instance_id join ideas i on i.idea_id = cp.idea_id "
                        + "where wi.current_status_code not in ('PL','PLRV') "
                        + "and i.submitted_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long published = scalarLong("select count(distinct e.content_plan_id) from actual_publication_events e "
                        + "join content_plans cp on cp.content_plan_id = e.content_plan_id "
                        + "join ideas i on i.idea_id = cp.idea_id where e.event_type = 'ORIGINAL' "
                        + "and i.submitted_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        BigDecimal approvalRate = rate(approved, approved + rejected);
        return new IdeaFunnelDto(submitted, approved, retained, rejected, planned, published, approvalRate);
    }

    // ================================================================================ SQL helpers
    // (mirrors KpiService's established raw-SQL helper style for consistency)

    @SuppressWarnings("unchecked")
    private Query nativeQuery(String sql, Map<String, Object> params) {
        Query q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q;
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
    private List<Object[]> rows(String sql, Map<String, Object> params) {
        return nativeQuery(sql, params).getResultList();
    }

    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }

    // ================================================================================ WORKFLOW & SLA (spec §11-16)

    @Transactional(readOnly = true)
    public WorkflowSlaDashboardDto workflowSla(User requester, LocalDate startDate, LocalDate endDate) {
        requireViewAuthority(requester);
        DashboardContext ctx = buildContext(startDate, endDate);

        List<StageHealthRow> stageHealth = stageHealth(ctx);

        Double planningTurnaround = scalarDouble(
                "select avg(extract(epoch from (plap.ts - pl.ts)) / 86400.0) from ("
                        + "  select workflow_instance_id, min(transition_timestamp) as ts "
                        + "  from workflow_transition_history where to_status_code = 'PL' group by workflow_instance_id"
                        + ") pl join (select workflow_instance_id, min(transition_timestamp) as ts "
                        + "  from workflow_transition_history where to_status_code = 'PLAP' group by workflow_instance_id"
                        + ") plap on plap.workflow_instance_id = pl.workflow_instance_id "
                        + "where plap.ts >= pl.ts and plap.ts::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));

        Double shootToPublish = scalarDouble(
                "select avg(extract(epoch from (last_pub.max_ts - sap.first_sap)) / 86400.0) from ("
                        + "  select workflow_instance_id, min(transition_timestamp) as first_sap "
                        + "  from workflow_transition_history where to_status_code = 'SAP' group by workflow_instance_id"
                        + ") sap join content_plans cp on cp.workflow_instance_id = sap.workflow_instance_id "
                        + "join (select content_plan_id, max(actual_publication_timestamp) as max_ts "
                        + "      from actual_publication_events where event_type = 'ORIGINAL' group by content_plan_id) last_pub "
                        + "  on last_pub.content_plan_id = cp.content_plan_id "
                        + "where last_pub.max_ts >= sap.first_sap and last_pub.max_ts::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));

        Double endToEnd = avgEndToEndCycleTimeDays(ctx);
        OnTimeDeliveryResult onTime = onTimeDelivery(ctx);

        List<ContentPlan> delayedActive = ctx.activePlans.stream().filter(p -> {
            LocalDate target = currentApprovedTarget(p);
            return target != null && target.isBefore(ctx.today);
        }).toList();
        List<Long> delayDaysList = delayedActive.stream()
                .map(p -> ChronoUnit.DAYS.between(currentApprovedTarget(p), ctx.today)).toList();
        Double avgDelay = delayDaysList.isEmpty() ? null
                : delayDaysList.stream().mapToLong(Long::longValue).average().orElse(0);
        List<DelayAgingBucket> aging = delayAgingBuckets(delayDaysList);

        OnHoldSummaryDto onHold = onHoldSummary(ctx);

        long reopenedCount = scalarLong("select count(*) from reopen_records where reopened_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long repostCount = scalarLong("select count(*) from actual_publication_events "
                        + "where event_type = 'REPOST' and actual_publication_timestamp::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));

        return new WorkflowSlaDashboardDto(stageHealth, planningTurnaround, shootToPublish, endToEnd, onTime,
                avgDelay, aging, onHold, reopenedCount, repostCount);
    }

    /** spec §13: reuse a governed bucket definition if one exists elsewhere - none does, so this is
     * the dashboard's own definition (0-2 / 3-5 / 6-10 / 11+ days), applied consistently everywhere
     * delay aging is shown. */
    private List<DelayAgingBucket> delayAgingBuckets(List<Long> delayDaysList) {
        long b1 = delayDaysList.stream().filter(d -> d >= 0 && d <= 2).count();
        long b2 = delayDaysList.stream().filter(d -> d >= 3 && d <= 5).count();
        long b3 = delayDaysList.stream().filter(d -> d >= 6 && d <= 10).count();
        long b4 = delayDaysList.stream().filter(d -> d >= 11).count();
        return List.of(new DelayAgingBucket("0-2 days", b1), new DelayAgingBucket("3-5 days", b2),
                new DelayAgingBucket("6-10 days", b3), new DelayAgingBucket("11+ days", b4));
    }

    /** spec §14: open count is a live snapshot (resumedAt IS NULL, never a resumed historical hold);
     * avg/longest duration are computed over RESUMED (completed) holds within the selected period.
     * {@code resumedHoldCountInRange} is an explicit, unambiguous {@code COUNT(*)} availability
     * signal - avg/longest are only queried (and only ever non-null) when that count is positive,
     * so "no applicable data" is never merely inferred from an AVG/MAX query happening to return
     * null; it is a always a distinct, directly-verifiable population size. */
    private OnHoldSummaryDto onHoldSummary(DashboardContext ctx) {
        long openCount = workHoldRecordRepository.findByResumedAtIsNull().size();
        long resumedCount = scalarLong("select count(*) from work_hold_records "
                        + "where resumed_at is not null and resumed_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        Double avgDuration = resumedCount == 0 ? null
                : scalarDouble("select avg(extract(epoch from (resumed_at - held_at)) / 86400.0) "
                        + "from work_hold_records where resumed_at is not null and resumed_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        Double longest = resumedCount == 0 ? null
                : scalarDouble("select max(extract(epoch from (resumed_at - held_at)) / 86400.0) "
                        + "from work_hold_records where resumed_at is not null and resumed_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        return new OnHoldSummaryDto(openCount, resumedCount, avgDuration, longest);
    }

    // ================================================================================ CONTENT & PUBLISHING (spec §21-27)

    @Transactional(readOnly = true)
    public ContentPublishingDashboardDto contentPublishing(User requester, LocalDate startDate, LocalDate endDate) {
        requireViewAuthority(requester);
        DashboardContext ctx = buildContext(startDate, endDate);

        long published = publishedContentCount(ctx.rangeStart, ctx.rangeEnd);
        long originalCount = scalarLong("select count(*) from actual_publication_events "
                        + "where event_type = 'ORIGINAL' and actual_publication_timestamp::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long repostCount = scalarLong("select count(*) from actual_publication_events "
                        + "where event_type = 'REPOST' and actual_publication_timestamp::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));

        // spec §27: raw count vs distinct-events-corrected rate - never raw/events (would distort
        // the rate if one event has multiple corrections).
        long allEventsInRange = originalCount + repostCount;
        long rawCorrections = scalarLong("select count(*) from publication_evidence_corrections pec "
                        + "join actual_publication_events e on e.event_id = pec.event_id "
                        + "where e.actual_publication_timestamp::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long distinctCorrectedEvents = scalarLong("select count(distinct pec.event_id) from publication_evidence_corrections pec "
                        + "join actual_publication_events e on e.event_id = pec.event_id "
                        + "where e.actual_publication_timestamp::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        BigDecimal correctionRate = rate(distinctCorrectedEvents, allEventsInRange);

        List<LabelCountRow> contentMix = contentMixByType(ctx);
        List<LabelCountRow> platformDist = groupCountsRows(
                "select p.platform_name, count(*) from actual_publication_events e "
                        + "join publication_targets pt on pt.publication_target_id = e.publication_target_id "
                        + "join platforms p on p.platform_id = pt.platform_id "
                        + "where e.actual_publication_timestamp::date between :from and :to group by p.platform_name "
                        + "order by count(*) desc",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        List<LabelCountRow> channelDist = groupCountsRows(
                "select cc.channel_handle, count(*) from actual_publication_events e "
                        + "join publication_targets pt on pt.publication_target_id = e.publication_target_id "
                        + "join company_channels cc on cc.channel_id = pt.channel_id "
                        + "where e.actual_publication_timestamp::date between :from and :to group by cc.channel_handle "
                        + "order by count(*) desc limit 5",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));

        TargetCompletionDto targetCompletion = publishingTargetCompletion(ctx);

        return new ContentPublishingDashboardDto(published, originalCount, repostCount, rawCorrections, correctionRate,
                contentMix, platformDist, channelDist, targetCompletion);
    }

    private List<LabelCountRow> groupCountsRows(String sql, Map<String, Object> params) {
        List<LabelCountRow> out = new ArrayList<>();
        for (Object[] row : rows(sql, params)) {
            out.add(new LabelCountRow(String.valueOf(row[0]), ((Number) row[1]).longValue()));
        }
        return out;
    }

    /** spec §22: actual system OutputType/ReelType values only - REEL rows further split by
     * ReelType (VERY_SHORT/SHORT/LONG), never an invented category. Date-ranged by the output's
     * own creation timestamp. */
    private List<LabelCountRow> contentMixByType(DashboardContext ctx) {
        List<Object[]> rawRows = rows("select output_type, reel_type, count(*) from planned_outputs "
                        + "where created_at::date between :from and :to group by output_type, reel_type",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        Map<String, Long> byLabel = new LinkedHashMap<>();
        for (Object[] row : rawRows) {
            String outputType = String.valueOf(row[0]);
            Object reelType = row[1];
            String label = reelType != null ? "REEL · " + reelType : outputType;
            byLabel.merge(label, ((Number) row[2]).longValue(), Long::sum);
        }
        return byLabel.entrySet().stream().map(e -> new LabelCountRow(e.getKey(), e.getValue())).toList();
    }

    /** Publishing Target Completion (spec §21/§25, denominator locked, approved): published
     * non-N/A mappings / all non-N/A mappings. N/A mappings (latest PublicationTargetNaRecord
     * action = DESIGNATED) are excluded from both numerator and denominator entirely - reuses the
     * exact same designated-N/A exclusion {@code DeliverableMvcController#buildPublishingChecklist}
     * already applies, never a newly-invented rule. Date-ranged by the output's own creation
     * timestamp, consistent with Content Mix. */
    private TargetCompletionDto publishingTargetCompletion(DashboardContext ctx) {
        List<PlannedOutput> outputs = plannedOutputRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && inRange(o.getCreatedAt(), ctx.rangeStart, ctx.rangeEnd))
                .toList();
        if (outputs.isEmpty()) {
            return new TargetCompletionDto(0, 0, 0, null);
        }
        Set<UUID> outputIds = outputs.stream().map(PlannedOutput::getId).collect(Collectors.toSet());
        Set<UUID> planIds = outputs.stream().map(o -> o.getContentPlan().getId()).collect(Collectors.toSet());
        Map<UUID, List<PlannedOutputPublicationTargetMapping>> mappingsByOutput = mappingRepository
                .findByPlannedOutput_IdIn(outputIds).stream()
                .collect(Collectors.groupingBy(m -> m.getPlannedOutput().getId()));
        Map<UUID, List<PublicationTargetNaRecord>> naByOutput = naRecordRepository.findByPlannedOutput_IdIn(outputIds)
                .stream().collect(Collectors.groupingBy(n -> n.getPlannedOutput().getId()));
        Map<UUID, List<ActualPublicationEvent>> eventsByOutput = eventRepository.findByContentPlan_IdIn(planIds)
                .stream().filter(e -> e.getEventType() == PublicationEventType.ORIGINAL)
                .collect(Collectors.groupingBy(e -> e.getPlannedOutput().getId()));

        long published = 0;
        long pending = 0;
        long na = 0;
        for (PlannedOutput output : outputs) {
            for (PlannedOutputPublicationTargetMapping mapping : mappingsByOutput.getOrDefault(output.getId(), List.of())) {
                UUID targetId = mapping.getPublicationTarget().getId();
                boolean isNa = naByOutput.getOrDefault(output.getId(), List.of()).stream()
                        .filter(n -> n.getPublicationTarget().getId().equals(targetId))
                        .max(Comparator.comparing(PublicationTargetNaRecord::getRecordedAt))
                        .map(n -> n.getActionType() == NaActionType.DESIGNATED).orElse(false);
                if (isNa) {
                    na++;
                    continue;
                }
                boolean isPublished = eventsByOutput.getOrDefault(output.getId(), List.of()).stream()
                        .anyMatch(e -> e.getPublicationTarget().getId().equals(targetId));
                if (isPublished) {
                    published++;
                } else {
                    pending++;
                }
            }
        }
        BigDecimal completionPercent = rate(published, published + pending);
        return new TargetCompletionDto(published, pending, na, completionPercent);
    }

    /** Raw correction count in range - shared unmodified between Content &amp; Publishing (§21) and
     * Quality &amp; Reviews (§28) headline cards so the two screens can never disagree. */
    private long evidenceCorrectionCount(LocalDate start, LocalDate end) {
        return scalarLong("select count(*) from publication_evidence_corrections pec "
                        + "join actual_publication_events e on e.event_id = pec.event_id "
                        + "where e.actual_publication_timestamp::date between :from and :to",
                Map.of("from", start, "to", end));
    }

    // ================================================================================ QUALITY & REVIEWS (spec §28-32)

    @Transactional(readOnly = true)
    public QualityReviewsDashboardDto qualityReviews(User requester, LocalDate startDate, LocalDate endDate) {
        requireViewAuthority(requester);
        DashboardContext ctx = buildContext(startDate, endDate);

        long firstPassDecided = scalarLong("select count(*) from review_cycles where gate_type = any(:gates) "
                        + "and cycle_number = 1 and decided_at is not null and decided_at::date between :from and :to",
                Map.of("gates", REVIEW_GATE_NAMES.toArray(new String[0]), "from", ctx.rangeStart, "to", ctx.rangeEnd));
        long firstPassApproved = scalarLong("select count(*) from review_cycles where gate_type = any(:gates) "
                        + "and cycle_number = 1 and decision = 'APPROVED' and decided_at::date between :from and :to",
                Map.of("gates", REVIEW_GATE_NAMES.toArray(new String[0]), "from", ctx.rangeStart, "to", ctx.rangeEnd));
        BigDecimal firstPassRate = rate(firstPassApproved, firstPassDecided);

        BigDecimal overallReworkRate = productionReworkRate(ctx.rangeStart, ctx.rangeEnd);

        Double avgTurnaround = scalarDouble(
                "select avg(extract(epoch from (decided_at - submitted_at)) / 86400.0) from review_cycles "
                        + "where decided_at is not null and decided_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));

        long pendingReviews = pendingReviewsCount();

        long ideaApproved = scalarLong("select count(*) from review_cycles where gate_type = 'IDEA_REVIEW' "
                        + "and decision = 'APPROVED' and decided_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long ideaRejected = scalarLong("select count(*) from review_cycles where gate_type = 'IDEA_REVIEW' "
                        + "and decision = 'REJECTED' and decided_at::date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        BigDecimal ideaRejectionRate = rate(ideaRejected, ideaApproved + ideaRejected);

        long evidenceCorrections = evidenceCorrectionCount(ctx.rangeStart, ctx.rangeEnd);

        List<ReviewStageRow> stageRows = reviewStageRows(ctx);

        return new QualityReviewsDashboardDto(firstPassRate, overallReworkRate, avgTurnaround, pendingReviews,
                ideaRejectionRate, evidenceCorrections, stageRows, stageRows);
    }

    /** spec §31: Planning/Shoot/Edit only (production gates with a real rework loop) - Idea Review
     * is reported separately (§32: APPROVED/REJECTED/RETAINED semantics differ from a rework gate).
     * First-Pass Approval % is governed as cycle_number=1 APPROVED / all cycle_number=1 decided
     * reviews - never all-cycles decided reviews (a stage with rework has more total decided cycles
     * than first-cycle ones, which would silently understate the rate if used as the denominator).
     * Rework % intentionally keeps the all-cycles denominator (rework can occur on any cycle, not
     * just the first) - matches the existing, already-governed KPI-024 shape. */
    private List<ReviewStageRow> reviewStageRows(DashboardContext ctx) {
        Map<String, GateType> gatesByLabel = new LinkedHashMap<>();
        gatesByLabel.put("Planning", GateType.PLANNING_REVIEW);
        gatesByLabel.put("Shoot", GateType.SHOOT_REVIEW);
        gatesByLabel.put("Edit", GateType.EDIT_REVIEW);
        List<ReviewStageRow> out = new ArrayList<>();
        for (Map.Entry<String, GateType> entry : gatesByLabel.entrySet()) {
            String gate = entry.getValue().name();
            long total = scalarLong("select count(*) from review_cycles where gate_type = :gate "
                            + "and decided_at is not null and decided_at::date between :from and :to",
                    Map.of("gate", gate, "from", ctx.rangeStart, "to", ctx.rangeEnd));
            long firstCycleDecided = scalarLong("select count(*) from review_cycles where gate_type = :gate "
                            + "and cycle_number = 1 and decided_at is not null and decided_at::date between :from and :to",
                    Map.of("gate", gate, "from", ctx.rangeStart, "to", ctx.rangeEnd));
            long firstPassApproved = scalarLong("select count(*) from review_cycles where gate_type = :gate "
                            + "and cycle_number = 1 and decision = 'APPROVED' and decided_at::date between :from and :to",
                    Map.of("gate", gate, "from", ctx.rangeStart, "to", ctx.rangeEnd));
            long rework = scalarLong("select count(*) from review_cycles where gate_type = :gate "
                            + "and decision = 'REQUEST_REWORK' and decided_at::date between :from and :to",
                    Map.of("gate", gate, "from", ctx.rangeStart, "to", ctx.rangeEnd));
            Double avgTime = scalarDouble("select avg(extract(epoch from (decided_at - submitted_at)) / 86400.0) "
                            + "from review_cycles where gate_type = :gate and decided_at is not null "
                            + "and decided_at::date between :from and :to",
                    Map.of("gate", gate, "from", ctx.rangeStart, "to", ctx.rangeEnd));
            out.add(new ReviewStageRow(entry.getKey(), total, firstCycleDecided, firstPassApproved, rework, avgTime,
                    rate(firstPassApproved, firstCycleDecided), rate(rework, total)));
        }
        return out;
    }

    // ================================================================================ PERFORMANCE (spec §33-37)

    private record ScorecardContext(CreativePerformanceScorecard scorecard, ActualPublicationEvent event,
                                     PlannedOutput output, BigDecimal effectiveCtr, BigDecimal effectiveImpressions) {
    }

    @Transactional(readOnly = true)
    public PerformanceDashboardDto performance(User requester, LocalDate startDate, LocalDate endDate) {
        requireViewAuthority(requester);
        DashboardContext ctx = buildContext(startDate, endDate);

        long performancePending = scalarLong("select count(*) from performance_obligations where is_completed = false",
                Map.of());
        long performanceOverdue = performanceOverdueCount(ctx.today);

        long obligationsDue = scalarLong("select count(*) from performance_obligations "
                + "where performance_due_date between :from and :to", Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        long obligationsSubmitted = scalarLong("select count(*) from performance_obligations po "
                        + "join creative_performance_scorecards s on s.obligation_id = po.obligation_id "
                        + "where s.submitted_at is not null and po.performance_due_date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));
        BigDecimal scorecardCompletion = rate(obligationsSubmitted, obligationsDue);

        Double avgDelayInReporting = scalarDouble(
                "select avg(extract(epoch from (s.submitted_at - po.performance_due_date::timestamp)) / 86400.0) "
                        + "from performance_obligations po join creative_performance_scorecards s "
                        + "on s.obligation_id = po.obligation_id "
                        + "where s.submitted_at is not null and po.performance_due_date between :from and :to",
                Map.of("from", ctx.rangeStart, "to", ctx.rangeEnd));

        List<ScorecardContext> scorecards = submittedScorecardsInRange(ctx);

        List<LabelValueRow> topContentType = topByCtr(scorecards, sc -> {
            String type = sc.output().getOutputType().name();
            return sc.output().getReelType() != null ? "REEL · " + sc.output().getReelType() : type;
        });
        List<LabelValueRow> topPlatform = topByCtr(scorecards, sc -> sc.event().getPublicationTarget().getPlatform().getPlatformName());
        List<LabelValueRow> topChannel = topByCtr(scorecards, sc -> sc.event().getPublicationTarget().getChannel().getChannelHandle());

        LabelValueRow originalCtr = avgByEventType(scorecards, PublicationEventType.ORIGINAL, ScorecardContext::effectiveCtr, "Original Avg CTR %");
        LabelValueRow repostCtr = avgByEventType(scorecards, PublicationEventType.REPOST, ScorecardContext::effectiveCtr, "Repost Avg CTR %");
        LabelValueRow originalImpressions = avgByEventType(scorecards, PublicationEventType.ORIGINAL, ScorecardContext::effectiveImpressions, "Original Avg Impressions");
        LabelValueRow repostImpressions = avgByEventType(scorecards, PublicationEventType.REPOST, ScorecardContext::effectiveImpressions, "Repost Avg Impressions");

        return new PerformanceDashboardDto(performancePending, performanceOverdue, scorecardCompletion, avgDelayInReporting,
                topContentType, topPlatform, topChannel, originalCtr, repostCtr, originalImpressions, repostImpressions);
    }

    /** Every submitted scorecard whose event's actual publication falls in range, with its
     * effective (post-correction) CTR/Impressions already resolved - batch-loaded (avoids N+1). */
    private List<ScorecardContext> submittedScorecardsInRange(DashboardContext ctx) {
        List<PerformanceObligation> obligations = obligationRepository.findAll().stream()
                .filter(o -> inRange(o.getEvent().getActualPublicationTimestamp(), ctx.rangeStart, ctx.rangeEnd))
                .toList();
        if (obligations.isEmpty()) {
            return List.of();
        }
        Set<UUID> obligationIds = obligations.stream().map(PerformanceObligation::getId).collect(Collectors.toSet());
        Map<UUID, CreativePerformanceScorecard> scorecardByObligationId = scorecardRepository
                .findByObligation_IdIn(obligationIds).stream()
                .filter(CreativePerformanceScorecard::isSubmitted)
                .collect(Collectors.toMap(s -> s.getObligation().getId(), s -> s));
        if (scorecardByObligationId.isEmpty()) {
            return List.of();
        }
        Set<UUID> scorecardIds = scorecardByObligationId.values().stream().map(CreativePerformanceScorecard::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<PerformanceMetricCorrection>> correctionsByScorecardId = metricCorrectionRepository
                .findByScorecard_IdInOrderByCorrectedAtDesc(scorecardIds).stream()
                .collect(Collectors.groupingBy(c -> c.getScorecard().getId()));

        Set<UUID> outputIds = obligations.stream().map(o -> o.getEvent().getPlannedOutput().getId()).collect(Collectors.toSet());
        Map<UUID, PlannedOutput> outputById = plannedOutputRepository.findAllById(outputIds).stream()
                .collect(Collectors.toMap(PlannedOutput::getId, o -> o));

        List<ScorecardContext> out = new ArrayList<>();
        for (PerformanceObligation obligation : obligations) {
            CreativePerformanceScorecard scorecard = scorecardByObligationId.get(obligation.getId());
            if (scorecard == null) {
                continue;
            }
            List<PerformanceMetricCorrection> corrections =
                    correctionsByScorecardId.getOrDefault(scorecard.getId(), List.of());
            Integer effClicks = effectiveInt(corrections, PerformanceMetricCorrection::getNewClicks, scorecard.getLinkClicks());
            boolean effClicksIsNa = effectiveBoolean(corrections, PerformanceMetricCorrection::getNewClicksIsNa, scorecard.isClicksIsNa());
            Integer effImpressions = effectiveInt(corrections, PerformanceMetricCorrection::getNewImpressions, scorecard.getImpressions());
            BigDecimal effectiveCtr = CreativePerformanceScorecard.computeRatePercent(
                    effClicksIsNa ? null : CreativePerformanceScorecard.toDecimal(effClicks),
                    CreativePerformanceScorecard.toDecimal(effImpressions));
            BigDecimal effectiveImpressions = CreativePerformanceScorecard.toDecimal(effImpressions);
            PlannedOutput output = outputById.get(obligation.getEvent().getPlannedOutput().getId());
            if (output == null) {
                continue;
            }
            out.add(new ScorecardContext(scorecard, obligation.getEvent(), output, effectiveCtr, effectiveImpressions));
        }
        return out;
    }

    /** "Latest correction wins" per metric - the same rule {@code PerformanceService
     * #resolveEffectiveMetrics} applies, reimplemented here only because that method isn't batch-
     * shaped (it re-queries corrections per scorecard); the underlying formula
     * ({@link CreativePerformanceScorecard#computeRatePercent}) is still the exact same call, never
     * duplicated logic. {@code correctionsDesc} must already be newest-first. */
    private static Integer effectiveInt(List<PerformanceMetricCorrection> correctionsDesc,
                                         java.util.function.Function<PerformanceMetricCorrection, Integer> extractor,
                                         Integer rawValue) {
        return correctionsDesc.stream().map(extractor).filter(java.util.Objects::nonNull).findFirst().orElse(rawValue);
    }

    private static Boolean effectiveBoolean(List<PerformanceMetricCorrection> correctionsDesc,
                                             java.util.function.Function<PerformanceMetricCorrection, Boolean> extractor,
                                             boolean rawValue) {
        return correctionsDesc.stream().map(extractor).filter(java.util.Objects::nonNull).findFirst().orElse(rawValue);
    }

    /** spec §36: ranking metric is explicitly Avg CTR %, N/A (null) scorecards excluded entirely -
     * never treated as 0. Top 5, descending. */
    private List<LabelValueRow> topByCtr(List<ScorecardContext> scorecards,
                                          java.util.function.Function<ScorecardContext, String> labelFn) {
        Map<String, List<BigDecimal>> byLabel = new LinkedHashMap<>();
        for (ScorecardContext sc : scorecards) {
            if (sc.effectiveCtr() == null) {
                continue;
            }
            byLabel.computeIfAbsent(labelFn.apply(sc), k -> new ArrayList<>()).add(sc.effectiveCtr());
        }
        return byLabel.entrySet().stream()
                .map(e -> new LabelValueRow(e.getKey(), average(e.getValue()), (long) e.getValue().size()))
                .sorted(Comparator.comparing(LabelValueRow::getValue, Comparator.reverseOrder()))
                .limit(5)
                .toList();
    }

    /** spec §37: ORIGINAL vs REPOST comparison, N/A excluded from the average, never a fabricated 0. */
    private LabelValueRow avgByEventType(List<ScorecardContext> scorecards, PublicationEventType eventType,
                                          java.util.function.Function<ScorecardContext, BigDecimal> valueFn, String label) {
        List<BigDecimal> values = scorecards.stream()
                .filter(sc -> sc.event().getEventType() == eventType)
                .map(valueFn).filter(java.util.Objects::nonNull).toList();
        return new LabelValueRow(label, values.isEmpty() ? null : average(values));
    }

    private static BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
