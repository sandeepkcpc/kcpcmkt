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
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.publishing.repository.PublicationTargetNaRecordRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.publishing.service.PublishingService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.StageContext;
import com.kcpc.mkt.workflow.domain.TaskStage;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
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
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final PerformanceObligationRepository obligationRepository;
    private final CreativePerformanceScorecardRepository scorecardRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final WorkHoldRecordRepository holdRecordRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    private final PlanningService planningService;
    private final ShootingService shootingService;
    private final EditingService editingService;
    private final PublishingService publishingService;
    private final PerformanceService performanceService;
    private final AdminActionService adminActionService;
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
                                     PublishingAssignmentRepository publishingAssignmentRepository,
                                     PerformanceObligationRepository obligationRepository,
                                     CreativePerformanceScorecardRepository scorecardRepository,
                                     ReviewCycleRepository reviewCycleRepository,
                                     WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                     WorkHoldRecordRepository holdRecordRepository, UserRepository userRepository,
                                     AuthorizationService authorizationService, ObjectMapper objectMapper,
                                     PlanningService planningService,
                                     ShootingService shootingService, EditingService editingService,
                                     PublishingService publishingService, PerformanceService performanceService,
                                     AdminActionService adminActionService, HoldService holdService,
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
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.obligationRepository = obligationRepository;
        this.scorecardRepository = scorecardRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.holdRecordRepository = holdRecordRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
        this.planningService = planningService;
        this.shootingService = shootingService;
        this.editingService = editingService;
        this.publishingService = publishingService;
        this.performanceService = performanceService;
        this.adminActionService = adminActionService;
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

    @GetMapping
    public String view(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal, Model model,
                        jakarta.servlet.http.HttpServletRequest request) {
        ContentPlan plan = requirePlan(id);
        User user = principal.user();
        WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
        model.addAttribute("plan", plan);
        model.addAttribute("status", status);
        model.addAttribute("user", user);
        model.addAttribute("today", LocalDate.now(BUSINESS_ZONE));

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
        model.addAttribute("outputNaRecords", outputs.stream().collect(
                java.util.stream.Collectors.toMap(PlannedOutput::getId, naRecordRepository::findByPlannedOutput)));
        model.addAttribute("talentEntries", talentEntryRepository.findByContentPlan(plan));
        model.addAttribute("modelUsers",
                userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Model"));
        model.addAttribute("camerapersonUsers",
                userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Camera Person"));
        model.addAttribute("videoEditorUsers",
                userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Video Editor"));
        model.addAttribute("publisherUsers",
                userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Publisher"));
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
        model.addAttribute("shootingParticipants", shootingParticipantRepository.findByContentPlan(plan));
        model.addAttribute("editingParticipants", editingParticipantRepository.findByContentPlan(plan));
        model.addAttribute("activeUsers", userRepository.findByActiveTrueOrderByFullNameAsc());

        model.addAttribute("events", eventRepository.findByContentPlan(plan));
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

        var timeline = new java.util.ArrayList<>(transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(plan.getWorkflowInstance()));
        java.util.Collections.reverse(timeline); // UI/UX §9.15: newest first.
        model.addAttribute("timeline", timeline);
        Optional<com.kcpc.mkt.workflow.domain.WorkHoldRecord> openHold =
                holdRecordRepository.findByWorkflowInstanceAndResumedAtIsNull(plan.getWorkflowInstance());
        model.addAttribute("openHold", openHold.orElse(null));
        model.addAttribute("delayed", plan.getPlannedLiveDate() != null
                && plan.getPlannedLiveDate().isBefore(LocalDate.now(BUSINESS_ZONE))
                && status != WorkflowStatus.COMP && status != WorkflowStatus.CAN);

        // Pending review cycles per gate (drives read-only vs review-gate rendering).
        model.addAttribute("pendingPlanningReview", pendingCycle(plan, GateType.PLANNING_REVIEW));
        model.addAttribute("pendingShootReview", pendingCycle(plan, GateType.SHOOT_REVIEW));
        model.addAttribute("pendingEditReview", pendingCycle(plan, GateType.EDIT_REVIEW));

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
        model.addAttribute("taskStages", TaskStage.values());
        model.addAttribute("eventTypes", PublicationEventType.values());

        return "deliverable-detail";
    }

    private com.kcpc.mkt.workflow.domain.ReviewCycle pendingCycle(ContentPlan plan, GateType gateType) {
        return reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), gateType)
                .stream().filter(c -> c.getDecidedAt() == null).findFirst().orElse(null);
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

    /** Model(s) is a dropdown of Users holding the "Model" Business Role, not free text. */
    private List<String> resolveModelNames(List<UUID> modelUserIds) {
        if (modelUserIds == null || modelUserIds.isEmpty()) {
            return List.of();
        }
        return modelUserIds.stream()
                .map(userId -> userRepository.findById(userId).map(User::getFullName).orElse(null))
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
        List<String> talentNames = resolveModelNames(modelUserIds);
        try {
            planningService.savePlan(principal.user(), id, categoryText, contentPriority, skuReference,
                    isSkuBlank(skuReference), talentNames, folderLink, planningMode, plannedLiveDate, shootDate, editDate,
                    urgencyReason);
            ra.addFlashAttribute("successMessage", "Plan saved.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    /**
     * ENG-045: Planning Workspace UI has one form and one button. Planning Details is
     * {@code <form id="planning-details-form">} with no submit button of its own; the single
     * "Submit for Planning Review" button at the bottom of the page references it via the HTML5
     * {@code form="..."} attribute, and now ALSO carries the Cameraperson(s)/Shoot Lead fields (no
     * separate "Assign Cameraperson(s)" button anymore - see the Shoot Assignment section, which
     * moved into this same form). One click saves Planning Details, creates/updates the Shoot
     * Assignment, and submits for review, all in ONE transaction
     * ({@link PlanningService#savePlanAssignAndSubmit}) - if any step fails (including the
     * submit-readiness check), nothing is saved at all, not even the Planning Details fields. This
     * replaces the previous two-transaction design (save always persisted even when the
     * submit-readiness check failed) per the user's explicit request for full atomicity.
     */
    @PostMapping("/plan-submit")
    public String savePlanAndSubmit(@PathVariable UUID id, @RequestParam(required = false) String categoryText,
                                     @RequestParam(required = false) ContentPriority contentPriority,
                                     @RequestParam(required = false) String skuReference,
                                     @RequestParam(required = false) List<UUID> modelUserIds,
                                     @RequestParam(required = false) String folderLink,
                                     @RequestParam PlanningMode planningMode,
                                     @RequestParam LocalDate plannedLiveDate,
                                     @RequestParam(required = false) LocalDate shootDate,
                                     @RequestParam(required = false) LocalDate editDate,
                                     @RequestParam(required = false) String urgencyReason,
                                     @RequestParam(required = false) List<UUID> cameramanUserIds,
                                     @RequestParam(required = false) String leadUserId,
                                     @RequestParam(required = false) String shootDescription,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        List<String> talentNames = resolveModelNames(modelUserIds);
        try {
            ContentPlan plan = requirePlan(id);
            boolean touchShootAssignment = allowed(principal.user(), OperationalPermission.PERM_04_SHOOT_ASSIGNMENT,
                    LifecycleStage.PLANNING, plan);
            List<User> camerapersons = new ArrayList<>();
            UUID leadId = null;
            if (touchShootAssignment) {
                leadId = parseOptionalUuid(leadUserId);
                if (cameramanUserIds != null) {
                    for (UUID uid : cameramanUserIds) {
                        camerapersons.add(userRepository.findById(uid)
                                .orElseThrow(() -> DomainException.notFound("User not found")));
                    }
                }
            }
            planningService.savePlanAssignAndSubmit(principal.user(), id, categoryText, contentPriority, skuReference,
                    isSkuBlank(skuReference), talentNames, folderLink, planningMode, plannedLiveDate, shootDate, editDate,
                    urgencyReason, camerapersons, leadId, touchShootAssignment, shootDescription);
            ra.addFlashAttribute("successMessage", "Plan saved and submitted for Planning Review.");
        } catch (DomainException e) {
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
        List<String> talentNames = resolveModelNames(modelUserIds);
        try {
            planningService.updateParameters(principal.user(), id, categoryText, contentPriority, skuReference,
                    isSkuBlank(skuReference), talentNames, folderLink);
            ra.addFlashAttribute("successMessage", "Planning parameters saved.");
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

    @PostMapping("/publishing/events")
    public String recordEvent(@PathVariable UUID id, @RequestParam UUID plannedOutputId,
                               @RequestParam UUID publicationTargetId, @RequestParam PublicationEventType eventType,
                               @RequestParam String actualPublicationTimestamp, @RequestParam String evidenceUrl,
                               @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            Instant ts = LocalDate.parse(actualPublicationTimestamp).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
            publishingService.recordActualPublication(principal.user(), id, plannedOutputId, publicationTargetId,
                    eventType, ts, evidenceUrl);
            ra.addFlashAttribute("successMessage", "Actual publication recorded.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
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
                                 @RequestParam(required = false) Integer correctedLinkClicks,
                                 @RequestParam String correctionReason,
                                 @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            performanceService.correctMetrics(principal.user(), scorecardId, null, null, null, null, null, null, null,
                    correctedLinkClicks, null, null, correctionReason);
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
                        @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            ContentPlan plan = requirePlan(id);
            holdService.placeHold(principal.user(), plan.getWorkflowInstance(), reason);
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

    @PostMapping("/reopen-publishing")
    public String reopenPublishing(@PathVariable UUID id, @RequestParam String reason,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            adminActionService.reopenForPublishing(principal.user(), id, reason);
            ra.addFlashAttribute("successMessage", "Reopened for Publishing.");
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
}
