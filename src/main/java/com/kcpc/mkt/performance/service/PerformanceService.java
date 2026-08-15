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
    private final AuditService auditService;

    public PerformanceService(PerformanceObligationRepository obligationRepository,
                               CreativePerformanceScorecardRepository scorecardRepository,
                               PerformanceMetricCorrectionRepository metricCorrectionRepository,
                               ContentPlanRepository contentPlanRepository, WorkflowTransitionService workflowService,
                               AuthorizationService authorizationService, AuditService auditService) {
        this.obligationRepository = obligationRepository;
        this.scorecardRepository = scorecardRepository;
        this.metricCorrectionRepository = metricCorrectionRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
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

    @Transactional
    public CreativePerformanceScorecard saveDraft(User actor, UUID obligationId, Integer views3sec, boolean views3secIsNa,
                                                    Integer plays, BigDecimal averageWatchTimeSeconds, boolean watchTimeIsNa,
                                                    BigDecimal videoLengthSeconds, boolean videoLengthIsNa,
                                                    Integer linkClicks, boolean clicksIsNa, Integer impressions) {
        PerformanceObligation obligation = requireObligation(obligationId);
        WorkflowInstance workflowInstance = obligation.getEvent().getContentPlan().getWorkflowInstance();
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_09_PERFORMANCE_UPDATE, LifecycleStage.PERFORMANCE, workflowInstance);
        requireDueDateReached(obligation);

        CreativePerformanceScorecard scorecard = scorecardFor(obligation, actor);
        scorecard.updateDraft(views3sec, views3secIsNa, plays, averageWatchTimeSeconds, watchTimeIsNa,
                videoLengthSeconds, videoLengthIsNa, linkClicks, clicksIsNa, impressions);
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
    @Transactional
    public PerformanceMetricCorrection correctMetrics(User actor, UUID scorecardId, Integer correctedViews3sec,
                                                        Boolean correctedViews3secIsNa, Integer correctedPlays,
                                                        BigDecimal correctedWatchTimeSeconds, Boolean correctedWatchTimeIsNa,
                                                        BigDecimal correctedVideoLengthSeconds, Boolean correctedVideoLengthIsNa,
                                                        Integer correctedLinkClicks, Boolean correctedClicksIsNa,
                                                        Integer correctedImpressions, String correctionReason) {
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

        if (correctedViews3sec != null) {
            correction.setViews3sec(effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewViews3sec,
                    scorecard.getViews3sec()), correctedViews3sec);
        }
        if (correctedPlays != null) {
            correction.setPlays(effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewPlays,
                    scorecard.getPlays()), correctedPlays);
        }
        if (correctedWatchTimeSeconds != null) {
            correction.setWatchTime(effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewWatchTime,
                    scorecard.getAverageWatchTimeSeconds()), correctedWatchTimeSeconds);
        }
        if (correctedVideoLengthSeconds != null) {
            correction.setVideoLength(effectiveDecimal(priorCorrections, PerformanceMetricCorrection::getNewVideoLength,
                    scorecard.getVideoLengthSeconds()), correctedVideoLengthSeconds);
        }
        if (correctedLinkClicks != null) {
            correction.setClicks(effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewClicks,
                    scorecard.getLinkClicks()), correctedLinkClicks);
        }
        if (correctedImpressions != null) {
            correction.setImpressions(effectiveInt(priorCorrections, PerformanceMetricCorrection::getNewImpressions,
                    scorecard.getImpressions()), correctedImpressions);
        }
        if (correctedViews3secIsNa != null) {
            correction.setViews3secIsNa(effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewViews3secIsNa,
                    scorecard.isViews3secIsNa()), correctedViews3secIsNa);
        }
        if (correctedWatchTimeIsNa != null) {
            correction.setWatchTimeIsNa(effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewWatchTimeIsNa,
                    scorecard.isWatchTimeIsNa()), correctedWatchTimeIsNa);
        }
        if (correctedVideoLengthIsNa != null) {
            correction.setVideoLengthIsNa(effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewVideoLengthIsNa,
                    scorecard.isVideoLengthIsNa()), correctedVideoLengthIsNa);
        }
        if (correctedClicksIsNa != null) {
            correction.setClicksIsNa(effectiveBoolean(priorCorrections, PerformanceMetricCorrection::getNewClicksIsNa,
                    scorecard.isClicksIsNa()), correctedClicksIsNa);
        }

        correction = metricCorrectionRepository.save(correction);
        auditService.record(actor, actingGrant, "PERFORMANCE", "PERFORMANCE_METRIC_CORRECTED",
                "performance_metric_corrections", correction.getId(), correctionReason);
        return correction;
    }

    private static Integer effectiveInt(List<PerformanceMetricCorrection> priorCorrectionsDesc,
                                         Function<PerformanceMetricCorrection, Integer> extractor,
                                         Integer rawScorecardValue) {
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

    /** BFD status #20: Completed once every obligation for the deliverable is submitted/completed. */
    private void maybeComplete(ContentPlan plan, User actor, Optional<PermissionGrant> actingGrant) {
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.PFUP) {
            return;
        }
        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(plan.getId());
        boolean allCompleted = !obligations.isEmpty() && obligations.stream().allMatch(PerformanceObligation::isCompleted);
        if (allCompleted) {
            workflowService.transition(workflowInstance, WorkflowStatus.COMP, actor, actingGrant, "COMPLETE_DELIVERABLE", null);
            workflowInstance.markFirstCompleted(Instant.now());
            auditService.record(actor, actingGrant, "PERFORMANCE", "DELIVERABLE_COMPLETED", "content_plans",
                    plan.getId(), null);
        }
    }
}
