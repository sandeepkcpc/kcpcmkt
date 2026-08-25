package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.ApiErrorResponse;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.CreativePerformanceScorecardRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.performance.service.PerformanceService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPriority;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlanningMode;
import com.kcpc.mkt.planning.domain.ReelType;
import com.kcpc.mkt.planning.dto.PlannedOutputGroupResponse;
import com.kcpc.mkt.planning.dto.TalentSelection;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.planning.repository.PlanningPreparerRepository;
import com.kcpc.mkt.planning.service.PlanningService;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.EditingExecutionParticipantRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingExecutionParticipantRepository;
import com.kcpc.mkt.production.service.EditingService;
import com.kcpc.mkt.production.service.ShootingService;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.NaActionType;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.domain.PublicationTargetNaRecord;
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.publishing.repository.PublicationEvidenceCorrectionRepository;
import com.kcpc.mkt.publishing.repository.PublicationTargetNaRecordRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.publishing.service.PublishingService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.web.mvc.dto.ShootFeedbackEntry;
import com.kcpc.mkt.web.mvc.dto.ShootProgressStep;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.StageContext;
import com.kcpc.mkt.workflow.domain.TaskStage;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import com.kcpc.mkt.workflow.service.AdminActionService;
import com.kcpc.mkt.workflow.service.HoldService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * UI/UX Design Specification §9.6-9.15: the single Deliverable Detail shell whose panels
 * (Planning / Shoot / Edit / Publishing / Performance) follow the deliverable's current
 * workflow status - no panel ever exposes a manual status control (Principle 3). Every POST
 * handler here calls the SAME application/service layer as the equivalent REST controller
 * (CRITICAL ARCHITECTURE RULE) - never an HTTP self-call, never duplicated business logic.
 */
@Controller
@org.springframework.web.bind.annotation.RequestMapping("/app/deliverables/{id}")
public class DeliverableMvcController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ContentPlanRepository contentPlanRepository;
    private final PredefinedRoleMarksRepository predefinedRoleMarksRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final PublicationTargetNaRecordRepository naRecordRepository;
    private final PublicationTargetRepository publicationTargetRepository;
    private final PlanningPreparerRepository planningPreparerRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final ShootingExecutionParticipantRepository shootingParticipantRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final EditingExecutionParticipantRepository editingParticipantRepository;
    private final ActualPublicationEventRepository eventRepository;
    private final PublicationEvidenceCorrectionRepository evidenceCorrectionRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final PerformanceObligationRepository obligationRepository;
    private final CreativePerformanceScorecardRepository scorecardRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final WorkHoldRecordRepository holdRecordRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final com.kcpc.mkt.identity.service.OperationalEligibilityService operationalEligibilityService;
    private final ObjectMapper objectMapper;

    private final PlanningService planningService;
    private final ShootingService shootingService;
    private final EditingService editingService;
    private final PublishingService publishingService;
    private final PerformanceService performanceService;
    private final AdminActionService adminActionService;
    private final com.kcpc.mkt.workflow.service.AvailableActionService availableActionService;
    private final com.kcpc.mkt.drive.service.DriveProvisioningService driveProvisioningService;
    private final com.kcpc.mkt.drive.repository.ContentDriveProvisioningRepository driveProvisioningRepository;
    private final HoldService holdService;
    private final com.kcpc.mkt.discussion.service.StageCommentService stageCommentService;

    public DeliverableMvcController(ContentPlanRepository contentPlanRepository,
                                     PredefinedRoleMarksRepository predefinedRoleMarksRepository,
                                     PlannedOutputRepository plannedOutputRepository,
                                     ContentPlanTalentEntryRepository talentEntryRepository,
                                     PlannedOutputPublicationTargetMappingRepository mappingRepository,
                                     PublicationTargetNaRecordRepository naRecordRepository,
                                     PublicationTargetRepository publicationTargetRepository,
                                     PlanningPreparerRepository planningPreparerRepository,
                                     ShootingAssignmentRepository shootingAssignmentRepository,
                                     ShootingExecutionParticipantRepository shootingParticipantRepository,
                                     EditingAssignmentRepository editingAssignmentRepository,
                                     EditingExecutionParticipantRepository editingParticipantRepository,
                                     ActualPublicationEventRepository eventRepository,
                                     PublicationEvidenceCorrectionRepository evidenceCorrectionRepository,
                                     PublishingAssignmentRepository publishingAssignmentRepository,
                                     PerformanceObligationRepository obligationRepository,
                                     CreativePerformanceScorecardRepository scorecardRepository,
                                     ReviewCycleRepository reviewCycleRepository,
                                     WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                     WorkHoldRecordRepository holdRecordRepository, UserRepository userRepository,
                                     AuthorizationService authorizationService,
                                     com.kcpc.mkt.identity.service.OperationalEligibilityService operationalEligibilityService,
                                     ObjectMapper objectMapper,
                                     PlanningService planningService,
                                     ShootingService shootingService, EditingService editingService,
                                     PublishingService publishingService, PerformanceService performanceService,
                                     AdminActionService adminActionService,
                                     com.kcpc.mkt.workflow.service.AvailableActionService availableActionService,
                                     com.kcpc.mkt.drive.service.DriveProvisioningService driveProvisioningService,
                                     com.kcpc.mkt.drive.repository.ContentDriveProvisioningRepository driveProvisioningRepository,
                                     HoldService holdService,
                                     com.kcpc.mkt.discussion.service.StageCommentService stageCommentService) {
        this.contentPlanRepository = contentPlanRepository;
        this.predefinedRoleMarksRepository = predefinedRoleMarksRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.mappingRepository = mappingRepository;
        this.naRecordRepository = naRecordRepository;
        this.publicationTargetRepository = publicationTargetRepository;
        this.planningPreparerRepository = planningPreparerRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.shootingParticipantRepository = shootingParticipantRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.editingParticipantRepository = editingParticipantRepository;
        this.eventRepository = eventRepository;
        this.evidenceCorrectionRepository = evidenceCorrectionRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.obligationRepository = obligationRepository;
        this.scorecardRepository = scorecardRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.holdRecordRepository = holdRecordRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.operationalEligibilityService = operationalEligibilityService;
        this.objectMapper = objectMapper;
        this.planningService = planningService;
        this.shootingService = shootingService;
        this.editingService = editingService;
        this.publishingService = publishingService;
        this.performanceService = performanceService;
        this.adminActionService = adminActionService;
        this.availableActionService = availableActionService;
        this.driveProvisioningService = driveProvisioningService;
        this.driveProvisioningRepository = driveProvisioningRepository;
        this.holdService = holdService;
        this.stageCommentService = stageCommentService;
    }

    private ContentPlan requirePlan(UUID id) {
        return contentPlanRepository.findById(id).orElseThrow(() -> DomainException.notFound("Content Plan not found"));
    }

    private boolean allowed(User user, OperationalPermission permission, LifecycleStage stage, ContentPlan plan) {
        try {
            authorizationService.requireAuthority(user, permission, stage, plan.getWorkflowInstance());
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ GET

    private static final java.util.Set<String> CONTENT_DETAIL_TABS = java.util.Set.of(
            "overview", "planning", "shoot", "edit", "publishing", "performance", "timeline");

    @GetMapping
    public String view(@PathVariable UUID id, @RequestParam(required = false) String tab,
                        @AuthenticationPrincipal KcpcUserPrincipal principal, Model model,
                        jakarta.servlet.http.HttpServletRequest request) {
        ContentPlan plan = requirePlan(id);
        User user = principal.user();
        WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
        model.addAttribute("plan", plan);
        model.addAttribute("status", status);
        // Action Center's "Current Stage" - the one canonical Status->Stage resolver (never a
        // second, parallel mapping), so "Shoot Review" reads as "Shoot", not its raw status name.
        model.addAttribute("currentStage",
                com.kcpc.mkt.workflow.domain.ContentCanonicalStage.forStatus(status));
        // Structured Drive provisioning (may be absent for legacy content created before this
        // feature existed - plan.folderLink alone still works for those, untouched). Folder Link
        // Management (PERM_13) governs manual retry/relink; never PERM_02 alone.
        model.addAttribute("driveProvisioning", driveProvisioningRepository.findByContentPlan(plan).orElse(null));
        model.addAttribute("canManageDriveFolders",
                allowed(user, OperationalPermission.PERM_13_FOLDER_LINK_MANAGE, LifecycleStage.ADMINISTRATIVE, plan));
        model.addAttribute("user", user);
        model.addAttribute("today", LocalDate.now(BUSINESS_ZONE));
        // Lets a link (e.g. the My Work -> Assignment Management queue's "Set Up Shoot Team")
        // land the viewer directly on a specific tab (?tab=shoot) instead of always Overview -
        // purely which tab starts active, never a visibility/authorization decision.
        model.addAttribute("activeTab", tab != null && CONTENT_DETAIL_TABS.contains(tab) ? tab : "overview");

        predefinedRoleMarksRepository.findByContentPlan(plan).ifPresent(m -> model.addAttribute("marks", m));
        List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan(plan);
        model.addAttribute("outputs", outputs);

        // Planning Workspace groups outputs by reelGroupId: a REEL "+ Add Output" submission with
        // multiple Reel Types (e.g. SHORT + LONG) stays one grouped row sharing one Publication
        // Target set, while every other output is simply a "group of one" (ERD-TBL-011).
        Map<UUID, List<PlannedOutput>> outputGroupMembers = new java.util.LinkedHashMap<>();
        for (PlannedOutput o : outputs) {
            outputGroupMembers.computeIfAbsent(o.getReelGroupId(), k -> new java.util.ArrayList<>()).add(o);
        }
        List<PlannedOutput> outputGroupRepresentatives =
                outputGroupMembers.values().stream().map(members -> members.get(0)).toList();
        model.addAttribute("outputGroupMembers", outputGroupMembers);
        model.addAttribute("outputGroupRepresentatives", outputGroupRepresentatives);

        Map<UUID, List<com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping>> outputTargetMappings =
                new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, List<PlannedOutput>> entry : outputGroupMembers.entrySet()) {
            outputTargetMappings.put(entry.getKey(), mappingRepository.findByPlannedOutput(entry.getValue().get(0)));
        }
        model.addAttribute("outputTargetMappings", outputTargetMappings);
        model.addAttribute("outputTargetsByPlatform", outputTargetMappings.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().collect(
                        java.util.stream.Collectors.groupingBy(
                                m -> m.getPublicationTarget().getPlatform().getPlatformName(),
                                java.util.LinkedHashMap::new, java.util.stream.Collectors.toList())))));
        // Publishing Scope Verification (modal) / Publishing Scope (read-only) share this: a mapping
        // is "published" once a real ActualPublicationEvent (ORIGINAL) exists for its exact
        // (plannedOutput, publicationTarget) pair - reusing PlanningService.unmapPublicationTarget's
        // own guard logic's data source, never re-deriving it. Locked mappings get a "Published"
        // badge instead of Edit/Delete controls in the modal.
        List<com.kcpc.mkt.publishing.domain.ActualPublicationEvent> originalEventsForScope =
                eventRepository.findByContentPlan(plan).stream()
                        .filter(e -> e.getEventType() == PublicationEventType.ORIGINAL).toList();
        java.util.Set<UUID> publishedMappingIds = outputTargetMappings.values().stream().flatMap(List::stream)
                .filter(m -> originalEventsForScope.stream().anyMatch(e ->
                        e.getPlannedOutput().getId().equals(m.getPlannedOutput().getId())
                                && e.getPublicationTarget().getId().equals(m.getPublicationTarget().getId())))
                .map(com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping::getId)
                .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("publishedMappingIds", publishedMappingIds);
        model.addAttribute("outputHasPublishedTarget", outputTargetMappings.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().anyMatch(m -> publishedMappingIds.contains(m.getId())))));
        model.addAttribute("outputNaRecords", outputs.stream().collect(
                java.util.stream.Collectors.toMap(PlannedOutput::getId, naRecordRepository::findByPlannedOutput)));
        // Repost cycle: non-null exactly when this deliverable has been Reopened for Publishing at
        // least once, in which case we're either mid-repost or have completed one before (see
        // PublishingService#currentPublishingCycleStart - the single source of truth also used to
        // decide Publishing Scope resolution, so the checklist and the workflow auto-advance rule
        // can never disagree about which cycle is active).
        Instant publishingCycleStart = publishingService.currentPublishingCycleStart(plan.getWorkflowInstance());
        boolean isRepostPublishingCycle = publishingCycleStart != null;
        model.addAttribute("isRepostPublishingCycle", isRepostPublishingCycle);
        model.addAttribute("publishingChecklist", buildPublishingChecklist(outputs, publishingCycleStart));
        model.addAttribute("talentEntries", talentEntryRepository.findByContentPlan(plan));
        model.addAttribute("modelUsers",
                userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Model"));
        // Permission-driven candidate eligibility (PERM_18/19/08), not Business-Role name - see
        // OperationalEligibilityService. Business Role is still shown alongside each candidate's
        // name in the picker (organizational context only, never the eligibility rule).
        model.addAttribute("camerapersonUsers",
                operationalEligibilityService.shootExecutionCandidates(plan.getWorkflowInstance()));
        model.addAttribute("videoEditorUsers",
                operationalEligibilityService.editExecutionCandidates(plan.getWorkflowInstance()));
        model.addAttribute("publisherUsers",
                operationalEligibilityService.publishingExecutionCandidates(plan.getWorkflowInstance()));
        model.addAttribute("publishingAssignments", publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan));
        List<PublicationTarget> activeTargets = publicationTargetRepository.findByActiveTrue();
        model.addAttribute("activePublicationTargets", activeTargets);
        model.addAttribute("activePlatformNames", activeTargets.stream()
                .map(t -> t.getPlatform().getPlatformName()).distinct().sorted().toList());
        model.addAttribute("outputTypes", OutputType.values());
        model.addAttribute("reelTypes", ReelType.values());
        model.addAttribute("priorities", ContentPriority.values());
        model.addAttribute("planningOptionsJson", buildPlanningOptionsJson(activeTargets, request));

        model.addAttribute("shootingAssignments", shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan));
        model.addAttribute("editingAssignments", editingAssignmentRepository.findByContentPlanAndActiveTrue(plan));
        // ENG-053: shooting_execution_participants/editing_execution_participants (ERD-TBL-038)
        // record a fresh row every execution cycle - a Request Rework followed by another Start
        // Shoot/Start Edit adds a NEW row for the same person, so the raw findByContentPlan result
        // can (and after a rework round-trip, will) contain the same user more than once. The
        // Qualifying Cameraperson(s)/Editor(s) picker must show each person once regardless -
        // deduped here, by user id (not name), before the model ever reaches the JSP.
        model.addAttribute("shootingParticipants",
                dedupeByUser(shootingParticipantRepository.findByContentPlan(plan),
                        com.kcpc.mkt.production.domain.ShootingExecutionParticipant::getCameraperson));
        model.addAttribute("editingParticipants",
                dedupeByUser(editingParticipantRepository.findByContentPlan(plan),
                        com.kcpc.mkt.production.domain.EditingExecutionParticipant::getEditor));
        model.addAttribute("activeUsers", userRepository.findByActiveTrueOrderByFullNameAsc());

        List<ActualPublicationEvent> events = eventRepository.findByContentPlan(plan);
        model.addAttribute("events", events);
        // Actual Publication Events table must show the CURRENT effective evidence URL (latest
        // correction if one exists), never the raw immutable original event.evidenceUrl directly -
        // same shared resolver the Pipeline platform popover uses, so the two views can never
        // disagree on which correction is current.
        model.addAttribute("effectiveEvidenceUrlByEventId", publishingService.resolveEffectiveEvidenceUrls(events));
        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(id);
        model.addAttribute("obligations", obligations);
        // Collectors.toMap rejects null values (no scorecard drafted yet is the common case) -
        // build the map manually rather than via a null-hostile collector.
        java.util.Map<UUID, com.kcpc.mkt.performance.domain.CreativePerformanceScorecard> scorecardsByObligation =
                new java.util.HashMap<>();
        for (PerformanceObligation obligation : obligations) {
            scorecardsByObligation.put(obligation.getId(), scorecardRepository.findByObligation(obligation).orElse(null));
        }
        model.addAttribute("scorecardsByObligation", scorecardsByObligation);
        // Correct-a-Metric's "Current Value" and the submitted-scorecard summary (Hook/Hold/CTR)
        // must both reflect the latest per-metric correction (ERD-CON-060), never the frozen
        // at-submission-time raw value - same resolver for both, keyed by obligation id like
        // scorecardsByObligation above so the JSP can look either map up from the same ${ob.id}.
        java.util.Map<UUID, com.kcpc.mkt.performance.dto.EffectiveScorecardMetrics> effectiveMetricsByObligation =
                new java.util.HashMap<>();
        java.util.Map<UUID, java.util.List<com.kcpc.mkt.performance.domain.PerformanceMetricCorrection>> correctionsByObligation =
                new java.util.HashMap<>();
        for (PerformanceObligation obligation : obligations) {
            com.kcpc.mkt.performance.domain.CreativePerformanceScorecard sc = scorecardsByObligation.get(obligation.getId());
            if (sc != null && sc.isSubmitted()) {
                effectiveMetricsByObligation.put(obligation.getId(), performanceService.resolveEffectiveMetrics(sc));
                correctionsByObligation.put(obligation.getId(), performanceService.correctionsFor(sc));
            }
        }
        model.addAttribute("effectiveMetricsByObligation", effectiveMetricsByObligation);
        model.addAttribute("correctionsByObligation", correctionsByObligation);

        var timeline = new java.util.ArrayList<>(transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(plan.getWorkflowInstance()));
        java.util.Collections.reverse(timeline); // UI/UX §9.15: newest first.
        model.addAttribute("timeline", timeline);
        Optional<com.kcpc.mkt.workflow.domain.WorkHoldRecord> openHold =
                holdRecordRepository.findByWorkflowInstanceAndResumedAtIsNull(plan.getWorkflowInstance());
        model.addAttribute("openHold", openHold.orElse(null));
        // BR-063 Hold/Resume: every Hold/Resume cycle stays individually visible here (append-only,
        // ERD-CON-062 - a later cycle never overwrites an earlier one's reason), separate from the
        // real WorkflowTransitionHistory-based Timeline above since Hold is deliberately not a
        // status transition (ERD-CON-061).
        model.addAttribute("holdHistory",
                holdRecordRepository.findByWorkflowInstanceOrderByHeldAtDesc(plan.getWorkflowInstance()));
        model.addAttribute("delayed", plan.getPlannedLiveDate() != null
                && plan.getPlannedLiveDate().isBefore(LocalDate.now(BUSINESS_ZONE))
                && status != WorkflowStatus.COMP && status != WorkflowStatus.CAN);

        // Pending review cycles per gate (drives read-only vs review-gate rendering).
        model.addAttribute("pendingPlanningReview", pendingCycle(plan, GateType.PLANNING_REVIEW));
        model.addAttribute("pendingShootReview", pendingCycle(plan, GateType.SHOOT_REVIEW));
        model.addAttribute("pendingEditReview", pendingCycle(plan, GateType.EDIT_REVIEW));
        // ENG-058: Rework Feedback block on the Shoot panel - the reason text ONLY if the most
        // recently DECIDED Shoot Review cycle was a Request Rework (a later Approve, if any,
        // naturally supersedes it since this always looks at the latest decided cycle).
        model.addAttribute("shootReworkFeedback", latestDecidedReworkReason(plan, GateType.SHOOT_REVIEW));

        // Self-review barriers (Permission #3/#5/#7) only apply to authority exercised via an
        // explicit PermissionGrant (Employees granted the permission individually) - matching
        // PlanningService/ShootingService/EditingService's own `actingGrant.isPresent()` checks,
        // CEO/Marketing Manager's native (blanket, ungranted) authority is exempt, so a preparer
        // who happens to be CEO/Marketing Manager can still decide their own submission.
        boolean nativeAuthority = authorizationService.hasNativeAuthority(user);
        boolean isPreparer = planningPreparerRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getPreparer().getId().equals(user.getId()));
        boolean isShootParticipant = shootingParticipantRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getCameraperson().getId().equals(user.getId()));
        boolean isEditParticipant = editingParticipantRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getEditor().getId().equals(user.getId()));
        boolean planningSelfReviewBlocked = isPreparer && !nativeAuthority;
        boolean shootSelfReviewBlocked = isShootParticipant && !nativeAuthority;
        boolean editSelfReviewBlocked = isEditParticipant && !nativeAuthority;

        // Permission-gated visibility flags - server re-validates unconditionally on every POST.
        model.addAttribute("canPlanningExecute", allowed(user, OperationalPermission.PERM_02_PLANNING_EXECUTION, LifecycleStage.PLANNING, plan));
        model.addAttribute("canAssignCameraperson", allowed(user, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, LifecycleStage.PLANNING, plan));
        boolean canDecidePlanning = allowed(user, OperationalPermission.PERM_03_PLANNING_REVIEW, LifecycleStage.PLANNING, plan) && !planningSelfReviewBlocked;
        model.addAttribute("canDecidePlanningReview", canDecidePlanning);
        model.addAttribute("planningSelfReviewBlocked", planningSelfReviewBlocked);

        // ENG-043: Start/Submit are hands-on execution, not management - restricted to an actively
        // assigned Cameraperson/Editor/Publisher only. CEO/MM's native authority deliberately does
        // NOT bypass this (unlike every other *OrNative/canX flag on this page), matching
        // ShootingService/EditingService/PublishingService#requireActiveAssignee server-side.
        model.addAttribute("isShootActiveAssignee",
                shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getCameraperson().getId().equals(user.getId())));
        boolean canDecideShoot = allowed(user, OperationalPermission.PERM_05_SHOOT_REVIEW, LifecycleStage.SHOOTING, plan) && !shootSelfReviewBlocked;
        model.addAttribute("canDecideShootReview", canDecideShoot);
        model.addAttribute("shootSelfReviewBlocked", shootSelfReviewBlocked);

        model.addAttribute("canAssignEditor", allowed(user, OperationalPermission.PERM_06_EDIT_ASSIGNMENT, LifecycleStage.EDITING, plan));
        model.addAttribute("isEditActiveAssignee",
                editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getEditor().getId().equals(user.getId())));
        boolean canDecideEdit = allowed(user, OperationalPermission.PERM_07_EDIT_REVIEW, LifecycleStage.EDITING, plan) && !editSelfReviewBlocked;
        model.addAttribute("canDecideEditReview", canDecideEdit);
        model.addAttribute("editSelfReviewBlocked", editSelfReviewBlocked);

        model.addAttribute("canPublishingExecute", allowed(user, OperationalPermission.PERM_08_PUBLISHING_EXECUTION, LifecycleStage.PUBLISHING, plan));
        model.addAttribute("isPublishActiveAssignee",
                publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getPublisher().getId().equals(user.getId())));
        // ENG-044: Publisher(s) assignment (the picker/add/remove) is native CEO/MM only - a
        // PERM_08 grant exists so a rank-and-file Publisher can execute their OWN assigned task,
        // never to assign/reassign/remove other Publishers on this plan.
        model.addAttribute("canAssignPublisher", nativeAuthority);
        model.addAttribute("canPerformanceUpdate", allowed(user, OperationalPermission.PERM_09_PERFORMANCE_UPDATE, LifecycleStage.PERFORMANCE, plan));
        model.addAttribute("canReschedule", allowed(user, OperationalPermission.PERM_10_RESCHEDULE, LifecycleStage.ADMINISTRATIVE, plan));
        model.addAttribute("canReassign", allowed(user, OperationalPermission.PERM_11_REASSIGN, LifecycleStage.ADMINISTRATIVE, plan));
        model.addAttribute("canCancel", allowed(user, OperationalPermission.PERM_12_CANCEL, LifecycleStage.ADMINISTRATIVE, plan));
        model.addAttribute("isNative", nativeAuthority);

        // ENG-046: Description is editable by whoever can Assign for that stage (Publishing:
        // native-only, matching ENG-044); comment authorship matches whoever's currently the
        // active assignee for that stage (or native), mirrored server-side in
        // StageCommentService#requireCommentAuthority - these flags are UI-visibility only.
        // ENG-048: Shoot Instructions is ALSO editable by a Planning Approver (PERM_03), not just
        // PERM_04, mirrored in PlanningService#requireShootDescriptionAuthority - so it can be
        // shown/edited on the Planning Review Decision screen too, not only during Planning itself.
        model.addAttribute("canEditShootDescription", allowed(user, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, LifecycleStage.PLANNING, plan)
                || allowed(user, OperationalPermission.PERM_03_PLANNING_REVIEW, LifecycleStage.PLANNING, plan));
        model.addAttribute("canEditEditDescription", allowed(user, OperationalPermission.PERM_06_EDIT_ASSIGNMENT, LifecycleStage.EDITING, plan));
        model.addAttribute("canEditPublishingDescription", nativeAuthority);
        boolean canCommentOnShoot = nativeAuthority || shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                .stream().anyMatch(a -> a.getCameraperson().getId().equals(user.getId()));
        boolean canCommentOnEdit = nativeAuthority || editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                .stream().anyMatch(a -> a.getEditor().getId().equals(user.getId()));
        boolean canCommentOnPublishing = nativeAuthority || publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                .stream().anyMatch(a -> a.getPublisher().getId().equals(user.getId()));
        model.addAttribute("canCommentOnShoot", canCommentOnShoot);
        model.addAttribute("canCommentOnEdit", canCommentOnEdit);
        model.addAttribute("canCommentOnPublishing", canCommentOnPublishing);
        model.addAttribute("shootComments", stageCommentService.listComments(id, LifecycleStage.SHOOTING));
        model.addAttribute("editComments", stageCommentService.listComments(id, LifecycleStage.EDITING));
        model.addAttribute("publishingComments", stageCommentService.listComments(id, LifecycleStage.PUBLISHING));

        model.addAttribute("stageContexts", StageContext.values());
        // Reassign's Task Stage dropdown only ever offers a stage actually eligible right now
        // (AvailableActionService#eligibleReassignTaskStages) - never the unconditional full
        // TaskStage.values() - so a visible option always means submitting it is expected to
        // succeed, same contract as the Action Center's Reassign button itself.
        model.addAttribute("taskStages", availableActionService.eligibleReassignTaskStages(plan));

        // ENG-064: a Camera Person viewing their own Shoot task gets a dedicated, redesigned,
        // read-mostly detail page instead of the full CEO/MM-oriented multi-stage shell - same
        // route, same GET handler, same underlying model-building above (reused wholesale), just a
        // different view name, matching the established "same route, branch server-side by role"
        // house pattern (/app/my-work, /app/pipeline, /app/ideas). Scoped to the Shoot-relevant
        // status window (SA/SIP/SRV/SAP) - outside that window there is no "Shoot task" to show a
        // Cameraperson-focused page for, so the standard shell renders as before.
        String businessRoleName = user.getBusinessRole() == null ? null : user.getBusinessRole().getRoleName();
        boolean shootRelevantStatus = status == WorkflowStatus.SA || status == WorkflowStatus.SIP
                || status == WorkflowStatus.SRV || status == WorkflowStatus.SAP;
        if ("Camera Person".equals(businessRoleName) && shootRelevantStatus) {
            addShootTaskDetailAttributes(plan, status, user, model);
            return "shoot-task-detail";
        }
        // ENG-066: exact mirror for a Video Editor viewing their own Edit task. EAP is included
        // defensively even though it's transient/never actually observed (ERV->EAP->RFP fires
        // atomically in EditingService#decideEditReview) - unlike Shoot's SAP, there is no resting
        // "Edit Approved" status, so this window is EA/ED/ERV only in practice.
        boolean editRelevantStatus = status == WorkflowStatus.EA || status == WorkflowStatus.ED
                || status == WorkflowStatus.ERV || status == WorkflowStatus.EAP;
        if ("Video Editor".equals(businessRoleName) && editRelevantStatus) {
            addEditTaskDetailAttributes(plan, status, user, model);
            return "edit-task-detail";
        }
        // ENG-068: same mirror again for a Publisher viewing their own Publishing task. Unlike
        // Shoot/Edit, PP is included here - Publishing has no review/rework gate, so once the
        // publication scope resolves and PP is reached, that IS the Publisher's final resting state
        // (not a hand-off to a different stage's page the way Edit's approval hands off to
        // Publishing) - the redesigned page keeps rendering there instead of falling back to the
        // standard shell.
        boolean publishRelevantStatus = status == WorkflowStatus.RFP || status == WorkflowStatus.PUBG
                || status == WorkflowStatus.PP;
        if ("Publisher".equals(businessRoleName) && publishRelevantStatus) {
            addPublishTaskDetailAttributes(plan, status, model);
            return "publish-task-detail";
        }

        // ENG-082: CEO/MM management shell - Platforms×Channels summary (Publishing tab) and the
        // combined Planning/Shoot/Edit Review Feedback History, only needed on this fallback path.
        model.addAttribute("platformSummaries", buildContentDetailPlatformSummaries(plan, outputs));
        List<ShootFeedbackEntry> feedbackHistory = buildReviewFeedbackHistory(plan);
        model.addAttribute("reviewFeedbackHistory", feedbackHistory);
        model.addAttribute("availableActions", buildAvailableActions(plan, status, model, user, nativeAuthority, openHold));

        // ENG-082: Overview tab's Actual Shoot/Edit/Live Date - same derivation
        // PipelineDashboardService already uses (Actual Shoot/Edit Date = the date that gate's
        // Review was APPROVED, from the Review Feedback History just built above; Actual Live Date
        // = the earliest ORIGINAL publication event's date, from the already-loaded `events` list) -
        // never invented, never a new query for the dates themselves.
        model.addAttribute("actualShootDate", latestApprovedDate(feedbackHistory, "Shoot Review"));
        model.addAttribute("actualEditDate", latestApprovedDate(feedbackHistory, "Edit Review"));
        List<ActualPublicationEvent> originalEventsForPlan = eventRepository.findByContentPlan(plan).stream()
                .filter(e -> e.getEventType() == PublicationEventType.ORIGINAL).toList();
        model.addAttribute("actualLiveDate", originalEventsForPlan.stream()
                .map(ActualPublicationEvent::getActualPublicationTimestamp).min(Comparator.naturalOrder())
                .map(ts -> LocalDate.ofInstant(ts, BUSINESS_ZONE)).orElse(null));

        // Permission-scoped tab visibility: only the tabs actually relevant to this viewer's real
        // authority/participation on THIS plan - never the full Overview..Timeline set for every
        // viewer regardless of what they can do. The lifecycle stepper (always full, read-only
        // orientation) is deliberately untouched by this - it never gates on any of these flags.
        boolean canSeePlanningTab = nativeAuthority
                || allowed(user, OperationalPermission.PERM_02_PLANNING_EXECUTION, LifecycleStage.PLANNING, plan)
                || allowed(user, OperationalPermission.PERM_03_PLANNING_REVIEW, LifecycleStage.PLANNING, plan);
        boolean canSeeShootTab = nativeAuthority
                || allowed(user, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, LifecycleStage.PLANNING, plan)
                || allowed(user, OperationalPermission.PERM_05_SHOOT_REVIEW, LifecycleStage.SHOOTING, plan)
                || allowed(user, OperationalPermission.PERM_11_REASSIGN, LifecycleStage.ADMINISTRATIVE, plan)
                || allowed(user, OperationalPermission.PERM_18_SHOOT_EXECUTION, LifecycleStage.SHOOTING, plan)
                || shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getCameraperson().getId().equals(user.getId()));
        boolean canSeeEditTab = nativeAuthority
                || allowed(user, OperationalPermission.PERM_06_EDIT_ASSIGNMENT, LifecycleStage.EDITING, plan)
                || allowed(user, OperationalPermission.PERM_07_EDIT_REVIEW, LifecycleStage.EDITING, plan)
                || allowed(user, OperationalPermission.PERM_11_REASSIGN, LifecycleStage.ADMINISTRATIVE, plan)
                || allowed(user, OperationalPermission.PERM_19_EDIT_EXECUTION, LifecycleStage.EDITING, plan)
                || editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getEditor().getId().equals(user.getId()));
        boolean canSeePublishingTab = nativeAuthority
                || allowed(user, OperationalPermission.PERM_08_PUBLISHING_EXECUTION, LifecycleStage.PUBLISHING, plan)
                || publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getPublisher().getId().equals(user.getId()));
        boolean canSeePerformanceTab = nativeAuthority
                || allowed(user, OperationalPermission.PERM_09_PERFORMANCE_UPDATE, LifecycleStage.PERFORMANCE, plan);
        // Timeline is full workflow/audit history, not an operational-stage tab - gated by the
        // audit-history permission (or native authority), same as the Audit/Logs module elsewhere,
        // never implied just because the viewer can see some other stage tab.
        boolean canSeeTimeline = nativeAuthority
                || allowed(user, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW, LifecycleStage.ADMINISTRATIVE, plan);
        model.addAttribute("canSeePlanningTab", canSeePlanningTab);
        model.addAttribute("canSeeShootTab", canSeeShootTab);
        model.addAttribute("canSeeEditTab", canSeeEditTab);
        model.addAttribute("canSeePublishingTab", canSeePublishingTab);
        model.addAttribute("canSeePerformanceTab", canSeePerformanceTab);
        model.addAttribute("canSeeTimeline", canSeeTimeline);
        // Requested tab falls back to Overview (always visible) if this viewer can't actually see it -
        // e.g. a stale/direct ?tab=planning link for an HR employee with no Planning authority.
        String requestedTab = (String) model.getAttribute("activeTab");
        boolean requestedTabVisible = switch (requestedTab) {
            case "planning" -> canSeePlanningTab;
            case "shoot" -> canSeeShootTab;
            case "edit" -> canSeeEditTab;
            case "publishing" -> canSeePublishingTab;
            case "performance" -> canSeePerformanceTab;
            case "timeline" -> canSeeTimeline;
            default -> true;
        };
        if (!requestedTabVisible) {
            model.addAttribute("activeTab", "overview");
        }

        // Back navigation is caller-aware: only a viewer who can actually reach the Content
        // Pipeline (native authority) gets "Back to Pipeline" - everyone else (a delegated employee
        // reaching this page from My Work / Assignment Management) gets "Back to My Work" instead,
        // never a link to a module they cannot open.
        model.addAttribute("canSeePipeline", nativeAuthority);

        return "deliverable-detail";
    }

    /**
     * ENG-082 (defect fix): Content Detail's Publishing tab Platform×Channel chips - reuses
     * {@link com.kcpc.mkt.reporting.service.PipelineDashboardService#buildPlatformSummaries}
     * verbatim (same "published only when a real ORIGINAL event exists with a non-blank, current/
     * correction-resolved effective Evidence URL" rule the Pipeline dashboard already uses for the
     * exact same data), scoped to this one plan instead of the dashboard's multi-plan batch.
     * Mappings are re-queried per real output ({@code mappingRepository.findByPlannedOutput_IdIn}
     * over every one of this plan's {@code outputs}), NOT flattened from {@code outputTargetMappings}
     * - that map is grouped by reel-group representative for the Planning tab's editing UI (one row
     * per group), so flattening it here would under-count exactly like the original Pipeline
     * dashboard defect this fix addresses (e.g. a 2-Reel-Type group's second type's mappings would
     * be silently dropped). Every screen - Publisher's checklist, Pipeline dashboard, and this
     * Content Detail summary - must agree on the same real per-output publishing scope.
     */
    private List<com.kcpc.mkt.reporting.dto.PipelinePlatformSummary> buildContentDetailPlatformSummaries(
            ContentPlan plan, List<PlannedOutput> outputs) {
        List<UUID> outputIds = outputs.stream().map(PlannedOutput::getId).toList();
        if (outputIds.isEmpty()) {
            return List.of();
        }
        List<com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping> allMappings =
                mappingRepository.findByPlannedOutput_IdIn(outputIds);
        if (allMappings.isEmpty()) {
            return List.of();
        }
        // ENG-082 (defect fix): label fields (title/type) must be read from these already-fully-
        // loaded PlannedOutput entities, never from mapping.getPlannedOutput() - that association is
        // LAZY and this controller isn't @Transactional, so touching a label field on it directly
        // would throw LazyInitializationException the moment the session that loaded it is gone.
        Map<UUID, PlannedOutput> outputsById = outputs.stream()
                .collect(java.util.stream.Collectors.toMap(PlannedOutput::getId, o -> o));
        // ENG-082 (defect fix): one real ORIGINAL event per exact (Planned Output, Publication
        // Target) pair - never collapsed across Planned Outputs sharing the same target, matching
        // buildPublishingChecklist's own per-output event lookup exactly.
        List<ActualPublicationEvent> originalEvents = eventRepository.findByContentPlan(plan).stream()
                .filter(e -> e.getEventType() == PublicationEventType.ORIGINAL).toList();
        Map<UUID, Map<UUID, ActualPublicationEvent>> latestEventByOutputAndTarget = new java.util.HashMap<>();
        for (ActualPublicationEvent e : originalEvents) {
            latestEventByOutputAndTarget
                    .computeIfAbsent(e.getPlannedOutput().getId(), k -> new java.util.HashMap<>())
                    .merge(e.getPublicationTarget().getId(), e,
                            (a, b) -> a.getActualPublicationTimestamp().isAfter(b.getActualPublicationTimestamp()) ? a : b);
        }
        java.util.Set<UUID> representativeEventIds = latestEventByOutputAndTarget.values().stream()
                .flatMap(m -> m.values().stream()).map(ActualPublicationEvent::getId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, com.kcpc.mkt.publishing.domain.PublicationEvidenceCorrection> latestCorrectionByEventId = new java.util.HashMap<>();
        if (!representativeEventIds.isEmpty()) {
            for (var corr : evidenceCorrectionRepository.findByEvent_IdIn(representativeEventIds)) {
                latestCorrectionByEventId.merge(corr.getEvent().getId(), corr,
                        (a, b) -> a.getCorrectedAt().isAfter(b.getCorrectedAt()) ? a : b);
            }
        }
        // ENG-082 (defect fix): a mapping currently DESIGNATED N/A is excluded entirely - matches
        // buildPublishingChecklist's own rule exactly, so the Content Detail's Platforms×Channels
        // chips can never show a different task set than the Publisher's own checklist. Keyed by
        // each mapping's REAL Planned Output id (never outputTargetMappings' reel-group key, a
        // separate generated id that never matches any individual PlannedOutput's own id) so the
        // lookup in buildPlatformSummaries (keyed the same real way) actually hits.
        Map<UUID, java.util.Set<UUID>> naTargetIdsByOutput = new java.util.HashMap<>();
        java.util.Set<UUID> realOutputIds = allMappings.stream()
                .map(m -> m.getPlannedOutput().getId()).collect(java.util.stream.Collectors.toSet());
        for (UUID outputId : realOutputIds) {
            PlannedOutput output = outputsById.get(outputId);
            if (output == null) {
                continue;
            }
            Map<UUID, PublicationTargetNaRecord> latestNaByTarget = new java.util.HashMap<>();
            for (PublicationTargetNaRecord r : naRecordRepository.findByPlannedOutput(output)) {
                latestNaByTarget.merge(r.getPublicationTarget().getId(), r,
                        (a, b) -> a.getRecordedAt().isAfter(b.getRecordedAt()) ? a : b);
            }
            java.util.Set<UUID> naTargets = latestNaByTarget.values().stream()
                    .filter(r -> r.getActionType() == NaActionType.DESIGNATED)
                    .map(r -> r.getPublicationTarget().getId())
                    .collect(java.util.stream.Collectors.toSet());
            if (!naTargets.isEmpty()) {
                naTargetIdsByOutput.put(outputId, naTargets);
            }
        }
        return com.kcpc.mkt.reporting.service.PipelineDashboardService.buildPlatformSummaries(
                allMappings, outputsById, latestEventByOutputAndTarget, latestCorrectionByEventId, naTargetIdsByOutput);
    }

    /**
     * ENG-082: combined Review Feedback History for the CEO/MM management shell - every DECIDED
     * cycle across Planning, Shoot, and Edit (never Publishing - no review gate exists for it),
     * newest first. Reuses {@link ShootFeedbackEntry} (already gate-agnostic in shape) with its
     * ENG-082 {@code reviewStage}/{@code cycleNumber} fields identifying which gate/cycle each row
     * is - the same reviewer-batch-fetch and lead-detection pattern already used by
     * {@link #addShootTaskDetailAttributes}/{@link #addEditTaskDetailAttributes}, just merging all
     * three gates instead of one.
     */
    private List<ShootFeedbackEntry> buildReviewFeedbackHistory(ContentPlan plan) {
        List<ReviewCycle> planningDecided = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), GateType.PLANNING_REVIEW)
                .stream().filter(c -> c.getDecidedAt() != null).toList();
        List<ReviewCycle> shootDecided = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), GateType.SHOOT_REVIEW)
                .stream().filter(c -> c.getDecidedAt() != null).toList();
        List<ReviewCycle> editDecided = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), GateType.EDIT_REVIEW)
                .stream().filter(c -> c.getDecidedAt() != null).toList();

        java.util.Set<UUID> reviewerIds = java.util.stream.Stream.of(planningDecided, shootDecided, editDecided)
                .flatMap(List::stream).map(ReviewCycle::getReviewer).filter(java.util.Objects::nonNull)
                .map(User::getId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, User> reviewersById = reviewerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(reviewerIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        var shootLead = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(com.kcpc.mkt.production.domain.ShootingAssignment::isLead).findFirst().orElse(null);
        UUID shootLeadId = shootLead == null ? null : shootLead.getCameraperson().getId();
        var editLead = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(com.kcpc.mkt.production.domain.EditingAssignment::isLead).findFirst().orElse(null);
        UUID editLeadId = editLead == null ? null : editLead.getEditor().getId();

        List<ShootFeedbackEntry> combined = new ArrayList<>();
        combined.addAll(toFeedbackEntries(planningDecided, "Planning Review", null, reviewersById));
        combined.addAll(toFeedbackEntries(shootDecided, "Shoot Review", shootLeadId, reviewersById));
        combined.addAll(toFeedbackEntries(editDecided, "Edit Review", editLeadId, reviewersById));
        return combined.stream()
                .sorted(Comparator.comparing(ShootFeedbackEntry::getDecidedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static List<ShootFeedbackEntry> toFeedbackEntries(List<ReviewCycle> decided, String reviewStage,
                                                                UUID leadId, Map<UUID, User> reviewersById) {
        return decided.stream().map(c -> {
            boolean rework = "REQUEST_REWORK".equals(c.getDecision());
            UUID reviewerId = c.getReviewer() == null ? null : c.getReviewer().getId();
            User reviewer = reviewerId == null ? null : reviewersById.get(reviewerId);
            boolean reviewerIsLead = reviewerId != null && leadId != null && reviewerId.equals(leadId);
            return new ShootFeedbackEntry(reviewStage, c.getCycleNumber(), rework ? "Rework Required" : "Approved",
                    rework ? "status-needschanges" : "status-completed", c.getDecisionReason(),
                    reviewer == null ? null : reviewer.getFullName(), reviewerIsLead, c.getDecidedAt());
        }).toList();
    }

    /**
     * ENG-082: "Actual Shoot/Edit Date" for the Overview tab - the date that gate's Review was
     * APPROVED (never a rework decision), matching {@code PipelineDashboardService}'s own
     * derivation exactly, just read from the {@code reviewFeedbackHistory} already built above
     * instead of a second query. Latest wins if more than one Approved entry ever exists for the
     * same gate (defensive only - a decided ReviewCycle is immutable, ERD-CON-039).
     */
    private static LocalDate latestApprovedDate(List<ShootFeedbackEntry> feedbackHistory, String reviewStage) {
        return feedbackHistory.stream()
                .filter(f -> reviewStage.equals(f.getReviewStage()) && "Approved".equals(f.getDecisionLabel()))
                .map(ShootFeedbackEntry::getDecidedAt).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(ts -> LocalDate.ofInstant(ts, BUSINESS_ZONE)).orElse(null);
    }

    /**
     * ENG-082: the CEO/MM Action Center's dynamic action list - assembled from the SAME permission
     * flags this method already pushed into {@code model} above (re-read back out of the {@link Model}
     * here rather than re-computed, so there is exactly one source of truth per flag) plus the SAME
     * per-status conditions the old admin-actions bar encoded as JSTL {@code c:if}s (Hold/Resume only
     * at SIP/ED, Reopen only at COMP). Every action's own actual authorization is re-validated
     * server-side by its POST handler regardless of what appears here - this list is UI-visibility
     * only, exactly like every other {@code canX} flag on this page.
     */
    private List<com.kcpc.mkt.web.mvc.dto.AvailableAction> buildAvailableActions(
            ContentPlan plan, WorkflowStatus status, Model model, User user, boolean nativeAuthority,
            Optional<com.kcpc.mkt.workflow.domain.WorkHoldRecord> openHold) {
        List<com.kcpc.mkt.web.mvc.dto.AvailableAction> actions = new ArrayList<>();
        Map<String, Object> attrs = model.asMap();

        // Primary: whichever review gate is currently pending decision for this user. Planning
        // Review is a plain boolean decision (PlanningService#decidePlanningReview takes no extra
        // selection), so its Action Center form is genuinely complete and stays here. Shoot/Edit
        // Review are NOT plain boolean decisions - approval requires selecting at least one
        // qualifying final Cameraperson/Editor (ShootingService#decideShootReview,
        // EditingService#decideEditReview both throw VALIDATION_FAILED without one), and that
        // selection control only exists in the Shoot/Edit tabs' own canonical review UI - never
        // duplicated here. Exposing APPROVE_SHOOT_REVIEW/APPROVE_EDIT_REVIEW here would be a
        // dead-end action that always 400s, so Shoot/Edit review decisions live exclusively in
        // their own stage tab (Content Detail -> Shoot/Edit, and Reviews -> Shoot/Edit).
        if (Boolean.TRUE.equals(attrs.get("canDecidePlanningReview")) && status == WorkflowStatus.PLRV) {
            actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("APPROVE_PLANNING_REVIEW", "Approve Planning", "primary", "primary", false));
            actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("REQUEST_REWORK_PLANNING_REVIEW", "Request Rework", "danger", "primary", true));
        }

        // Other actions - permission flags the old admin-actions bar used, ANDed with the actual
        // AvailableActionService eligibility rule for each action (never permission alone) - the
        // single source of truth also enforced server-side by AdminActionService, so a button shown
        // here always means the backend expects the click to succeed (permission-admin-ui / Action
        // Center spec §1/§10/§12).
        if (availableActionService.isReschedulable(plan.getWorkflowInstance())
                && Boolean.TRUE.equals(attrs.get("canReschedule"))) {
            actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("RESCHEDULE", "Reschedule", "secondary", "other", true));
        }
        // Reassign: shown only when at least one taskStage is actually eligible right now (a valid
        // canonical stage window for that task stage AND an active assignment to reassign) - never
        // merely because PERM_11 is held. The Reassign form's own Task Stage dropdown is separately
        // filtered to the same eligible set (see the "taskStages" model attribute above).
        if (availableActionService.isAnyReassignEligible(plan) && Boolean.TRUE.equals(attrs.get("canReassign"))) {
            actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("REASSIGN", "Reassign", "secondary", "other", true));
        }
        if (nativeAuthority && (status == WorkflowStatus.SIP || status == WorkflowStatus.ED || status == WorkflowStatus.PUBG)) {
            if (openHold.isEmpty()) {
                actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("HOLD", "Hold Work", "secondary", "other", true));
            } else {
                actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("RESUME", "Resume Work", "secondary", "other", false));
            }
        }
        if (availableActionService.isCancellable(plan) && Boolean.TRUE.equals(attrs.get("canCancel"))) {
            actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("CANCEL", "Cancel", "danger", "other", true));
        }
        if (availableActionService.isReopenEligible(plan.getWorkflowInstance())) {
            if (Boolean.TRUE.equals(attrs.get("canPublishingExecute"))) {
                actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("REOPEN_PUBLISHING", "Reopen for Publishing", "secondary", "other", true));
            }
            if (Boolean.TRUE.equals(attrs.get("canPerformanceUpdate"))) {
                actions.add(new com.kcpc.mkt.web.mvc.dto.AvailableAction("REOPEN_PERFORMANCE", "Reopen for Performance", "secondary", "other", true));
            }
        }
        return actions;
    }

    /**
     * ENG-064: everything the redesigned Camera Person Shoot Task Detail page needs beyond what
     * {@link #view} already builds for the shared shell (plan/status/user/talentEntries/
     * shootingAssignments/shootComments/canCommentOnShoot/isShootActiveAssignee/openHold/timeline
     * are all reused as-is) - the friendly status label, the display-only progress tracker, and
     * the Shoot Review feedback history (reusing {@link ShootFeedbackEntry}, the same gate-agnostic
     * DTO the Content Detail page's own Review Feedback History card uses - ENG-082).
     */
    private void addShootTaskDetailAttributes(ContentPlan plan, WorkflowStatus status, User user, Model model) {
        boolean reworkActive = status == WorkflowStatus.SIP
                && latestDecidedReworkReason(plan, GateType.SHOOT_REVIEW) != null;
        model.addAttribute("shootFriendlyStatusLabel", shootFriendlyStatusLabel(status, reworkActive));
        model.addAttribute("shootFriendlyStatusCssClass", shootFriendlyStatusCssClass(status, reworkActive));

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Integer delayDays = (plan.getPlannedShootDate() != null && plan.getPlannedShootDate().isBefore(today)
                && status != WorkflowStatus.SAP)
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(plan.getPlannedShootDate(), today) : null;
        model.addAttribute("shootDelayDays", delayDays);

        List<WorkflowTransitionHistory> ascending = transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(plan.getWorkflowInstance());
        Instant assignedAt = latestTransitionTimestamp(ascending, "ACTIVATE_SHOOTING");
        Instant inProgressAt = latestTransitionTimestamp(ascending, "START_SHOOTING");
        Instant reviewAt = latestTransitionTimestamp(ascending, "SUBMIT_SHOOT_REVIEW");
        Instant reworkAt = latestTransitionTimestamp(ascending, "REQUEST_REWORK_SHOOT");
        Instant approvedAt = latestTransitionTimestamp(ascending, "APPROVE_SHOOT");

        List<ShootProgressStep> progress = new ArrayList<>();
        progress.add(new ShootProgressStep("Assigned", "done", assignedAt));
        progress.add(new ShootProgressStep("In Progress", inProgressAt == null ? "pending"
                : (status == WorkflowStatus.SIP && !reworkActive ? "current" : "done"), inProgressAt));
        progress.add(new ShootProgressStep("Review", reviewAt == null ? "pending"
                : (status == WorkflowStatus.SRV ? "current" : "done"), reviewAt));
        progress.add(new ShootProgressStep("Rework Required", reworkActive ? "current" : "pending", reworkAt));
        progress.add(new ShootProgressStep("Approved", status == WorkflowStatus.SAP ? "done" : "pending", approvedAt));
        model.addAttribute("shootProgressSteps", progress);

        // Feedback history: decided SHOOT_REVIEW cycles only, newest first - never another gate's
        // data (Idea/Planning/Edit Review) on this Camera Person screen.
        List<ReviewCycle> decided = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), GateType.SHOOT_REVIEW)
                .stream().filter(c -> c.getDecidedAt() != null).toList();
        var lead = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(com.kcpc.mkt.production.domain.ShootingAssignment::isLead).findFirst().orElse(null);
        java.util.UUID leadUserId = lead == null ? null : lead.getCameraperson().getId();
        // ReviewCycle.reviewer is LAZY and this controller isn't @Transactional - touch only the
        // proxy's own .getId() (safe, no DB hit), then batch-fetch the real User rows once
        // (same pattern/bug already fixed for My Work's feedback section, ENG-062).
        java.util.Set<java.util.UUID> reviewerIds = decided.stream().map(ReviewCycle::getReviewer)
                .filter(java.util.Objects::nonNull).map(User::getId).collect(java.util.stream.Collectors.toSet());
        Map<java.util.UUID, User> reviewersById = reviewerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(reviewerIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<ShootFeedbackEntry> feedbackHistory = decided.stream().map(c -> {
            boolean rework = "REQUEST_REWORK".equals(c.getDecision());
            java.util.UUID reviewerId = c.getReviewer() == null ? null : c.getReviewer().getId();
            User reviewer = reviewerId == null ? null : reviewersById.get(reviewerId);
            boolean reviewerIsLead = reviewerId != null && leadUserId != null && reviewerId.equals(leadUserId);
            return new ShootFeedbackEntry("Shoot Review", c.getCycleNumber(), rework ? "Rework Required" : "Approved",
                    rework ? "status-needschanges" : "status-completed", c.getDecisionReason(),
                    reviewer == null ? null : reviewer.getFullName(), reviewerIsLead, c.getDecidedAt());
        }).toList();
        model.addAttribute("shootFeedbackLatest", feedbackHistory.isEmpty() ? null : feedbackHistory.get(0));
        model.addAttribute("shootFeedbackPriorHistory",
                feedbackHistory.isEmpty() ? List.of() : feedbackHistory.subList(1, feedbackHistory.size()));
    }

    private static Instant latestTransitionTimestamp(List<WorkflowTransitionHistory> ascending, String triggerCommand) {
        return ascending.stream().filter(t -> triggerCommand.equals(t.getTriggerCommand()))
                .reduce((first, second) -> second)
                .map(WorkflowTransitionHistory::getTransitionTimestamp).orElse(null);
    }

    private static String shootFriendlyStatusLabel(WorkflowStatus status, boolean reworkActive) {
        return switch (status) {
            case SA -> "Assigned";
            case SIP -> reworkActive ? "Rework Required" : "In Progress";
            case SRV -> "Submitted for Review";
            case SAP -> "Approved";
            default -> status.getStatusName();
        };
    }

    private static String shootFriendlyStatusCssClass(WorkflowStatus status, boolean reworkActive) {
        return switch (status) {
            case SA -> "status-assigned";
            case SIP -> reworkActive ? "status-needschanges" : "status-inprogress";
            case SRV -> "status-inreview";
            case SAP -> "status-completed";
            default -> "status-inprogress";
        };
    }

    /**
     * ENG-066: exact mirror of {@link #addShootTaskDetailAttributes} for a Video Editor's Edit Task
     * Detail page - same shared {@link ShootFeedbackEntry}/{@link ShootProgressStep} DTOs, same
     * transition-timestamp/reviewer-batch-fetch pattern, EDIT_REVIEW/Editor-specific data only.
     */
    private void addEditTaskDetailAttributes(ContentPlan plan, WorkflowStatus status, User user, Model model) {
        boolean reworkActive = status == WorkflowStatus.ED
                && latestDecidedReworkReason(plan, GateType.EDIT_REVIEW) != null;
        model.addAttribute("editFriendlyStatusLabel", editFriendlyStatusLabel(status, reworkActive));
        model.addAttribute("editFriendlyStatusCssClass", editFriendlyStatusCssClass(status, reworkActive));

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Integer delayDays = (plan.getPlannedEditDate() != null && plan.getPlannedEditDate().isBefore(today)
                && status != WorkflowStatus.EAP)
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(plan.getPlannedEditDate(), today) : null;
        model.addAttribute("editDelayDays", delayDays);

        List<WorkflowTransitionHistory> ascending = transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(plan.getWorkflowInstance());
        Instant assignedAt = latestTransitionTimestamp(ascending, "ASSIGN_EDITOR");
        Instant inProgressAt = latestTransitionTimestamp(ascending, "START_EDITING");
        Instant reviewAt = latestTransitionTimestamp(ascending, "SUBMIT_EDIT_REVIEW");
        Instant reworkAt = latestTransitionTimestamp(ascending, "REQUEST_REWORK_EDIT");
        Instant approvedAt = latestTransitionTimestamp(ascending, "APPROVE_EDIT");

        List<ShootProgressStep> progress = new ArrayList<>();
        progress.add(new ShootProgressStep("Assigned", "done", assignedAt));
        progress.add(new ShootProgressStep("In Progress", inProgressAt == null ? "pending"
                : (status == WorkflowStatus.ED && !reworkActive ? "current" : "done"), inProgressAt));
        progress.add(new ShootProgressStep("Review", reviewAt == null ? "pending"
                : (status == WorkflowStatus.ERV ? "current" : "done"), reviewAt));
        progress.add(new ShootProgressStep("Rework Required", reworkActive ? "current" : "pending", reworkAt));
        progress.add(new ShootProgressStep("Approved", status == WorkflowStatus.EAP ? "done" : "pending", approvedAt));
        model.addAttribute("editProgressSteps", progress);

        // Feedback history: decided EDIT_REVIEW cycles only, newest first - never another gate's
        // data (Idea/Planning/Shoot Review) on this Video Editor screen.
        List<ReviewCycle> decided = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), GateType.EDIT_REVIEW)
                .stream().filter(c -> c.getDecidedAt() != null).toList();
        var lead = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(com.kcpc.mkt.production.domain.EditingAssignment::isLead).findFirst().orElse(null);
        java.util.UUID leadUserId = lead == null ? null : lead.getEditor().getId();
        java.util.Set<java.util.UUID> reviewerIds = decided.stream().map(ReviewCycle::getReviewer)
                .filter(java.util.Objects::nonNull).map(User::getId).collect(java.util.stream.Collectors.toSet());
        Map<java.util.UUID, User> reviewersById = reviewerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(reviewerIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<ShootFeedbackEntry> feedbackHistory = decided.stream().map(c -> {
            boolean rework = "REQUEST_REWORK".equals(c.getDecision());
            java.util.UUID reviewerId = c.getReviewer() == null ? null : c.getReviewer().getId();
            User reviewer = reviewerId == null ? null : reviewersById.get(reviewerId);
            boolean reviewerIsLead = reviewerId != null && leadUserId != null && reviewerId.equals(leadUserId);
            return new ShootFeedbackEntry("Edit Review", c.getCycleNumber(), rework ? "Rework Required" : "Approved",
                    rework ? "status-needschanges" : "status-completed", c.getDecisionReason(),
                    reviewer == null ? null : reviewer.getFullName(), reviewerIsLead, c.getDecidedAt());
        }).toList();
        model.addAttribute("editFeedbackLatest", feedbackHistory.isEmpty() ? null : feedbackHistory.get(0));
        model.addAttribute("editFeedbackPriorHistory",
                feedbackHistory.isEmpty() ? List.of() : feedbackHistory.subList(1, feedbackHistory.size()));
    }

    private static String editFriendlyStatusLabel(WorkflowStatus status, boolean reworkActive) {
        return switch (status) {
            case EA -> "Assigned";
            case ED -> reworkActive ? "Rework Required" : "In Progress";
            case ERV -> "Submitted for Review";
            case EAP -> "Approved";
            default -> status.getStatusName();
        };
    }

    private static String editFriendlyStatusCssClass(WorkflowStatus status, boolean reworkActive) {
        return switch (status) {
            case EA -> "status-assigned";
            case ED -> reworkActive ? "status-needschanges" : "status-inprogress";
            case ERV -> "status-inreview";
            case EAP -> "status-completed";
            default -> "status-inprogress";
        };
    }

    /**
     * ENG-068: everything the redesigned Publisher Publishing Task Detail page needs beyond what
     * {@link #view} already builds for the shared shell (plan/status/user/outputs/
     * publishingChecklist/publishingAssignments/publishingComments/canCommentOnPublishing/
     * canPublishingExecute/isPublishActiveAssignee/events/timeline are all reused as-is - the
     * checklist and comments markup is copied byte-for-byte from the shared shell so
     * publishing-checklist.js/stage-discussion.js need no changes). Publishing has no review/rework
     * gate, so - unlike {@link #addShootTaskDetailAttributes}/{@link #addEditTaskDetailAttributes} -
     * there is no feedback history to build here, and the progress tracker has its own 5 Publishing-
     * specific milestones instead of the Shoot/Edit Assigned/In Progress/Review/Rework/Approved shape.
     */
    private void addPublishTaskDetailAttributes(ContentPlan plan, WorkflowStatus status, Model model) {
        model.addAttribute("publishFriendlyStatusLabel", publishFriendlyStatusLabel(status));
        model.addAttribute("publishFriendlyStatusCssClass", publishFriendlyStatusCssClass(status));

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Integer delayDays = (plan.getPlannedLiveDate() != null && plan.getPlannedLiveDate().isBefore(today)
                && status != WorkflowStatus.PP)
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(plan.getPlannedLiveDate(), today) : null;
        model.addAttribute("publishDelayDays", delayDays);

        List<WorkflowTransitionHistory> ascending = transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(plan.getWorkflowInstance());
        Instant readyAt = latestTransitionToStatus(ascending, WorkflowStatus.RFP);
        Instant publishingAt = latestTransitionTimestamp(ascending, "START_PUBLISHING");
        Instant scopeResolvedAt = latestTransitionTimestamp(ascending, "PUBLICATION_SCOPE_RESOLVED");

        // Unlike Shoot/Edit Assignment, Publisher assignment triggers no workflow transition (the
        // plan is already at RFP before and after) - its own milestone date comes from the
        // assignment record itself, not WorkflowTransitionHistory.
        Instant assignedAt = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .map(PublishingAssignment::getAssignedAt).min(Comparator.naturalOrder()).orElse(null);

        List<ShootProgressStep> progress = new ArrayList<>();
        progress.add(new ShootProgressStep("Assigned", assignedAt == null ? "pending" : "done", assignedAt));
        progress.add(new ShootProgressStep("Ready for Publishing", "done", readyAt));
        progress.add(new ShootProgressStep("Publishing",
                status == WorkflowStatus.RFP ? "pending" : (status == WorkflowStatus.PUBG ? "current" : "done"),
                publishingAt));
        progress.add(new ShootProgressStep("Scope Resolved", status == WorkflowStatus.PP ? "done" : "pending", scopeResolvedAt));
        progress.add(new ShootProgressStep("Performance Pending", status == WorkflowStatus.PP ? "done" : "pending", scopeResolvedAt));
        model.addAttribute("publishProgressSteps", progress);
    }

    private static Instant latestTransitionToStatus(List<WorkflowTransitionHistory> ascending, WorkflowStatus toStatus) {
        return ascending.stream().filter(t -> t.getToStatusCode() == toStatus)
                .reduce((first, second) -> second)
                .map(WorkflowTransitionHistory::getTransitionTimestamp).orElse(null);
    }

    private static String publishFriendlyStatusLabel(WorkflowStatus status) {
        return switch (status) {
            case RFP -> "Ready for Publishing";
            case PUBG -> "Publishing";
            case PP -> "Performance Pending";
            default -> status.getStatusName();
        };
    }

    private static String publishFriendlyStatusCssClass(WorkflowStatus status) {
        return switch (status) {
            case RFP -> "status-assigned";
            case PUBG -> "status-inprogress";
            case PP -> "status-completed";
            default -> "status-inprogress";
        };
    }

    private com.kcpc.mkt.workflow.domain.ReviewCycle pendingCycle(ContentPlan plan, GateType gateType) {
        return reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), gateType)
                .stream().filter(c -> c.getDecidedAt() == null).findFirst().orElse(null);
    }

    /** ENG-058: the reason text from the most recently DECIDED cycle on this gate, but only when that decision was a rework request. */
    private String latestDecidedReworkReason(ContentPlan plan, GateType gateType) {
        return reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), gateType)
                .stream().filter(c -> c.getDecidedAt() != null).findFirst()
                .filter(c -> "REQUEST_REWORK".equals(c.getDecision()))
                .map(com.kcpc.mkt.workflow.domain.ReviewCycle::getDecisionReason).orElse(null);
    }

    private String redirect(UUID id) {
        return "redirect:/app/deliverables/" + id;
    }

    /**
     * Static reference data (Output/Reel Type enum names, active Platforms/Channels, the CSRF
     * pair) that planning-outputs.js needs once, up front, to build a brand-new Planned Output
     * row's Edit/+ Add Target forms client-side after an AJAX "+ Add Output" - without which the
     * server would have to send back full HTML for JS to splice in, defeating the point of
     * returning JSON. Embedded as a {@code <script type="application/json">} block (see
     * deliverable-detail.jsp) rather than scattered data-* attributes since it's a handful of
     * lists, not per-row state. {@code "<"} is escaped so a Channel handle containing
     * {@code </script>} can never break out of the block.
     */
    private String buildPlanningOptionsJson(List<PublicationTarget> activeTargets,
                                             jakarta.servlet.http.HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        Map<String, Object> options = new java.util.LinkedHashMap<>();
        options.put("csrfParamName", csrfToken.getParameterName());
        options.put("csrfToken", csrfToken.getToken());
        options.put("outputTypes", java.util.Arrays.stream(OutputType.values()).map(Enum::name).toList());
        options.put("reelTypes", java.util.Arrays.stream(ReelType.values()).map(Enum::name).toList());
        options.put("targets", activeTargets.stream().map(t -> Map.of(
                "id", t.getId().toString(),
                "platform", t.getPlatform().getPlatformName(),
                "channel", t.getChannel().getChannelHandle())).toList());
        try {
            return objectMapper.writeValueAsString(options).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize planning options", e);
        }
    }

    /**
     * Model(s) is a dropdown of Users holding the "Model" Business Role, not free text - resolves
     * straight to name+id pairs (ENG-067) so "My Shoots" can later find a Model's own shoots by a
     * real backend-enforced link, not a fragile name match.
     */
    private List<TalentSelection> resolveTalentSelections(List<UUID> modelUserIds) {
        if (modelUserIds == null || modelUserIds.isEmpty()) {
            return List.of();
        }
        return modelUserIds.stream()
                .map(userId -> userRepository.findById(userId)
                        .map(u -> new TalentSelection(u.getFullName(), u.getId())).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ------------------------------------------------------------- Planning

    /**
     * SKU Reference has no separate "N/A" checkbox in the UI (user request) - leaving the field
     * blank simply IS N/A, derived here rather than asked for explicitly. {@code ContentPlan.setSku}
     * still enforces ERD-CON-009 (mutual exclusion) unchanged; a non-blank value here always pairs
     * with {@code skuNotApplicable=false}.
     */
    private static boolean isSkuBlank(String skuReference) {
        return skuReference == null || skuReference.isBlank();
    }

    /** Single-form Planning submission: Parameters + Schedule (Standard or Urgent) in one POST. */
    @PostMapping("/plan")
    public String savePlan(@PathVariable UUID id, @RequestParam(required = false) String categoryText,
                            @RequestParam(required = false) ContentPriority contentPriority,
                            @RequestParam(required = false) String skuReference,
                            @RequestParam(required = false) List<UUID> modelUserIds,
                            @RequestParam(required = false) String folderLink,
                            @RequestParam PlanningMode planningMode,
                            @RequestParam LocalDate plannedLiveDate,
                            @RequestParam(required = false) LocalDate shootDate,
                            @RequestParam(required = false) LocalDate editDate,
                            @RequestParam(required = false) String urgencyReason,
                            @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        List<TalentSelection> talentSelections = resolveTalentSelections(modelUserIds);
        try {
            planningService.savePlan(principal.user(), id, categoryText, contentPriority, skuReference,
                    isSkuBlank(skuReference), talentSelections, folderLink, planningMode, plannedLiveDate, shootDate, editDate,
                    urgencyReason);
            ra.addFlashAttribute("successMessage", "Plan saved.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * Planning Workspace UI has one form and one button: Planning Details is
     * {@code <form id="planning-details-form">} with no submit button of its own; the single
     * "Submit for Planning Review" button at the bottom of the page references it via the HTML5
     * {@code form="..."} attribute. Shoot Assignment is no longer part of this form or this request
     * at all - it is managed exclusively (and takes effect immediately) from the Shoot tab's own
     * dedicated endpoints, so a PERM_04-only delegated employee has a working entry point too, not
     * just a canPlanningExecute+PERM_04 combination. This endpoint only saves Planning Details and
     * submits for review; {@link PlanningService#submitPlanningReview} rejects the submission (with
     * a clear message pointing back to the Shoot tab) if Shoot setup isn't already complete.
     */
    /**
     * ENG-051: AJAX-aware so a validation failure (e.g. Urgency Reason missing for an Urgent plan)
     * doesn't force a full-page redirect that discards everything else the user had filled in -
     * planning-submit.js submits this via fetch first and only lets the browser navigate on success;
     * the plain-form fallback (no JS) still works exactly as before, redirect and all.
     */
    @PostMapping("/plan-submit")
    public Object savePlanAndSubmit(@PathVariable UUID id, @RequestParam(required = false) String categoryText,
                                     @RequestParam(required = false) ContentPriority contentPriority,
                                     @RequestParam(required = false) String skuReference,
                                     @RequestParam(required = false) List<UUID> modelUserIds,
                                     @RequestParam(required = false) String folderLink,
                                     @RequestParam PlanningMode planningMode,
                                     @RequestParam LocalDate plannedLiveDate,
                                     @RequestParam(required = false) LocalDate shootDate,
                                     @RequestParam(required = false) LocalDate editDate,
                                     @RequestParam(required = false) String urgencyReason,
                                     @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                     jakarta.servlet.http.HttpServletRequest request) {
        List<TalentSelection> talentSelections = resolveTalentSelections(modelUserIds);
        try {
            planningService.savePlanAssignAndSubmit(principal.user(), id, categoryText, contentPriority, skuReference,
                    isSkuBlank(skuReference), talentSelections, folderLink, planningMode, plannedLiveDate, shootDate, editDate,
                    urgencyReason);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Plan saved and submitted for Planning Review.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ResponseEntity.status(e.getHttpStatus()).body(com.kcpc.mkt.common.error.ApiErrorResponse.of(
                        e.getErrorCode(), "Nothing was saved: " + e.getMessage(), request.getRequestURI()));
            }
            ra.addFlashAttribute("errorMessage", "Nothing was saved: " + e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/parameters")
    public String updateParameters(@PathVariable UUID id, @RequestParam(required = false) String categoryText,
                                    @RequestParam(required = false) ContentPriority contentPriority,
                                    @RequestParam(required = false) String skuReference,
                                    @RequestParam(required = false) List<UUID> modelUserIds,
                                    @RequestParam(required = false) String folderLink,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        List<TalentSelection> talentSelections = resolveTalentSelections(modelUserIds);
        try {
            planningService.updateParameters(principal.user(), id, categoryText, contentPriority, skuReference,
                    isSkuBlank(skuReference), talentSelections, folderLink);
            ra.addFlashAttribute("successMessage", "Planning parameters saved.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Authorized manual retry (PERM_13_FOLDER_LINK_MANAGE) of automatic Drive folder provisioning -
     * the same idempotent DriveProvisioningService#provision logic runs after every Content ID
     * creation, so retry can never create duplicate folders even after a partial prior failure. */
    @PostMapping("/drive/retry")
    public String retryDriveProvisioning(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                          RedirectAttributes ra) {
        try {
            authorizationService.requireAuthority(principal.user(), OperationalPermission.PERM_13_FOLDER_LINK_MANAGE,
                    LifecycleStage.ADMINISTRATIVE, requirePlan(id).getWorkflowInstance());
            if (!driveProvisioningService.isDriveIntegrationEnabled()) {
                ra.addFlashAttribute("infoMessage",
                        "Drive integration is not enabled on this server (app.drive.enabled=false) - nothing was attempted.");
                return redirect(id);
            }
            // "Accepted" is not the same as "succeeded" - retry() completes synchronously, so its
            // returned row's actual resulting status decides the flash outcome, never a blanket
            // "success" that would be shown identically whether Drive was reached at all.
            com.kcpc.mkt.drive.domain.ContentDriveProvisioning row = driveProvisioningService.retry(principal.user(), id);
            switch (row.getStatus()) {
                case SUCCEEDED -> ra.addFlashAttribute("successMessage", "Drive folders provisioned successfully.");
                case FAILED -> ra.addFlashAttribute("errorMessage", "Drive provisioning failed.");
                default -> ra.addFlashAttribute("infoMessage", "Drive provisioning retry started.");
            }
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Manual admin repair/relink (PERM_13_FOLDER_LINK_MANAGE): points the structured Drive record
     * at an admin-supplied folder (pasted URL or raw id) - the fallback path for when automatic
     * provisioning cannot be used or needs correcting. Updates the structured record first, then
     * resyncs the display folder_link - never the reverse. */
    @PostMapping("/drive/relink")
    public String relinkDriveFolder(@PathVariable UUID id, @RequestParam String rootFolderIdOrUrl,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            authorizationService.requireAuthority(principal.user(), OperationalPermission.PERM_13_FOLDER_LINK_MANAGE,
                    LifecycleStage.ADMINISTRATIVE, requirePlan(id).getWorkflowInstance());
            driveProvisioningService.relinkRootFolder(principal.user(), id, rootFolderIdOrUrl);
            ra.addFlashAttribute("successMessage", "Drive root folder relinked.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/schedule/standard")
    public String scheduleStandard(@PathVariable UUID id, @RequestParam LocalDate plannedLiveDate,
                                    @RequestParam(required = false) LocalDate shootDateOverride,
                                    @RequestParam(required = false) LocalDate editDateOverride,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            planningService.setStandardSchedule(principal.user(), id, plannedLiveDate, shootDateOverride, editDateOverride);
            ra.addFlashAttribute("successMessage", "Standard schedule saved.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/schedule/urgent")
    public String scheduleUrgent(@PathVariable UUID id, @RequestParam LocalDate plannedLiveDate,
                                  @RequestParam LocalDate shootDate, @RequestParam LocalDate editDate,
                                  @RequestParam String urgencyReason,
                                  @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            planningService.setUrgentSchedule(principal.user(), id, plannedLiveDate, shootDate, editDate, urgencyReason);
            ra.addFlashAttribute("successMessage", "Urgent schedule saved.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/outputs")
    public Object addOutput(@PathVariable UUID id, @RequestParam OutputType outputType,
                             @RequestParam(required = false) List<ReelType> reelTypes,
                             @RequestParam(required = false) String titleDescription,
                             @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                             @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                             jakarta.servlet.http.HttpServletRequest request) {
        try {
            List<PlannedOutput> created = planningService.addPlannedOutputs(principal.user(), id, outputType,
                    reelTypes, titleDescription);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok(PlannedOutputGroupResponse.of(created));
            }
            ra.addFlashAttribute("successMessage",
                    created.size() > 1 ? created.size() + " planned outputs added." : "Planned output added.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * ENG-053: keeps the first row seen per user id, dropping any later duplicates - used for the
     * execution-participant lists (which legitimately grow one row per execution cycle, e.g. a
     * Request Rework followed by a re-Start) so a person who shows up in several of those rows
     * still appears exactly once wherever the list is rendered as a set of people, not a history.
     */
    private static <T> List<T> dedupeByUser(List<T> rows, java.util.function.Function<T, User> userExtractor) {
        Map<UUID, T> byUserId = new java.util.LinkedHashMap<>();
        for (T row : rows) {
            byUserId.putIfAbsent(userExtractor.apply(row).getId(), row);
        }
        return new ArrayList<>(byUserId.values());
    }

    /** ERD-TBL-011: {@code groupId} is a Planned Output's reelGroupId - any member represents the whole group. */
    private UUID requireGroupRepresentative(UUID groupId) {
        return plannedOutputRepository.findByReelGroupId(groupId).stream().findFirst()
                .map(PlannedOutput::getId)
                .orElseThrow(() -> DomainException.notFound("Planned Output group not found: " + groupId));
    }

    /**
     * publication-scope.js submits the "+ Add Target" and per-chip Remove forms via {@code fetch}
     * (marked with this header) instead of a normal submit, so Publication Scope edits never
     * reload the page - the request/response shape is otherwise identical to the plain form POST,
     * which stays the fully-functional no-JS fallback.
     */
    private static boolean isAjax(String requestedWith) {
        return "fetch".equals(requestedWith);
    }

    /**
     * The assignment chip-pickers' no-JS batch-add form posts a plural field (e.g. {@code
     * cameramanUserIds}); the legacy singular field (e.g. {@code cameramanUserId}, still used by the REST
     * API and older callers) keeps working unchanged. Merges both into one de-duplicated list.
     */
    /**
     * The Lead {@code <select>}'s blank "None" option submits as an empty string, not an absent
     * parameter - a plain {@code @RequestParam(required = false) UUID} would fail converting ""
     * to a UUID instead of resolving to null, so the Lead endpoints take the raw String and parse
     * through here.
     */
    private static UUID parseOptionalUuid(String raw) {
        return (raw == null || raw.isBlank()) ? null : UUID.fromString(raw);
    }

    private static List<UUID> mergeIds(UUID single, List<UUID> plural) {
        java.util.LinkedHashSet<UUID> merged = new java.util.LinkedHashSet<>();
        if (single != null) {
            merged.add(single);
        }
        if (plural != null) {
            merged.addAll(plural);
        }
        return new java.util.ArrayList<>(merged);
    }

    private ResponseEntity<ApiErrorResponse> ajaxError(DomainException e, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiErrorResponse.of(e.getErrorCode(), e.getMessage(), request.getRequestURI()));
    }

    @PostMapping("/outputs/{groupId}/targets")
    public Object mapTargets(@PathVariable UUID id, @PathVariable UUID groupId,
                              @RequestParam(required = false) List<UUID> publicationTargetIds,
                              @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                              @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                              jakarta.servlet.http.HttpServletRequest request) {
        try {
            planningService.mapPublicationScope(principal.user(), requireGroupRepresentative(groupId),
                    publicationTargetIds == null ? List.of() : publicationTargetIds);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Publication scope updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * Reconciles the group's Reel Type membership to the submitted checkbox set (adding/removing
     * Planned Output rows as needed) instead of editing a single output in place, since the
     * grouped row's identity is the whole set of Reel Types, not any one member (see
     * {@link PlanningService#syncReelGroup}).
     */
    @PostMapping("/outputs/{groupId}/edit")
    public Object editOutput(@PathVariable UUID id, @PathVariable UUID groupId, @RequestParam OutputType outputType,
                              @RequestParam(required = false) List<ReelType> reelTypes,
                              @RequestParam(required = false) String titleDescription,
                              @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                              @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                              jakarta.servlet.http.HttpServletRequest request) {
        try {
            List<PlannedOutput> updated = planningService.syncReelGroup(principal.user(), groupId, outputType,
                    reelTypes, titleDescription);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok(PlannedOutputGroupResponse.of(updated));
            }
            ra.addFlashAttribute("successMessage", "Planned output updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/outputs/{groupId}/remove")
    public Object removeOutput(@PathVariable UUID id, @PathVariable UUID groupId,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                jakarta.servlet.http.HttpServletRequest request) {
        try {
            planningService.removeReelGroup(principal.user(), groupId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Planned output removed.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/outputs/{groupId}/targets/{targetId}/remove")
    public Object removeTarget(@PathVariable UUID id, @PathVariable UUID groupId, @PathVariable UUID targetId,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                jakarta.servlet.http.HttpServletRequest request) {
        try {
            planningService.unmapPublicationTarget(principal.user(), requireGroupRepresentative(groupId), targetId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Publication target removed.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/shooting-assignments")
    public Object assignCameraperson(@PathVariable UUID id, @RequestParam(required = false) UUID cameramanUserId,
                                      @RequestParam(required = false) List<UUID> cameramanUserIds,
                                      @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                      jakarta.servlet.http.HttpServletRequest request) {
        List<UUID> ids = mergeIds(cameramanUserId, cameramanUserIds);
        try {
            for (UUID uid : ids) {
                User cameraperson = userRepository.findById(uid)
                        .orElseThrow(() -> DomainException.notFound("User not found"));
                planningService.assignCameraperson(principal.user(), id, cameraperson);
            }
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage",
                    ids.size() > 1 ? "Camerapersons assigned." : "Cameraperson assigned.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Not in the frozen spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-035 (Model(s)-style chip-picker). */
    @PostMapping("/shooting-assignments/remove")
    public Object removeCameraperson(@PathVariable UUID id, @RequestParam UUID cameramanUserId,
                                      @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                      jakarta.servlet.http.HttpServletRequest request) {
        try {
            planningService.removeCameraperson(principal.user(), id, cameramanUserId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Cameraperson removed.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Not in the frozen spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-036 (Shoot Lead). */
    @PostMapping("/shooting-assignments/lead")
    public Object setShootLead(@PathVariable UUID id, @RequestParam(required = false) String cameramanUserId,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                jakarta.servlet.http.HttpServletRequest request) {
        UUID camId = parseOptionalUuid(cameramanUserId);
        try {
            planningService.setShootLead(principal.user(), id, camId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", camId != null ? "Shoot Lead set." : "Shoot Lead cleared.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * Single-button "Assign Cameraperson(s)" (ENG-041): replaces the old two-button
     * assign-then-set-lead flow the chip-picker used. One request assigns every newly-staged
     * Cameraperson and sets the Shoot Lead together, in one transaction (see
     * {@link com.kcpc.mkt.planning.service.PlanningService#assignShootTeam}). The older
     * {@code /shooting-assignments} and {@code /shooting-assignments/lead} endpoints above are
     * unchanged and still work independently for any other caller.
     */
    @PostMapping("/shooting-assignments/team")
    public Object assignShootTeam(@PathVariable UUID id, @RequestParam(required = false) List<UUID> cameramanUserIds,
                                   @RequestParam(required = false) String leadUserId,
                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                   @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                   jakarta.servlet.http.HttpServletRequest request) {
        UUID leadId = parseOptionalUuid(leadUserId);
        try {
            List<User> camerapersons = new ArrayList<>();
            if (cameramanUserIds != null) {
                for (UUID uid : cameramanUserIds) {
                    camerapersons.add(userRepository.findById(uid)
                            .orElseThrow(() -> DomainException.notFound("User not found")));
                }
            }
            planningService.assignShootTeam(principal.user(), id, camerapersons, leadId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Shoot team updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/planning-review/submit")
    public String submitPlanningReview(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            planningService.submitPlanningReview(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Submitted for Planning Review.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/planning-review/decision")
    public String decidePlanningReview(@PathVariable UUID id, @RequestParam boolean approve,
                                        @RequestParam(required = false) String reason,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            planningService.decidePlanningReview(principal.user(), id, approve, reason);
            ra.addFlashAttribute("successMessage", "Planning Review decision recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    // -------------------------------------------------------------- Shoot

    @PostMapping("/shooting/start")
    public String startShooting(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                 RedirectAttributes ra) {
        try {
            shootingService.startShooting(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Shoot started.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/shooting/review/submit")
    public String submitShootReview(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                     RedirectAttributes ra) {
        try {
            shootingService.submitShootReview(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Submitted for Shoot Review.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/shooting/review/decision")
    public String decideShootReview(@PathVariable UUID id, @RequestParam boolean approve,
                                     @RequestParam(required = false) String reason,
                                     @RequestParam(required = false) List<UUID> qualifyingRecipientUserIds,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            shootingService.decideShootReview(principal.user(), id, approve, reason, qualifyingRecipientUserIds);
            ra.addFlashAttribute("successMessage", "Shoot Review decision recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** ENG-046: shared Shoot Description, editable any time by whoever holds PERM_04. */
    @PostMapping("/shooting/description")
    public Object updateShootDescription(@PathVariable UUID id, @RequestParam(required = false) String description,
                                          @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                          @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                          jakarta.servlet.http.HttpServletRequest request) {
        try {
            planningService.updateShootDescription(principal.user(), id, description);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Shoot Description updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** ENG-046: Shoot's discussion thread - CEO/MM or the currently assigned Cameraperson team only. */
    @PostMapping("/shooting/comments")
    public Object addShootComment(@PathVariable UUID id, @RequestParam String commentText,
                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                   @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                   jakarta.servlet.http.HttpServletRequest request) {
        try {
            var comment = stageCommentService.addComment(principal.user(), id, LifecycleStage.SHOOTING, commentText);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok(Map.of(
                        "commentId", comment.getId(),
                        "commenterName", comment.getCommenter().getFullName(),
                        "createdAt", com.kcpc.mkt.common.util.DisplayTime.ist(comment.getCreatedAt()),
                        "commentText", comment.getCommentText()));
            }
            ra.addFlashAttribute("successMessage", "Comment added.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * ENG-050: edit/delete are stage-agnostic once you have the commentId (StageCommentService
     * looks the comment up directly and checks ownership, not the stage) - one shared body behind
     * three thin per-stage endpoints, matching this controller's existing stage-scoped route shape.
     */
    private Object doEditComment(UUID id, UUID commentId, String commentText, String requestedWith,
                                  KcpcUserPrincipal principal, RedirectAttributes ra,
                                  jakarta.servlet.http.HttpServletRequest request) {
        try {
            var comment = stageCommentService.editComment(principal.user(), id, commentId, commentText);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok(Map.of(
                        "commentText", comment.getCommentText(),
                        "editedAt", com.kcpc.mkt.common.util.DisplayTime.ist(comment.getEditedAt())));
            }
            ra.addFlashAttribute("successMessage", "Comment updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    private Object doDeleteComment(UUID id, UUID commentId, String requestedWith,
                                    KcpcUserPrincipal principal, RedirectAttributes ra,
                                    jakarta.servlet.http.HttpServletRequest request) {
        try {
            stageCommentService.deleteComment(principal.user(), id, commentId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Comment deleted.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/shooting/comments/{commentId}/edit")
    public Object editShootComment(@PathVariable UUID id, @PathVariable UUID commentId, @RequestParam String commentText,
                                    @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                    jakarta.servlet.http.HttpServletRequest request) {
        return doEditComment(id, commentId, commentText, requestedWith, principal, ra, request);
    }

    @PostMapping("/shooting/comments/{commentId}/delete")
    public Object deleteShootComment(@PathVariable UUID id, @PathVariable UUID commentId,
                                      @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                      jakarta.servlet.http.HttpServletRequest request) {
        return doDeleteComment(id, commentId, requestedWith, principal, ra, request);
    }

    // --------------------------------------------------------------- Edit

    @PostMapping("/editing/assignments")
    public Object assignEditor(@PathVariable UUID id, @RequestParam(required = false) UUID editorUserId,
                                @RequestParam(required = false) List<UUID> editorUserIds,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                jakarta.servlet.http.HttpServletRequest request) {
        List<UUID> ids = mergeIds(editorUserId, editorUserIds);
        try {
            for (UUID uid : ids) {
                User editor = userRepository.findById(uid).orElseThrow(() -> DomainException.notFound("User not found"));
                editingService.assignEditor(principal.user(), id, editor);
            }
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", ids.size() > 1 ? "Editors assigned." : "Editor assigned.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Not in the frozen spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-035 (Model(s)-style chip-picker). */
    @PostMapping("/editing/assignments/remove")
    public Object removeEditor(@PathVariable UUID id, @RequestParam UUID editorUserId,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                jakarta.servlet.http.HttpServletRequest request) {
        try {
            editingService.removeEditor(principal.user(), id, editorUserId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Editor removed.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Not in the frozen spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-036 (Edit Lead). */
    @PostMapping("/editing/assignments/lead")
    public Object setEditLead(@PathVariable UUID id, @RequestParam(required = false) String editorUserId,
                               @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                               @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                               jakarta.servlet.http.HttpServletRequest request) {
        UUID edId = parseOptionalUuid(editorUserId);
        try {
            editingService.setEditLead(principal.user(), id, edId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", edId != null ? "Edit Lead set." : "Edit Lead cleared.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * Single-button "Assign Editor(s)" (ENG-041): replaces the old two-button assign-then-set-lead
     * flow the chip-picker used. One request assigns every newly-staged Editor and sets the Edit
     * Lead together, in one transaction (see
     * {@link com.kcpc.mkt.production.service.EditingService#assignEditTeam}). The older
     * {@code /editing/assignments} and {@code /editing/assignments/lead} endpoints above are
     * unchanged and still work independently for any other caller.
     */
    @PostMapping("/editing/assignments/team")
    public Object assignEditTeam(@PathVariable UUID id, @RequestParam(required = false) List<UUID> editorUserIds,
                                  @RequestParam(required = false) String leadUserId,
                                  @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                  @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                  jakarta.servlet.http.HttpServletRequest request) {
        UUID leadId = parseOptionalUuid(leadUserId);
        try {
            List<User> editors = new ArrayList<>();
            if (editorUserIds != null) {
                for (UUID uid : editorUserIds) {
                    editors.add(userRepository.findById(uid).orElseThrow(() -> DomainException.notFound("User not found")));
                }
            }
            editingService.assignEditTeam(principal.user(), id, editors, leadId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Edit team updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/editing/start")
    public String startEditing(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                RedirectAttributes ra) {
        try {
            editingService.startEditing(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Editing started.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/editing/review/submit")
    public String submitEditReview(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                    RedirectAttributes ra) {
        try {
            editingService.submitEditReview(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Submitted for Edit Review.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/editing/review/decision")
    public String decideEditReview(@PathVariable UUID id, @RequestParam boolean approve,
                                    @RequestParam(required = false) String reason,
                                    @RequestParam(required = false) List<UUID> qualifyingRecipientUserIds,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            editingService.decideEditReview(principal.user(), id, approve, reason, qualifyingRecipientUserIds);
            ra.addFlashAttribute("successMessage", "Edit Review decision recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** ENG-046: shared Edit Description, editable any time by whoever holds PERM_06. */
    @PostMapping("/editing/description")
    public Object updateEditDescription(@PathVariable UUID id, @RequestParam(required = false) String description,
                                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                         @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                         jakarta.servlet.http.HttpServletRequest request) {
        try {
            editingService.updateEditDescription(principal.user(), id, description);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Edit Description updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** ENG-046: Edit's discussion thread - CEO/MM or the currently assigned Editor team only. */
    @PostMapping("/editing/comments")
    public Object addEditComment(@PathVariable UUID id, @RequestParam String commentText,
                                  @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                  @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                  jakarta.servlet.http.HttpServletRequest request) {
        try {
            var comment = stageCommentService.addComment(principal.user(), id, LifecycleStage.EDITING, commentText);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok(Map.of(
                        "commentId", comment.getId(),
                        "commenterName", comment.getCommenter().getFullName(),
                        "createdAt", com.kcpc.mkt.common.util.DisplayTime.ist(comment.getCreatedAt()),
                        "commentText", comment.getCommentText()));
            }
            ra.addFlashAttribute("successMessage", "Comment added.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/editing/comments/{commentId}/edit")
    public Object editEditComment(@PathVariable UUID id, @PathVariable UUID commentId, @RequestParam String commentText,
                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                   @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                   jakarta.servlet.http.HttpServletRequest request) {
        return doEditComment(id, commentId, commentText, requestedWith, principal, ra, request);
    }

    @PostMapping("/editing/comments/{commentId}/delete")
    public Object deleteEditComment(@PathVariable UUID id, @PathVariable UUID commentId,
                                     @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                     jakarta.servlet.http.HttpServletRequest request) {
        return doDeleteComment(id, commentId, requestedWith, principal, ra, request);
    }

    // --------------------------------------------------------- Publishing

    @PostMapping("/publishing/start")
    public String startPublishing(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            publishingService.startPublishing(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Publishing started.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Not in the frozen spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-035 (Model(s)-style chip-picker). */
    @PostMapping("/publishing-assignments")
    public Object assignPublisher(@PathVariable UUID id, @RequestParam(required = false) UUID publisherUserId,
                                   @RequestParam(required = false) List<UUID> publisherUserIds,
                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                   @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                   jakarta.servlet.http.HttpServletRequest request) {
        List<UUID> ids = mergeIds(publisherUserId, publisherUserIds);
        try {
            for (UUID uid : ids) {
                User publisher = userRepository.findById(uid).orElseThrow(() -> DomainException.notFound("User not found"));
                publishingService.assignPublisher(principal.user(), id, publisher);
            }
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", ids.size() > 1 ? "Publishers assigned." : "Publisher assigned.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** Not in the frozen spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-035 (Model(s)-style chip-picker). */
    @PostMapping("/publishing-assignments/remove")
    public Object removePublisher(@PathVariable UUID id, @RequestParam UUID publisherUserId,
                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                   @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                   jakarta.servlet.http.HttpServletRequest request) {
        try {
            publishingService.removePublisher(principal.user(), id, publisherUserId);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Publisher removed.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * ORIGINAL vs REPOST is derived by {@link PublishingService}, never accepted from this form -
     * a raw "Event Type" dropdown here would let a Publisher record a Repost as another ORIGINAL
     * (or vice versa) by picking wrong, and a reopened/repost cycle must never depend on a human
     * correctly remembering to select REPOST (see PublishingService#recordActualPublication's
     * no-eventType overload javadoc).
     */
    @PostMapping("/publishing/events")
    public String recordEvent(@PathVariable UUID id, @RequestParam UUID plannedOutputId,
                               @RequestParam UUID publicationTargetId,
                               @RequestParam String actualPublicationTimestamp, @RequestParam String evidenceUrl,
                               @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            Instant ts = LocalDate.parse(actualPublicationTimestamp).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
            publishingService.recordActualPublication(principal.user(), id, plannedOutputId, publicationTargetId,
                    ts, evidenceUrl);
            ra.addFlashAttribute("successMessage", "Actual publication recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * ENG-055/056: the Publishing execution checklist's "Submit Published Tasks" - one POST
     * covering every checked (Planned Output, Publication Target) row, each carrying its own
     * Evidence URL. Actual Publication Date is not user-entered - it's the moment this request is
     * submitted ({@code Instant.now()}, computed once so every row in the same batch shares the
     * exact same timestamp, matching "submitted together"), per the user's explicit "isko hata do,
     * wo by default current timestamp k according data lelega". The two remaining lists arrive
     * strictly in lockstep: the checklist's "hidden" `plannedOutputId`/`publicationTargetId`/
     * `evidenceUrl` inputs for a row are only ever `disabled` (and so only ever submitted) together,
     * toggled as one unit by publishing-checklist.js when that row's checkbox is (un)checked - never
     * independently - so index i in one list always corresponds to the same row as index i in the
     * other. Deliberately NOT one atomic transaction across the whole batch: each row is recorded
     * via the existing single-row {@link PublishingService#recordActualPublication}, independently -
     * a Publisher who successfully recorded 3 of 4 platforms shouldn't lose those 3 just because the
     * 4th had a bad URL, and the auto-complete-when-scope-resolved check inside that method already
     * fires correctly on whichever call happens to be the last one needed to complete the plan.
     */
    @PostMapping("/publishing/events/bulk")
    public String recordBulkEvents(@PathVariable UUID id,
                                    @RequestParam(required = false) List<UUID> plannedOutputIds,
                                    @RequestParam(required = false) List<UUID> publicationTargetIds,
                                    @RequestParam(required = false) List<String> evidenceUrls,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        if (plannedOutputIds == null || plannedOutputIds.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Select at least one task before submitting.");
            return redirect(id);
        }
        Instant ts = Instant.now();
        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < plannedOutputIds.size(); i++) {
            try {
                // Derived (ORIGINAL first time, REPOST once a live post already exists for this
                // pair - e.g. a reopened cycle) - never hardcoded, so this same bulk "Submit"
                // correctly records reposts too once buildPublishingChecklist's cycle-aware rows
                // make a reopened cycle's pending pairs selectable again.
                publishingService.recordActualPublication(principal.user(), id, plannedOutputIds.get(i),
                        publicationTargetIds.get(i), ts, evidenceUrls.get(i));
                succeeded++;
            } catch (DomainException e) {
                failures.add(e.getMessage());
            }
        }
        if (succeeded > 0) {
            ra.addFlashAttribute("successMessage", succeeded + " task(s) recorded as published.");
        }
        if (!failures.isEmpty()) {
            ra.addFlashAttribute("errorMessage", failures.size() + " task(s) could not be recorded: " + String.join("; ", failures));
        }
        return redirect(id);
    }

    @PostMapping("/publishing/targets/na")
    public String designateNa(@PathVariable UUID id, @RequestParam UUID plannedOutputId,
                               @RequestParam UUID publicationTargetId, @RequestParam String reason,
                               @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            publishingService.designateTargetNA(principal.user(), plannedOutputId, publicationTargetId, reason);
            ra.addFlashAttribute("successMessage", "Target marked N/A.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/publishing/targets/na/{naRecordId}/reverse")
    public String reverseNa(@PathVariable UUID id, @PathVariable UUID naRecordId, @RequestParam String reason,
                             @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            publishingService.reverseTargetNA(principal.user(), naRecordId, reason);
            ra.addFlashAttribute("successMessage", "Target N/A reversed.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/publishing/events/{eventId}/evidence-corrections")
    public String correctEvidence(@PathVariable UUID id, @PathVariable UUID eventId,
                                   @RequestParam String correctedEvidenceUrl, @RequestParam String correctionReason,
                                   @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            publishingService.correctEvidenceUrl(principal.user(), eventId, correctedEvidenceUrl, correctionReason);
            ra.addFlashAttribute("successMessage", "Evidence correction recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** ENG-046: shared Publishing Description, editable any time by native CEO/MM only (matching ENG-044). */
    @PostMapping("/publishing/description")
    public Object updatePublishingDescription(@PathVariable UUID id, @RequestParam(required = false) String description,
                                               @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                               @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                               jakarta.servlet.http.HttpServletRequest request) {
        try {
            publishingService.updatePublishingDescription(principal.user(), id, description);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
            ra.addFlashAttribute("successMessage", "Publishing Description updated.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /** ENG-046: Publishing's discussion thread - CEO/MM or the currently assigned Publisher team only. */
    @PostMapping("/publishing/comments")
    public Object addPublishingComment(@PathVariable UUID id, @RequestParam String commentText,
                                        @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                        jakarta.servlet.http.HttpServletRequest request) {
        try {
            var comment = stageCommentService.addComment(principal.user(), id, LifecycleStage.PUBLISHING, commentText);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok(Map.of(
                        "commentId", comment.getId(),
                        "commenterName", comment.getCommenter().getFullName(),
                        "createdAt", com.kcpc.mkt.common.util.DisplayTime.ist(comment.getCreatedAt()),
                        "commentText", comment.getCommentText()));
            }
            ra.addFlashAttribute("successMessage", "Comment added.");
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ajaxError(e, request);
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/publishing/comments/{commentId}/edit")
    public Object editPublishingComment(@PathVariable UUID id, @PathVariable UUID commentId, @RequestParam String commentText,
                                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                         @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                         jakarta.servlet.http.HttpServletRequest request) {
        return doEditComment(id, commentId, commentText, requestedWith, principal, ra, request);
    }

    @PostMapping("/publishing/comments/{commentId}/delete")
    public Object deletePublishingComment(@PathVariable UUID id, @PathVariable UUID commentId,
                                           @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                           @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra,
                                           jakarta.servlet.http.HttpServletRequest request) {
        return doDeleteComment(id, commentId, requestedWith, principal, ra, request);
    }

    // -------------------------------------------------------- Performance

    @PostMapping("/performance/{obligationId}/draft")
    public String saveScorecardDraft(@PathVariable UUID id, @PathVariable UUID obligationId,
                                      @RequestParam(required = false) Integer views3sec,
                                      @RequestParam(required = false, defaultValue = "false") boolean views3secIsNa,
                                      @RequestParam(required = false) Integer plays,
                                      @RequestParam(required = false) java.math.BigDecimal averageWatchTimeSeconds,
                                      @RequestParam(required = false, defaultValue = "false") boolean watchTimeIsNa,
                                      @RequestParam(required = false) java.math.BigDecimal videoLengthSeconds,
                                      @RequestParam(required = false, defaultValue = "false") boolean videoLengthIsNa,
                                      @RequestParam(required = false) Integer linkClicks,
                                      @RequestParam(required = false, defaultValue = "false") boolean clicksIsNa,
                                      @RequestParam(required = false) Integer impressions,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            performanceService.saveDraft(principal.user(), obligationId, views3sec, views3secIsNa, plays,
                    averageWatchTimeSeconds, watchTimeIsNa, videoLengthSeconds, videoLengthIsNa, linkClicks,
                    clicksIsNa, impressions);
            ra.addFlashAttribute("successMessage", "Scorecard draft saved.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/performance/{obligationId}/submit")
    public String submitScorecard(@PathVariable UUID id, @PathVariable UUID obligationId,
                                   @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            performanceService.submit(principal.user(), obligationId);
            ra.addFlashAttribute("successMessage", "Scorecard submitted.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/performance/scorecards/{scorecardId}/corrections")
    public String correctMetric(@PathVariable UUID id, @PathVariable UUID scorecardId,
                                 @RequestParam(required = false) Integer correctedViews3sec,
                                 @RequestParam(required = false) Integer correctedPlays,
                                 @RequestParam(required = false) java.math.BigDecimal correctedWatchTimeSeconds,
                                 @RequestParam(required = false) java.math.BigDecimal correctedVideoLengthSeconds,
                                 @RequestParam(required = false) Integer correctedLinkClicks,
                                 @RequestParam(required = false) Integer correctedImpressions,
                                 @RequestParam String correctionReason,
                                 @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            // Metric to correct is chosen one at a time in the UI (Correct-a-Metric dropdown), but
            // the params are all optional here (same contract as API-OP-046/CorrectScorecardMetricsRequest)
            // so a correction may still touch more than one metric if a future caller needs to.
            performanceService.correctMetrics(principal.user(), scorecardId, correctedViews3sec, null, correctedPlays,
                    correctedWatchTimeSeconds, null, correctedVideoLengthSeconds, null, correctedLinkClicks, null,
                    correctedImpressions, correctionReason);
            ra.addFlashAttribute("successMessage", "Metric correction recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    // --------------------------------------------------------- Admin actions

    @PostMapping("/reschedule")
    public String reschedule(@PathVariable UUID id, @RequestParam StageContext stageContext,
                              @RequestParam(required = false) LocalDate newShootDate,
                              @RequestParam(required = false) LocalDate newEditDate,
                              @RequestParam(required = false) LocalDate newLiveDate, @RequestParam String reason,
                              @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            adminActionService.reschedule(principal.user(), id, stageContext, newShootDate, newEditDate, newLiveDate, reason);
            ra.addFlashAttribute("successMessage", "Reschedule recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/reassign")
    public String reassign(@PathVariable UUID id, @RequestParam TaskStage taskStage,
                            @RequestParam List<UUID> newAssigneeUserIds, @RequestParam String reason,
                            @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            adminActionService.reassign(principal.user(), id, taskStage, newAssigneeUserIds, reason);
            ra.addFlashAttribute("successMessage", "Reassignment recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/cancel")
    public String cancel(@PathVariable UUID id, @RequestParam String reason,
                          @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            adminActionService.cancel(principal.user(), id, reason);
            ra.addFlashAttribute("successMessage", "Deliverable cancelled.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/hold")
    public String hold(@PathVariable UUID id, @RequestParam String reason,
                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate expectedResumeDate,
                        @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            ContentPlan plan = requirePlan(id);
            holdService.placeHold(principal.user(), plan.getWorkflowInstance(), reason, expectedResumeDate);
            ra.addFlashAttribute("successMessage", "Work placed on Hold.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/resume")
    public String resume(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                          RedirectAttributes ra) {
        try {
            ContentPlan plan = requirePlan(id);
            holdService.resume(principal.user(), plan.getWorkflowInstance());
            ra.addFlashAttribute("successMessage", "Work resumed.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * Publisher selection here is optional - reopening still works with none selected, exactly as
     * before, and Assign Publisher(s) remains fully available afterward from the Publishing tab
     * (the fresh-assignment gate from {@link AdminActionService#reopenCompleted} still applies
     * either way). Selecting one here just lets CEO/MM do both steps in one action instead of a
     * second trip to the Publishing tab. A plain String (not UUID) because the dropdown's blank
     * "no selection" option submits as an empty string, not an absent parameter.
     */
    @PostMapping("/reopen-publishing")
    public String reopenPublishing(@PathVariable UUID id, @RequestParam String reason,
                                    @RequestParam(required = false) String publisherUserId,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            adminActionService.reopenForPublishing(principal.user(), id, reason);
            if (publisherUserId != null && !publisherUserId.isBlank()) {
                User publisher = userRepository.findById(UUID.fromString(publisherUserId))
                        .orElseThrow(() -> DomainException.notFound("User not found: " + publisherUserId));
                publishingService.assignPublisher(principal.user(), id, publisher);
                ra.addFlashAttribute("successMessage", "Reopened for Publishing and Publisher assigned.");
            } else {
                ra.addFlashAttribute("successMessage", "Reopened for Publishing.");
            }
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/reopen-performance")
    public String reopenPerformance(@PathVariable UUID id, @RequestParam String reason,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            adminActionService.reopenForPerformance(principal.user(), id, reason);
            ra.addFlashAttribute("successMessage", "Reopened for Performance.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * ENG-055: the Publishing execution checklist's rows - one per real (Planned Output,
     * Publication Target) pair (ERD-TBL-011/ERD-TBL-021's mapping table, NOT grouped by
     * reelGroupId - each Reel Type of a REEL group has its own independent mapping rows, and each
     * Publication Target within a platform, e.g. two different Facebook Pages, is its own separate
     * task). A pair currently designated N/A (PublishingService's private latestNaAction logic,
     * duplicated here read-only since that method isn't exposed) is left off the checklist entirely
     * - it's excluded from "what's left to publish", not merely unchecked. A pair with a live
     * ORIGINAL event already recorded is included but marked completed (its evidence/date shown
     * read-only, no checkbox) so it can never be resubmitted from this screen.
     *
     * <p>Cycle-aware ({@code cycleStart} from {@link PublishingService#currentPublishingCycleStart}
     * - null for the very first, never-reopened cycle): once this deliverable has been Reopened for
     * Publishing, "completed" for a pair means an event recorded on-or-after the CURRENT cycle
     * started, not merely "an ORIGINAL exists somewhere in history" - otherwise every row would show
     * permanently Completed from the very first cycle onward and the checklist could never be used
     * to record the repost it exists to capture. First-time Publishing (cycleStart null) keeps the
     * exact original ORIGINAL-only rule, unchanged.
     */
    private List<PublishingChecklistRow> buildPublishingChecklist(List<PlannedOutput> outputs, Instant cycleStart) {
        List<PublishingChecklistRow> rows = new ArrayList<>();
        for (PlannedOutput output : outputs) {
            for (var mapping : mappingRepository.findByPlannedOutput(output)) {
                PublicationTarget target = mapping.getPublicationTarget();
                boolean isNa = naRecordRepository.findByPlannedOutput(output).stream()
                        .filter(r -> r.getPublicationTarget().getId().equals(target.getId()))
                        .max(Comparator.comparing(PublicationTargetNaRecord::getRecordedAt))
                        .map(r -> r.getActionType() == NaActionType.DESIGNATED)
                        .orElse(false);
                if (isNa) {
                    continue;
                }
                ActualPublicationEvent liveEvent;
                if (cycleStart == null) {
                    liveEvent = eventRepository.findByPlannedOutputAndEventType(output, PublicationEventType.ORIGINAL).stream()
                            .filter(e -> e.getPublicationTarget().getId().equals(target.getId()))
                            .findFirst().orElse(null);
                } else {
                    liveEvent = eventRepository.findByPlannedOutput(output).stream()
                            .filter(e -> e.getPublicationTarget().getId().equals(target.getId()))
                            .filter(e -> !e.getRecordedAt().isBefore(cycleStart))
                            .max(Comparator.comparing(ActualPublicationEvent::getRecordedAt))
                            .orElse(null);
                }
                rows.add(new PublishingChecklistRow(output, target, liveEvent));
            }
        }
        return rows;
    }

    /** ENG-055: read-only view-model for one Publishing checklist row - see {@link #buildPublishingChecklist}. */
    public static final class PublishingChecklistRow {
        private final PlannedOutput plannedOutput;
        private final PublicationTarget publicationTarget;
        private final ActualPublicationEvent completedEvent;

        PublishingChecklistRow(PlannedOutput plannedOutput, PublicationTarget publicationTarget, ActualPublicationEvent completedEvent) {
            this.plannedOutput = plannedOutput;
            this.publicationTarget = publicationTarget;
            this.completedEvent = completedEvent;
        }

        public PlannedOutput getPlannedOutput() {
            return plannedOutput;
        }

        public PublicationTarget getPublicationTarget() {
            return publicationTarget;
        }

        public boolean isCompleted() {
            return completedEvent != null;
        }

        public ActualPublicationEvent getCompletedEvent() {
            return completedEvent;
        }
    }
}
