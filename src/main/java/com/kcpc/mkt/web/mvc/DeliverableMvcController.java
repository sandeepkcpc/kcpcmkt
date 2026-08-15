package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.CreativePerformanceScorecardRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.performance.service.PerformanceService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPriority;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.ReelType;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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
    private final PerformanceObligationRepository obligationRepository;
    private final CreativePerformanceScorecardRepository scorecardRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final WorkHoldRecordRepository holdRecordRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    private final PlanningService planningService;
    private final ShootingService shootingService;
    private final EditingService editingService;
    private final PublishingService publishingService;
    private final PerformanceService performanceService;
    private final AdminActionService adminActionService;
    private final HoldService holdService;

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
                                     PerformanceObligationRepository obligationRepository,
                                     CreativePerformanceScorecardRepository scorecardRepository,
                                     ReviewCycleRepository reviewCycleRepository,
                                     WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                     WorkHoldRecordRepository holdRecordRepository, UserRepository userRepository,
                                     AuthorizationService authorizationService, PlanningService planningService,
                                     ShootingService shootingService, EditingService editingService,
                                     PublishingService publishingService, PerformanceService performanceService,
                                     AdminActionService adminActionService, HoldService holdService) {
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
        this.obligationRepository = obligationRepository;
        this.scorecardRepository = scorecardRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.holdRecordRepository = holdRecordRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.planningService = planningService;
        this.shootingService = shootingService;
        this.editingService = editingService;
        this.publishingService = publishingService;
        this.performanceService = performanceService;
        this.adminActionService = adminActionService;
        this.holdService = holdService;
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
    public String view(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        ContentPlan plan = requirePlan(id);
        User user = principal.user();
        WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
        model.addAttribute("plan", plan);
        model.addAttribute("status", status);
        model.addAttribute("user", user);

        predefinedRoleMarksRepository.findByContentPlan(plan).ifPresent(m -> model.addAttribute("marks", m));
        List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan(plan);
        model.addAttribute("outputs", outputs);
        model.addAttribute("outputTargetMappings", outputs.stream().collect(
                java.util.stream.Collectors.toMap(PlannedOutput::getId, mappingRepository::findByPlannedOutput)));
        model.addAttribute("outputNaRecords", outputs.stream().collect(
                java.util.stream.Collectors.toMap(PlannedOutput::getId, naRecordRepository::findByPlannedOutput)));
        model.addAttribute("talentEntries", talentEntryRepository.findByContentPlan(plan));
        model.addAttribute("activePublicationTargets", publicationTargetRepository.findByActiveTrue());
        model.addAttribute("outputTypes", OutputType.values());
        model.addAttribute("reelTypes", ReelType.values());
        model.addAttribute("priorities", ContentPriority.values());

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
                && plan.getPlannedLiveDate().isBefore(LocalDate.now())
                && status != WorkflowStatus.COMP && status != WorkflowStatus.CAN);

        // Pending review cycles per gate (drives read-only vs review-gate rendering).
        model.addAttribute("pendingPlanningReview", pendingCycle(plan, GateType.PLANNING_REVIEW));
        model.addAttribute("pendingShootReview", pendingCycle(plan, GateType.SHOOT_REVIEW));
        model.addAttribute("pendingEditReview", pendingCycle(plan, GateType.EDIT_REVIEW));

        boolean isPreparer = planningPreparerRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getPreparer().getId().equals(user.getId()));
        boolean isShootParticipant = shootingParticipantRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getCameraperson().getId().equals(user.getId()));
        boolean isEditParticipant = editingParticipantRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getEditor().getId().equals(user.getId()));

        // Permission-gated visibility flags - server re-validates unconditionally on every POST.
        model.addAttribute("canPlanningExecute", allowed(user, OperationalPermission.PERM_02_PLANNING_EXECUTION, LifecycleStage.PLANNING, plan));
        model.addAttribute("canAssignCameraperson", allowed(user, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, LifecycleStage.PLANNING, plan));
        boolean canDecidePlanning = allowed(user, OperationalPermission.PERM_03_PLANNING_REVIEW, LifecycleStage.PLANNING, plan) && !isPreparer;
        model.addAttribute("canDecidePlanningReview", canDecidePlanning);
        model.addAttribute("planningSelfReviewBlocked", isPreparer);

        model.addAttribute("isShootAssigneeOrNative", authorizationService.hasNativeAuthority(user)
                || shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getCameraperson().getId().equals(user.getId())));
        boolean canDecideShoot = allowed(user, OperationalPermission.PERM_05_SHOOT_REVIEW, LifecycleStage.SHOOTING, plan) && !isShootParticipant;
        model.addAttribute("canDecideShootReview", canDecideShoot);
        model.addAttribute("shootSelfReviewBlocked", isShootParticipant);

        model.addAttribute("canAssignEditor", allowed(user, OperationalPermission.PERM_06_EDIT_ASSIGNMENT, LifecycleStage.EDITING, plan));
        model.addAttribute("isEditAssigneeOrNative", authorizationService.hasNativeAuthority(user)
                || editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .anyMatch(a -> a.getEditor().getId().equals(user.getId())));
        boolean canDecideEdit = allowed(user, OperationalPermission.PERM_07_EDIT_REVIEW, LifecycleStage.EDITING, plan) && !isEditParticipant;
        model.addAttribute("canDecideEditReview", canDecideEdit);
        model.addAttribute("editSelfReviewBlocked", isEditParticipant);

        model.addAttribute("canPublishingExecute", allowed(user, OperationalPermission.PERM_08_PUBLISHING_EXECUTION, LifecycleStage.PUBLISHING, plan));
        model.addAttribute("canPerformanceUpdate", allowed(user, OperationalPermission.PERM_09_PERFORMANCE_UPDATE, LifecycleStage.PERFORMANCE, plan));
        model.addAttribute("canReschedule", allowed(user, OperationalPermission.PERM_10_RESCHEDULE, LifecycleStage.ADMINISTRATIVE, plan));
        model.addAttribute("canReassign", allowed(user, OperationalPermission.PERM_11_REASSIGN, LifecycleStage.ADMINISTRATIVE, plan));
        model.addAttribute("canCancel", allowed(user, OperationalPermission.PERM_12_CANCEL, LifecycleStage.ADMINISTRATIVE, plan));
        model.addAttribute("isNative", authorizationService.hasNativeAuthority(user));

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

    // ------------------------------------------------------------- Planning

    @PostMapping("/parameters")
    public String updateParameters(@PathVariable UUID id, @RequestParam(required = false) String categoryText,
                                    @RequestParam(required = false) ContentPriority contentPriority,
                                    @RequestParam(required = false) String skuReference,
                                    @RequestParam(required = false, defaultValue = "false") boolean skuNotApplicable,
                                    @RequestParam(required = false) String talentNamesCsv,
                                    @RequestParam(required = false) String folderLink,
                                    @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        List<String> talentNames = talentNamesCsv == null || talentNamesCsv.isBlank() ? List.of()
                : Arrays.stream(talentNamesCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        try {
            planningService.updateParameters(principal.user(), id, categoryText, contentPriority, skuReference,
                    skuNotApplicable, talentNames, folderLink);
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
    public String addOutput(@PathVariable UUID id, @RequestParam OutputType outputType,
                             @RequestParam(required = false) ReelType reelType,
                             @RequestParam(required = false) String titleDescription,
                             @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            planningService.addPlannedOutput(principal.user(), id, outputType, reelType, titleDescription);
            ra.addFlashAttribute("successMessage", "Planned output added.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/outputs/{outputId}/targets")
    public String mapTargets(@PathVariable UUID id, @PathVariable UUID outputId,
                              @RequestParam(required = false) List<UUID> publicationTargetIds,
                              @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            planningService.mapPublicationScope(principal.user(), outputId,
                    publicationTargetIds == null ? List.of() : publicationTargetIds);
            ra.addFlashAttribute("successMessage", "Publication scope updated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(id);
    }

    @PostMapping("/shooting-assignments")
    public String assignCameraperson(@PathVariable UUID id, @RequestParam UUID cameramanUserId,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            User cameraperson = userRepository.findById(cameramanUserId)
                    .orElseThrow(() -> DomainException.notFound("User not found"));
            planningService.assignCameraperson(principal.user(), id, cameraperson);
            ra.addFlashAttribute("successMessage", "Cameraperson assigned.");
        } catch (DomainException e) {
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

    // --------------------------------------------------------------- Edit

    @PostMapping("/editing/assignments")
    public String assignEditor(@PathVariable UUID id, @RequestParam UUID editorUserId,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            User editor = userRepository.findById(editorUserId).orElseThrow(() -> DomainException.notFound("User not found"));
            editingService.assignEditor(principal.user(), id, editor);
            ra.addFlashAttribute("successMessage", "Editor assigned.");
        } catch (DomainException e) {
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
