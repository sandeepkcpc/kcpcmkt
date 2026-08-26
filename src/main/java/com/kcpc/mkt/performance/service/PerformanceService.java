package com.kcpc.mkt.performance.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.performance.domain.CreativePerformanceScorecard;
import com.kcpc.mkt.performance.domain.PerformanceMetricCorrection;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.dto.EffectiveScorecardMetrics;
import com.kcpc.mkt.performance.dto.LegacyEffectiveScorecardMetrics;
import com.kcpc.mkt.performance.repository.CreativePerformanceScorecardRepository;
import com.kcpc.mkt.performance.repository.PerformanceMetricCorrectionRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.service.WorkflowTransitionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * BRS-REQ-048..051: event-level Performance obligations, Creative Performance Scorecard
 * draft/submit lifecycle, and deliverable Completion.
 */
@Service
public class PerformanceService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final PerformanceObligationRepository obligationRepository;
    private final CreativePerformanceScorecardRepository scorecardRepository;
    private final PerformanceMetricCorrectionRepository metricCorrectionRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final WorkflowTransitionService workflowService;
    private final AuthorizationService authorizationService;
    private final PerformanceEligibilityService performanceEligibilityService;
    private final AuditService auditService;

    public PerformanceService(PerformanceObligationRepository obligationRepository,
                               CreativePerformanceScorecardRepository scorecardRepository,
                               PerformanceMetricCorrectionRepository metricCorrectionRepository,
                               ContentPlanRepository contentPlanRepository, WorkflowTransitionService workflowService,
                               AuthorizationService authorizationService,
                               PerformanceEligibilityService performanceEligibilityService, AuditService auditService) {
        this.obligationRepository = obligationRepository;
        this.scorecardRepository = scorecardRepository;
        this.metricCorrectionRepository = metricCorrectionRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
        this.performanceEligibilityService = performanceEligibilityService;
        this.auditService = auditService;
    }

    private PerformanceObligation requireObligation(UUID obligationId) {
        return obligationRepository.findById(obligationId)
                .orElseThrow(() -> DomainException.notFound("Performance obligation not found: " + obligationId));
    }

    private CreativePerformanceScorecard scorecardFor(PerformanceObligation obligation, User recordedBy) {
        return scorecardRepository.findByObligation(obligation)
                .orElseGet(() -> scorecardRepository.save(new CreativePerformanceScorecard(obligation, recordedBy)));
    }

    /** SAD state-diagram: PP -&gt; PFUP fires on the first eligible scorecard draft/metric entry on-or-after the due date. */
    private void maybeAdvanceToPerformanceUpdate(WorkflowInstance workflowInstance, PerformanceObligation obligation,
                                                  User actor, Optional<PermissionGrant> actingGrant) {
        if (workflowInstance.getCurrentStatusCode() == WorkflowStatus.PP
                && !obligation.getPerformanceDueDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
            workflowService.transition(workflowInstance, WorkflowStatus.PFUP, actor, actingGrant,
                    "BEGIN_PERFORMANCE_UPDATE", null);
        }
    }

    /**
     * BFD status #19 ("Performance Due Date reached ... begins metric entry"): metric entry is
     * only eligible on or after the due date. Without this gate, an early submission would
     * leave the deliverable stranded at PP forever, since completion requires PFUP.
     */
    private void requireDueDateReached(PerformanceObligation obligation) {
        if (obligation.getPerformanceDueDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Performance metrics cannot be entered before the Performance Due Date ("
                            + obligation.getPerformanceDueDate() + ")");
        }
    }

    /** V26: direct-entry Meta model (Hook Rate / Hold Rate / Views / Average View Duration). Views
     * has no N/A flag (see {@code EffectiveScorecardMetrics}). An obligation only ever exists for
     * an eligible Instagram/Facebook event ({@code PublishingService} gates creation), so no
     * eligibility re-check is needed here. */
    @Transactional
    public CreativePerformanceScorecard saveDraft(User actor, UUID obligationId,
                                                    BigDecimal hookRatePercent, boolean hookRateIsNa,
                                                    BigDecimal holdRatePercent, boolean holdRateIsNa,
                                                    Long views,
                                                    BigDecimal averageViewDurationSeconds, boolean avgViewDurationIsNa) {
        PerformanceObligation obligation = requireObligation(obligationId);
        WorkflowInstance workflowInstance = obligation.getEvent().getContentPlan().getWorkflowInstance();
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_09_PERFORMANCE_UPDATE, LifecycleStage.PERFORMANCE, workflowInstance);
        requireDueDateReached(obligation);

        CreativePerformanceScorecard scorecard = scorecardFor(obligation, actor);
        scorecard.updateMetaDraft(hookRatePercent, hookRateIsNa, holdRatePercent, holdRateIsNa, views,
                averageViewDurationSeconds, avgViewDurationIsNa);
        scorecardRepository.save(scorecard);
        maybeAdvanceToPerformanceUpdate(workflowInstance, obligation, actor, actingGrant);
        auditService.record(actor, actingGrant, "PERFORMANCE", "SCORECARD_DRAFT_SAVED", "creative_performance_scorecards",
                scorecard.getId(), null);
        return scorecard;
    }

    @Transactional
    public CreativePerformanceScorecard submit(User actor, UUID obligationId) {
        PerformanceObligation obligation = requireObligation(obligationId);
        WorkflowInstance workflowInstance = obligation.getEvent().getContentPlan().getWorkflowInstance();
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_09_PERFORMANCE_UPDATE, LifecycleStage.PERFORMANCE, workflowInstance);
        requireDueDateReached(obligation);

        CreativePerformanceScorecard scorecard = scorecardRepository.findByObligation(obligation)
                .orElseThrow(() -> DomainException.notFound("No draft scorecard exists yet for this obligation"));
        scorecard.submit();
        scorecardRepository.save(scorecard);
        obligation.markCompleted();
        obligationRepository.save(obligation);
        maybeAdvanceToPerformanceUpdate(workflowInstance, obligation, actor, actingGrant);
        auditService.record(actor, actingGrant, "PERFORMANCE", "SCORECARD_SUBMITTED", "creative_performance_scorecards",
                scorecard.getId(), null);

        maybeComplete(obligation.getEvent().getContentPlan(), actor, actingGrant);
        return scorecard;
    }

    /**
     * API-OP-046 / SRS-REQ-051: records an auditable metric correction for an immutable submitted
     * scorecard under Permission #9 authority, appending a linked row to
     * performance_metric_corrections (ERD-TBL-028). The sealed scorecard row (ERD-CON-060) is
     * never mutated. A correction may touch any subset of metrics; unspecified fields are left
     * null in the correction row (no change recorded for that field).
     */
    /** V26: corrections against the four direct-entry Meta metrics. */
    @Transactional
    public PerformanceMetricCorrection correctMetrics(User actor, UUID scorecardId,
                                                        BigDecimal correctedHookRatePercent, Boolean correctedHookRateIsNa,
                                                        BigDecimal correctedHoldRatePercent, Boolean correctedHoldRateIsNa,
                                                        Long correctedViews,
                                                        BigDecimal correctedAverageViewDurationSeconds,
                                                        Boolean correctedAvgViewDurationIsNa, String correctionReason) {
        if (correctionReason == null || correctionReason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A correction reason is mandatory");
        }
        CreativePerformanceScorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> DomainException.notFound("Scorecard not found: " + scorecardId));
        if (!scorecard.isSubmitted()) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Only a submitted, sealed scorecard can receive a linked correction (ERD-CON-060); "
                            + "use the draft endpoint before submission");
        }
        WorkflowInstance workflowInstance = scorecard.getObligation().getEvent().getContentPlan().getWorkflowInstance();
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_09_PERFORMANCE_UPDATE, LifecycleStage.PERFORMANCE, workflowInstance);

        List<PerformanceMetricCorrection> priorCorrections =
                metricCorrectionRepository.findByScorecardOrderByCorrectedAtDesc(scorecard);
        PerformanceMetricCorrection latest = priorCorrections.isEmpty() ? null : priorCorrections.get(0);

        PerformanceMetricCorrection correction = new PerformanceMetricCorrection(scorecard, latest, correctionReason,
                actor, actingGrant.orElse(null));

        if (correctedHookRatePercent != null) {
            correction.setMetaHookRate(effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewMetaHookRate,
                    scorecard.getMetaHookRatePercent()), correctedHookRatePercent);
        }
        if (correctedHookRateIsNa != null) {
            correction.setMetaHookRateIsNa(effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewMetaHookRateIsNa,
                    scorecard.isMetaHookRateIsNa()), correctedHookRateIsNa);
        }
        if (correctedHoldRatePercent != null) {
            correction.setMetaHoldRate(effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewMetaHoldRate,
                    scorecard.getMetaHoldRatePercent()), correctedHoldRatePercent);
        }
        if (correctedHoldRateIsNa != null) {
            correction.setMetaHoldRateIsNa(effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewMetaHoldRateIsNa,
                    scorecard.isMetaHoldRateIsNa()), correctedHoldRateIsNa);
        }
        if (correctedViews != null) {
            correction.setMetaViews(effectiveLong(priorCorrections, PerformanceMetricCorrection::getNewMetaViews,
                    scorecard.getMetaViews()), correctedViews);
        }
        if (correctedAverageViewDurationSeconds != null) {
            correction.setMetaAvgViewDuration(effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewMetaAvgViewDuration,
                    scorecard.getMetaAverageViewDurationSeconds()), correctedAverageViewDurationSeconds);
        }
        if (correctedAvgViewDurationIsNa != null) {
            correction.setMetaAvgViewDurationIsNa(effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewMetaAvgViewDurationIsNa,
                    scorecard.isMetaAvgViewDurationIsNa()), correctedAvgViewDurationIsNa);
        }

        correction = metricCorrectionRepository.save(correction);
        auditService.record(actor, actingGrant, "PERFORMANCE", "PERFORMANCE_METRIC_CORRECTED",
                "performance_metric_corrections", correction.getId(), correctionReason);
        return correction;
    }

    /** Per-scorecard Correction History (newest first) - never the global Reports -&gt; Logs audit trail. */
    public List<PerformanceMetricCorrection> correctionsFor(CreativePerformanceScorecard scorecard) {
        return metricCorrectionRepository.findByScorecardOrderByCorrectedAtDesc(scorecard);
    }

    /**
     * Read-side projection reused by both the Correct-a-Metric UI ("Current Value" per metric)
     * and the Performance summary (Hook/Hold/CTR must reflect the latest correction, ERD-CON-060 +
     * SC-REQ-001) - the exact same per-metric "latest correction wins" helpers {@link #correctMetrics}
     * uses to compute a new correction's "prior" value, so the two can never disagree.
     */
    public EffectiveScorecardMetrics resolveEffectiveMetrics(CreativePerformanceScorecard scorecard) {
        List<PerformanceMetricCorrection> priorCorrections =
                metricCorrectionRepository.findByScorecardOrderByCorrectedAtDesc(scorecard);

        BigDecimal hookRate = effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewMetaHookRate,
                scorecard.getMetaHookRatePercent());
        boolean hookRateIsNa = effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewMetaHookRateIsNa,
                scorecard.isMetaHookRateIsNa());
        BigDecimal holdRate = effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewMetaHoldRate,
                scorecard.getMetaHoldRatePercent());
        boolean holdRateIsNa = effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewMetaHoldRateIsNa,
                scorecard.isMetaHoldRateIsNa());
        Long views = effectiveLong(priorCorrections, PerformanceMetricCorrection::getNewMetaViews, scorecard.getMetaViews());
        BigDecimal avgViewDuration = effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewMetaAvgViewDuration,
                scorecard.getMetaAverageViewDurationSeconds());
        boolean avgViewDurationIsNa = effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewMetaAvgViewDurationIsNa,
                scorecard.isMetaAvgViewDurationIsNa());

        return new EffectiveScorecardMetrics(hookRate, hookRateIsNa, holdRate, holdRateIsNa, views,
                avgViewDuration, avgViewDurationIsNa);
    }

    /** For a PRE-V26 scorecard ({@code !scorecard.isUsesMetaMetricModel()}) only - the original
     * 6-field/derived-Hook-Hold-CTR effective-value resolution, unchanged, so historical corrected
     * values keep displaying correctly. Never called for a new (Meta-model) scorecard - use
     * {@link #resolveEffectiveMetrics} for those. */
    public LegacyEffectiveScorecardMetrics resolveLegacyEffectiveMetrics(CreativePerformanceScorecard scorecard) {
        List<PerformanceMetricCorrection> priorCorrections =
                metricCorrectionRepository.findByScorecardOrderByCorrectedAtDesc(scorecard);

        Integer views3sec = effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewViews3sec, scorecard.getViews3sec());
        boolean views3secIsNa = effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewViews3secIsNa,
                scorecard.isViews3secIsNa());
        Integer plays = effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewPlays, scorecard.getPlays());
        BigDecimal watchTime = effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewWatchTime,
                scorecard.getAverageWatchTimeSeconds());
        boolean watchTimeIsNa = effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewWatchTimeIsNa,
                scorecard.isWatchTimeIsNa());
        BigDecimal videoLength = effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewVideoLength,
                scorecard.getVideoLengthSeconds());
        boolean videoLengthIsNa = effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewVideoLengthIsNa,
                scorecard.isVideoLengthIsNa());
        Integer linkClicks = effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewClicks, scorecard.getLinkClicks());
        boolean clicksIsNa = effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewClicksIsNa,
                scorecard.isClicksIsNa());
        Integer impressions = effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewImpressions,
                scorecard.getImpressions());

        BigDecimal hookRate = CreativePerformanceScorecard.computeRatePercent(
                views3secIsNa ? null : CreativePerformanceScorecard.toDecimal(views3sec),
                CreativePerformanceScorecard.toDecimal(plays));
        BigDecimal holdRate = CreativePerformanceScorecard.computeRatePercent(
                watchTimeIsNa ? null : watchTime, videoLengthIsNa ? null : videoLength);
        BigDecimal ctr = CreativePerformanceScorecard.computeRatePercent(
                clicksIsNa ? null : CreativePerformanceScorecard.toDecimal(linkClicks),
                CreativePerformanceScorecard.toDecimal(impressions));

        return new LegacyEffectiveScorecardMetrics(views3sec, views3secIsNa, plays, watchTime, watchTimeIsNa,
                videoLength, videoLengthIsNa, linkClicks, clicksIsNa, impressions, hookRate, holdRate, ctr);
    }

    private static Integer effectiveInt(List<PerformanceMetricCorrection> priorCorrectionsDesc,
                                         Function<PerformanceMetricCorrection, Integer> extractor,
                                         Integer rawScorecardValue) {
        return priorCorrectionsDesc.stream().map(extractor).filter(Objects::nonNull).findFirst()
                .orElse(rawScorecardValue);
    }

    private static Long effectiveLong(List<PerformanceMetricCorrection> priorCorrectionsDesc,
                                       Function<PerformanceMetricCorrection, Long> extractor,
                                       Long rawScorecardValue) {
        return priorCorrectionsDesc.stream().map(extractor).filter(Objects::nonNull).findFirst()
                .orElse(rawScorecardValue);
    }

    private static BigDecimal effectiveDecimal(List<PerformanceMetricCorrection> priorCorrectionsDesc,
                                                Function<PerformanceMetricCorrection, BigDecimal> extractor,
                                                BigDecimal rawScorecardValue) {
        return priorCorrectionsDesc.stream().map(extractor).filter(Objects::nonNull).findFirst()
                .orElse(rawScorecardValue);
    }

    private static Boolean effectiveBoolean(List<PerformanceMetricCorrection> priorCorrectionsDesc,
                                             Function<PerformanceMetricCorrection, Boolean> extractor,
                                             boolean rawScorecardValue) {
        return priorCorrectionsDesc.stream().map(extractor).filter(Objects::nonNull).findFirst()
                .orElse(rawScorecardValue);
    }

    /** BFD status #20: Completed once every ELIGIBLE (Instagram/Facebook) obligation for the
     * deliverable is submitted/completed. Every obligation created after V26 is already
     * Meta-eligible by construction ({@code PublishingService} gates creation), but a Content Plan
     * published before that change can still carry older, non-Meta obligation rows (never deleted -
     * see docs/IMPLEMENTATION_DECISIONS.md) - filtering here too, not just at creation, is what
     * actually satisfies "a Content ID must not remain Performance Pending because YouTube/LinkedIn/
     * etc. do not have performance records" for THAT pre-existing data as well, not only for
     * content published going forward. */
    private void maybeComplete(ContentPlan plan, User actor, Optional<PermissionGrant> actingGrant) {
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.PFUP) {
            return;
        }
        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(plan.getId()).stream()
                .filter(o -> performanceEligibilityService.isEligible(o.getEvent()))
                .toList();
        boolean allCompleted = !obligations.isEmpty() && obligations.stream().allMatch(PerformanceObligation::isCompleted);
        if (allCompleted) {
            workflowService.transition(workflowInstance, WorkflowStatus.COMP, actor, actingGrant, "COMPLETE_DELIVERABLE", null);
            // ERD-CON-005: first_completed_at is a one-time "was this deliverable EVER completed"
            // marker, immutable once set - never re-stamped on a later completion (e.g. the repost
            // cycle's own COMP after Reopen for Publishing). Latent bug surfaced by the reopen/
            // multi-cycle repost fix: this is the first path that legitimately re-reaches COMP.
            if (!workflowInstance.everCompleted()) {
                workflowInstance.markFirstCompleted(Instant.now());
            }
            auditService.record(actor, actingGrant, "PERFORMANCE", "DELIVERABLE_COMPLETED", "content_plans",
                    plan.getId(), null);
        }
    }
}
