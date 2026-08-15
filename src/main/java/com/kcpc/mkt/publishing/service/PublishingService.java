package com.kcpc.mkt.publishing.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
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
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.service.WorkflowTransitionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** BRS-REQ-042..051: Publishing execution, Actual Publication events, Target N/A governance. */
@Service
public class PublishingService {

    private final ContentPlanRepository contentPlanRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final PublicationTargetRepository publicationTargetRepository;
    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final ActualPublicationEventRepository eventRepository;
    private final PublicationTargetNaRecordRepository naRecordRepository;
    private final PerformanceObligationRepository obligationRepository;
    private final PublicationEvidenceCorrectionRepository evidenceCorrectionRepository;
    private final WorkflowTransitionService workflowService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public PublishingService(ContentPlanRepository contentPlanRepository, PlannedOutputRepository plannedOutputRepository,
                              PublicationTargetRepository publicationTargetRepository,
                              PlannedOutputPublicationTargetMappingRepository mappingRepository,
                              ActualPublicationEventRepository eventRepository,
                              PublicationTargetNaRecordRepository naRecordRepository,
                              PerformanceObligationRepository obligationRepository,
                              PublicationEvidenceCorrectionRepository evidenceCorrectionRepository,
                              WorkflowTransitionService workflowService, AuthorizationService authorizationService,
                              AuditService auditService) {
        this.contentPlanRepository = contentPlanRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.publicationTargetRepository = publicationTargetRepository;
        this.mappingRepository = mappingRepository;
        this.eventRepository = eventRepository;
        this.naRecordRepository = naRecordRepository;
        this.obligationRepository = obligationRepository;
        this.evidenceCorrectionRepository = evidenceCorrectionRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private ContentPlan requirePlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    private Optional<PermissionGrant> requirePublishingAuthority(User user, WorkflowInstance workflowInstance) {
        return authorizationService.requireAuthority(user, OperationalPermission.PERM_08_PUBLISHING_EXECUTION,
                LifecycleStage.PUBLISHING, workflowInstance);
    }

    @Transactional
    public void startPublishing(User actor, UUID contentPlanId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.RFP) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Publishing can only start once Ready for Publishing");
        }
        Optional<PermissionGrant> actingGrant = requirePublishingAuthority(actor, workflowInstance);
        workflowService.transition(workflowInstance, WorkflowStatus.PUBG, actor, actingGrant, "START_PUBLISHING", null);
        auditService.record(actor, actingGrant, "PUBLISHING", "PUBLISHING_STARTED", "content_plans", plan.getId(), null);
    }

    @Transactional
    public ActualPublicationEvent recordActualPublication(User actor, UUID contentPlanId, UUID plannedOutputId,
                                                            UUID publicationTargetId, PublicationEventType eventType,
                                                            Instant actualPublicationTimestamp, String evidenceUrl) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.PUBG) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Actual Publication can only be recorded while Publishing is underway");
        }
        Optional<PermissionGrant> actingGrant = requirePublishingAuthority(actor, workflowInstance);
        PlannedOutput plannedOutput = plannedOutputRepository.findById(plannedOutputId)
                .orElseThrow(() -> DomainException.notFound("Planned Output not found: " + plannedOutputId));
        PublicationTarget target = publicationTargetRepository.findById(publicationTargetId)
                .orElseThrow(() -> DomainException.notFound("Publication Target not found: " + publicationTargetId));
        if (evidenceUrl == null || evidenceUrl.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Evidence URL is required");
        }

        ActualPublicationEvent event = eventRepository.save(new ActualPublicationEvent(plan, plannedOutput, target,
                eventType, actualPublicationTimestamp, evidenceUrl, actor));
        // build-prompt §25: every Actual Publication Event creates its own performance obligation.
        obligationRepository.save(new PerformanceObligation(event));

        auditService.record(actor, actingGrant, "PUBLISHING", "ACTUAL_PUBLICATION_RECORDED",
                "actual_publication_events", event.getId(), null);

        if (isScopeResolved(plan)) {
            workflowService.transition(workflowInstance, WorkflowStatus.PP, actor, actingGrant,
                    "PUBLICATION_SCOPE_RESOLVED", null);
        }
        return event;
    }

    @Transactional
    public PublicationTargetNaRecord designateTargetNA(User actor, UUID plannedOutputId, UUID publicationTargetId,
                                                         String reason) {
        PlannedOutput plannedOutput = plannedOutputRepository.findById(plannedOutputId)
                .orElseThrow(() -> DomainException.notFound("Planned Output not found: " + plannedOutputId));
        ContentPlan plan = plannedOutput.getContentPlan();
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        Optional<PermissionGrant> actingGrant = requirePublishingAuthority(actor, workflowInstance);
        PublicationTarget target = publicationTargetRepository.findById(publicationTargetId)
                .orElseThrow(() -> DomainException.notFound("Publication Target not found: " + publicationTargetId));

        if (wouldLeaveAllTargetsNa(plan, plannedOutput, target)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Cannot designate every publication target N/A - at least one target must remain live or eligible");
        }

        PublicationTargetNaRecord record = naRecordRepository.save(new PublicationTargetNaRecord(plannedOutput, target,
                NaActionType.DESIGNATED, null, reason, actor));
        auditService.record(actor, actingGrant, "PUBLISHING", "TARGET_NA_DESIGNATED", "publication_target_na_records",
                record.getId(), reason);

        if (workflowInstance.getCurrentStatusCode() == WorkflowStatus.PUBG && isScopeResolved(plan)) {
            workflowService.transition(workflowInstance, WorkflowStatus.PP, actor, actingGrant,
                    "PUBLICATION_SCOPE_RESOLVED", null);
        }
        return record;
    }

    @Transactional
    public PublicationTargetNaRecord reverseTargetNA(User actor, UUID naRecordId, String reason) {
        PublicationTargetNaRecord priorRecord = naRecordRepository.findById(naRecordId)
                .orElseThrow(() -> DomainException.notFound("N/A record not found: " + naRecordId));
        ContentPlan plan = priorRecord.getPlannedOutput().getContentPlan();
        Optional<PermissionGrant> actingGrant = requirePublishingAuthority(actor, plan.getWorkflowInstance());
        PublicationTargetNaRecord reversal = naRecordRepository.save(new PublicationTargetNaRecord(
                priorRecord.getPlannedOutput(), priorRecord.getPublicationTarget(), NaActionType.REVERSED,
                priorRecord, reason, actor));
        auditService.record(actor, actingGrant, "PUBLISHING", "TARGET_NA_REVERSED", "publication_target_na_records",
                reversal.getId(), reason);
        return reversal;
    }

    private Optional<NaActionType> latestNaAction(PlannedOutput output, PublicationTarget target) {
        return naRecordRepository.findByPlannedOutput(output).stream()
                .filter(r -> r.getPublicationTarget().getId().equals(target.getId()))
                .max(Comparator.comparing(PublicationTargetNaRecord::getRecordedAt))
                .map(PublicationTargetNaRecord::getActionType);
    }

    private boolean hasLivePost(PlannedOutput output, PublicationTarget target) {
        return eventRepository.findByPlannedOutputAndEventType(output, PublicationEventType.ORIGINAL).stream()
                .anyMatch(e -> e.getPublicationTarget().getId().equals(target.getId()));
    }

    /** BFD status #18: scope resolved (every mapped pair live-or-N/A) AND at least one live post exists. */
    private boolean isScopeResolved(ContentPlan plan) {
        List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan(plan);
        boolean anyLive = false;
        boolean anyPair = false;
        for (PlannedOutput output : outputs) {
            for (var mapping : mappingRepository.findByPlannedOutput(output)) {
                anyPair = true;
                PublicationTarget target = mapping.getPublicationTarget();
                if (hasLivePost(output, target)) {
                    anyLive = true;
                    continue;
                }
                boolean isNa = latestNaAction(output, target).filter(a -> a == NaActionType.DESIGNATED).isPresent();
                if (!isNa) {
                    return false;
                }
            }
        }
        return anyPair && anyLive;
    }

    /**
     * API-OP-041 / SRS-REQ-046: corrects an erroneous publication evidence link under Permission #8
     * authority, appending an immutable linked row to publication_evidence_corrections
     * (ERD-TBL-027). The original actual_publication_events row is never mutated.
     */
    @Transactional
    public PublicationEvidenceCorrection correctEvidenceUrl(User actor, UUID eventId, String correctedEvidenceUrl,
                                                              String correctionReason) {
        if (correctionReason == null || correctionReason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A correction reason is mandatory");
        }
        if (correctedEvidenceUrl == null || correctedEvidenceUrl.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A corrected evidence URL is mandatory");
        }
        ActualPublicationEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> DomainException.notFound("Publication event not found: " + eventId));
        Optional<PermissionGrant> actingGrant = requirePublishingAuthority(actor, event.getContentPlan().getWorkflowInstance());

        PublicationEvidenceCorrection latest = evidenceCorrectionRepository.findByEventOrderByCorrectedAtDesc(event)
                .stream().findFirst().orElse(null);
        String priorEvidenceUrl = latest != null ? latest.getCorrectedEvidenceUrl() : event.getEvidenceUrl();

        PublicationEvidenceCorrection correction = evidenceCorrectionRepository.save(new PublicationEvidenceCorrection(
                event, latest, priorEvidenceUrl, correctedEvidenceUrl, correctionReason, actor, actingGrant.orElse(null)));
        auditService.record(actor, actingGrant, "PUBLISHING", "PUBLICATION_EVIDENCE_CORRECTED",
                "publication_evidence_corrections", correction.getId(), correctionReason);
        return correction;
    }

    /** ERD-CON-017: completion/scope-resolution blocked if all planned targets end up N/A with zero live posts. */
    private boolean wouldLeaveAllTargetsNa(ContentPlan plan, PlannedOutput candidateOutput, PublicationTarget candidateTarget) {
        List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan(plan);
        boolean anyLive = false;
        boolean anyNonNa = false;
        for (PlannedOutput output : outputs) {
            for (var mapping : mappingRepository.findByPlannedOutput(output)) {
                PublicationTarget target = mapping.getPublicationTarget();
                boolean isCandidate = output.getId().equals(candidateOutput.getId()) && target.getId().equals(candidateTarget.getId());
                if (hasLivePost(output, target)) {
                    anyLive = true;
                    continue;
                }
                boolean isNa = isCandidate || latestNaAction(output, target).filter(a -> a == NaActionType.DESIGNATED).isPresent();
                if (!isNa) {
                    anyNonNa = true;
                }
            }
        }
        return !anyLive && !anyNonNa;
    }
}
