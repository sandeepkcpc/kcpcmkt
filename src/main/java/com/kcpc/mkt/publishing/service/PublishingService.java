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
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.publishing.repository.PublicationEvidenceCorrectionRepository;
import com.kcpc.mkt.publishing.repository.PublicationTargetNaRecordRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.service.HoldService;
import com.kcpc.mkt.workflow.service.WorkflowTransitionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final WorkflowTransitionService workflowService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final HoldService holdService;

    public PublishingService(ContentPlanRepository contentPlanRepository, PlannedOutputRepository plannedOutputRepository,
                              PublicationTargetRepository publicationTargetRepository,
                              PlannedOutputPublicationTargetMappingRepository mappingRepository,
                              ActualPublicationEventRepository eventRepository,
                              PublicationTargetNaRecordRepository naRecordRepository,
                              PerformanceObligationRepository obligationRepository,
                              PublicationEvidenceCorrectionRepository evidenceCorrectionRepository,
                              PublishingAssignmentRepository publishingAssignmentRepository,
                              WorkflowTransitionService workflowService, AuthorizationService authorizationService,
                              AuditService auditService, HoldService holdService) {
        this.contentPlanRepository = contentPlanRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.publicationTargetRepository = publicationTargetRepository;
        this.mappingRepository = mappingRepository;
        this.eventRepository = eventRepository;
        this.naRecordRepository = naRecordRepository;
        this.obligationRepository = obligationRepository;
        this.evidenceCorrectionRepository = evidenceCorrectionRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.holdService = holdService;
    }

    private ContentPlan requirePlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    private Optional<PermissionGrant> requirePublishingAuthority(User user, WorkflowInstance workflowInstance) {
        return authorizationService.requireAuthority(user, OperationalPermission.PERM_08_PUBLISHING_EXECUTION,
                LifecycleStage.PUBLISHING, workflowInstance);
    }

    /**
     * ENG-043: Publishing's hands-on execution acts (Start Publishing, recording an Actual
     * Publication) require the actor to be an actively assigned Publisher on this Content Plan -
     * PERM_08 alone (including CEO/MM's native-authority bypass of it) is not enough. CEO/MM's
     * native authority covers management actions (Publisher assignment, Target N/A, evidence
     * correction - all still gated on PERM_08 only, unaffected below) not hands-on execution of an
     * Employee's own task. Mirrors ShootingService/EditingService#requireActiveAssignee.
     */
    private void requireActiveAssignee(User actor, ContentPlan plan) {
        boolean isAssignee = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getPublisher().getId().equals(actor.getId()));
        if (!isAssignee) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    "Only an assigned Publisher can perform this action");
        }
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
        requireActiveAssignee(actor, plan);
        workflowService.transition(workflowInstance, WorkflowStatus.PUBG, actor, actingGrant, "START_PUBLISHING", null);
        auditService.record(actor, actingGrant, "PUBLISHING", "PUBLISHING_STARTED", "content_plans", plan.getId(), null);
    }

    /**
     * Publisher(s) assignment (not in the frozen ERD - see docs/IMPLEMENTATION_DECISIONS.md ENG-035).
     * ENG-044: restricted to native CEO/MM authority only, NOT `PERM_08_PUBLISHING_EXECUTION` grant
     * holders - a Publisher's PERM_08 grant exists so they can execute their own assigned task (see
     * {@link #startPublishing}/{@link #recordActualPublication}), never to assign/reassign/remove
     * other Publishers on a plan. An active assignment here is a precondition for those execution
     * methods (see {@link #requireActiveAssignee}). Idempotent: re-assigning a Publisher who already
     * holds an active assignment returns the existing row.
     */
    @Transactional
    public PublishingAssignment assignPublisher(User actor, UUID contentPlanId, User publisher) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.RFP) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Publisher assignment is only valid while Ready for Publishing");
        }
        authorizationService.requireNativeAuthority(actor, "Publisher assignment");
        Optional<PublishingAssignment> existing =
                publishingAssignmentRepository.findByContentPlanAndPublisherAndActiveTrue(plan, publisher);
        if (existing.isPresent()) {
            return existing.get();
        }
        PublishingAssignment assignment = publishingAssignmentRepository.save(
                new PublishingAssignment(plan, publisher, actor));
        auditService.record(actor, Optional.empty(), "PUBLISHING", "PUBLISHER_ASSIGNED", "publishing_assignments",
                assignment.getId(), null);
        return assignment;
    }

    /** Removes an active Publisher assignment, mirroring the same RFP-only window and ENG-044 native-only gate as assign. */
    @Transactional
    public void removePublisher(User actor, UUID contentPlanId, UUID publisherUserId) {
        ContentPlan plan = requirePlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.RFP) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Publisher assignment can only be removed while Ready for Publishing");
        }
        authorizationService.requireNativeAuthority(actor, "Publisher assignment");
        PublishingAssignment assignment = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().equals(publisherUserId))
                .findFirst()
                .orElseThrow(() -> DomainException.notFound("No active publishing assignment for this Publisher"));
        assignment.end();
        publishingAssignmentRepository.save(assignment);
        auditService.record(actor, Optional.empty(), "PUBLISHING", "PUBLISHER_UNASSIGNED", "publishing_assignments",
                assignment.getId(), null);
    }

    /**
     * ENG-046: one Publishing Description shared by the whole Publisher team on this plan (not per
     * individual assignee), editable any time. Gated to native CEO/MM authority only - matching
     * ENG-044's Publisher-assignment rule, not PERM_08 - since this is a management action
     * (instructing whoever is assigned), not hands-on execution of an Employee's own task.
     */
    @Transactional
    public ContentPlan updatePublishingDescription(User actor, UUID contentPlanId, String description) {
        ContentPlan plan = requirePlan(contentPlanId);
        authorizationService.requireNativeAuthority(actor, "Publishing Description");
        plan.setPublishingDescription(description);
        contentPlanRepository.save(plan);
        auditService.record(actor, Optional.empty(), "PUBLISHING", "PUBLISHING_DESCRIPTION_UPDATED", "content_plans",
                plan.getId(), null);
        return plan;
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
        holdService.requireNoOpenHold(workflowInstance);
        Optional<PermissionGrant> actingGrant = requirePublishingAuthority(actor, workflowInstance);
        requireActiveAssignee(actor, plan);
        PlannedOutput plannedOutput = plannedOutputRepository.findById(plannedOutputId)
                .orElseThrow(() -> DomainException.notFound("Planned Output not found: " + plannedOutputId));
        PublicationTarget target = publicationTargetRepository.findById(publicationTargetId)
                .orElseThrow(() -> DomainException.notFound("Publication Target not found: " + publicationTargetId));
        if (evidenceUrl == null || evidenceUrl.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Evidence URL is required");
        }
        // ENG-055: an Original publish for a (output, target) pair is a one-time task, not
        // resubmittable - the Publishing checklist enforces this by never showing a checkbox for an
        // already-completed row, but that's UI-only; this is the actual source of truth. A genuine
        // re-publish after the first goes through eventType=REPOST instead, which is unaffected.
        if (eventType == PublicationEventType.ORIGINAL && hasLivePost(plannedOutput, target)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "An Original publication event already exists for " + plannedOutput.getOutputType()
                            + " / " + target.getPlatform().getPlatformName() + " - use Repost instead");
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

    /** ENG-068: "Targets" column/KPI on the Publisher's own screens - resolved (live post or N/A) vs. total mapped (Planned Output, Publication Target) pairs. */
    public record TargetResolutionSummary(int resolvedCount, int totalCount) {
    }

    public TargetResolutionSummary summarizeTargets(ContentPlan plan) {
        List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan(plan);
        int total = 0;
        int resolved = 0;
        for (PlannedOutput output : outputs) {
            for (var mapping : mappingRepository.findByPlannedOutput(output)) {
                total++;
                PublicationTarget target = mapping.getPublicationTarget();
                boolean isNa = latestNaAction(output, target).filter(a -> a == NaActionType.DESIGNATED).isPresent();
                if (hasLivePost(output, target) || isNa) {
                    resolved++;
                }
            }
        }
        return new TargetResolutionSummary(resolved, total);
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

    /**
     * Effective (current) evidence URL per event = its latest correction's corrected URL if one
     * exists, else the event's own immutable original evidenceUrl - the single shared resolver
     * every Publishing view that shows "what's the current published link" must use (Content
     * Detail's Actual Publication Events table, the Pipeline platform popover), never a second,
     * independently-maintained computation that could silently diverge from this one.
     */
    public Map<UUID, String> resolveEffectiveEvidenceUrls(List<ActualPublicationEvent> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        Set<UUID> eventIds = events.stream().map(ActualPublicationEvent::getId).collect(Collectors.toSet());
        Map<UUID, PublicationEvidenceCorrection> latestByEventId =
                PublicationEvidenceCorrection.latestByEventId(evidenceCorrectionRepository.findByEvent_IdIn(eventIds));
        Map<UUID, String> effectiveUrlByEventId = new HashMap<>();
        for (ActualPublicationEvent event : events) {
            PublicationEvidenceCorrection latest = latestByEventId.get(event.getId());
            effectiveUrlByEventId.put(event.getId(), latest != null ? latest.getCorrectedEvidenceUrl() : event.getEvidenceUrl());
        }
        return effectiveUrlByEventId;
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
