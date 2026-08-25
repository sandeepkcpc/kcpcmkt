package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.ApiErrorResponse;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.domain.IdeaReviewDecision;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.idea.service.IdeaService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.ReelType;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.planning.repository.PlanningPreparerRepository;
import com.kcpc.mkt.planning.service.PlanningService;
import com.kcpc.mkt.production.domain.EditingExecutionParticipant;
import com.kcpc.mkt.production.domain.ShootingExecutionParticipant;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.EditingExecutionParticipantRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingExecutionParticipantRepository;
import com.kcpc.mkt.production.service.EditingService;
import com.kcpc.mkt.production.service.ShootingService;
import com.kcpc.mkt.reporting.dto.PipelineRow;
import com.kcpc.mkt.reporting.service.PipelineDashboardService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.web.mvc.dto.IdeaQueueRow;
import com.kcpc.mkt.web.mvc.dto.ReviewPlanItem;
import com.kcpc.mkt.web.mvc.dto.ShootFeedbackEntry;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manager Reviews Workspace: one page, four pending-review queues (Ideas/Planning/Shoot/Edit - no
 * Publishing tab, it has no review gate). Every decision here calls the EXACT SAME
 * application/service-layer methods the existing Idea Detail (IdeaMvcController) and Content
 * Detail (DeliverableMvcController) pages already use - this controller only adds a second,
 * AJAX-first entry point onto those same services; it never re-implements a workflow rule. The
 * existing `/app/ideas/{id}/review` and `/app/deliverables/{id}/.../decision` endpoints, and the
 * pages that use them, are completely untouched.
 */
@Controller
@RequestMapping("/app/reviews")
public class ReviewsMvcController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 25, 50);

    private final IdeaRepository ideaRepository;
    private final IdeaService ideaService;
    private final ContentPlanRepository contentPlanRepository;
    private final PipelineDashboardService pipelineDashboardService;
    private final PlanningService planningService;
    private final ShootingService shootingService;
    private final EditingService editingService;
    private final AuthorizationService authorizationService;
    private final com.kcpc.mkt.workflow.repository.ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final PlanningPreparerRepository planningPreparerRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final ShootingExecutionParticipantRepository shootingParticipantRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final EditingExecutionParticipantRepository editingParticipantRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final com.kcpc.mkt.discussion.service.StageCommentService stageCommentService;
    private final com.kcpc.mkt.identity.repository.UserRepository userRepository;

    public ReviewsMvcController(IdeaRepository ideaRepository, IdeaService ideaService,
                                 ContentPlanRepository contentPlanRepository, PipelineDashboardService pipelineDashboardService,
                                 PlanningService planningService, ShootingService shootingService, EditingService editingService,
                                 AuthorizationService authorizationService,
                                 com.kcpc.mkt.workflow.repository.ReviewCycleRepository reviewCycleRepository,
                                 WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                 PlanningPreparerRepository planningPreparerRepository,
                                 ShootingAssignmentRepository shootingAssignmentRepository,
                                 ShootingExecutionParticipantRepository shootingParticipantRepository,
                                 EditingAssignmentRepository editingAssignmentRepository,
                                 EditingExecutionParticipantRepository editingParticipantRepository,
                                 PlannedOutputRepository plannedOutputRepository,
                                 ContentPlanTalentEntryRepository talentEntryRepository,
                                 com.kcpc.mkt.discussion.service.StageCommentService stageCommentService,
                                 com.kcpc.mkt.identity.repository.UserRepository userRepository) {
        this.ideaRepository = ideaRepository;
        this.ideaService = ideaService;
        this.contentPlanRepository = contentPlanRepository;
        this.pipelineDashboardService = pipelineDashboardService;
        this.planningService = planningService;
        this.shootingService = shootingService;
        this.editingService = editingService;
        this.authorizationService = authorizationService;
        this.reviewCycleRepository = reviewCycleRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.planningPreparerRepository = planningPreparerRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.shootingParticipantRepository = shootingParticipantRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.editingParticipantRepository = editingParticipantRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.stageCommentService = stageCommentService;
        this.userRepository = userRepository;
    }

    private static boolean isAjax(String requestedWith) {
        return "fetch".equals(requestedWith);
    }

    // ------------------------------------------------------------------ GET

    @GetMapping
    public String reviews(@RequestParam(required = false, defaultValue = "ideas") String tab,
                           @RequestParam(required = false, defaultValue = "pending") String ideaView,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateTo,
                           @RequestParam(required = false) String sort,
                           @RequestParam(required = false) String mode,
                           @RequestParam(required = false) String priority,
                           @RequestParam(required = false, defaultValue = "false") boolean delayedOnly,
                           @RequestParam(required = false) UUID selectedId,
                           @RequestParam(required = false, defaultValue = "1") int page,
                           @RequestParam(required = false, defaultValue = "10") int pageSize,
                           @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                           @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        // Spec §16.3: an EMPLOYEE holding any review permission (PERM_01/03/05/07) can reach
        // Reviews now, not only CEO/MM - but only the specific tab(s) their own permission covers
        // ("Render only authorized tabs. Unauthorized direct URLs must be denied server-side.").
        // Scope-agnostic (hasAnyActiveGrant, not a specific stage/item) is deliberate here - this
        // is nav-level tab reachability, not the actual review-decision authorization, which each
        // buildXxxTab() call below still independently enforces with the real stage/item context.
        boolean nativeAuthority = authorizationService.hasNativeAuthority(user);
        boolean canViewIdeasTab = nativeAuthority || authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_01_IDEA_REVIEW);
        boolean canViewPlanningTab = nativeAuthority || authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_03_PLANNING_REVIEW);
        boolean canViewShootTab = nativeAuthority || authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_05_SHOOT_REVIEW);
        boolean canViewEditTab = nativeAuthority || authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_07_EDIT_REVIEW);
        if (user.resolvedAccessClass() == AccessClass.EMPLOYEE
                && !(canViewIdeasTab || canViewPlanningTab || canViewShootTab || canViewEditTab)) {
            return "redirect:/app/home";
        }
        // A direct URL to a tab this viewer isn't authorized for falls back to one they are,
        // rather than silently rendering it.
        boolean requestedTabAllowed = switch (tab) {
            case "planning" -> canViewPlanningTab;
            case "shoot" -> canViewShootTab;
            case "edit" -> canViewEditTab;
            default -> canViewIdeasTab;
        };
        if (!requestedTabAllowed) {
            tab = canViewIdeasTab ? "ideas" : canViewPlanningTab ? "planning" : canViewShootTab ? "shoot" : "edit";
        }
        model.addAttribute("user", user);
        model.addAttribute("activeTab", tab);
        model.addAttribute("canViewIdeasTab", canViewIdeasTab);
        model.addAttribute("canViewPlanningTab", canViewPlanningTab);
        model.addAttribute("canViewShootTab", canViewShootTab);
        model.addAttribute("canViewEditTab", canViewEditTab);

        List<Idea> pendingIdeas = ideaRepository.findByWorkflowInstance_CurrentStatusCodeOrderBySubmittedAtAsc(WorkflowStatus.PA);
        List<ContentPlan> allPlans = contentPlanRepository.findAllWithPreparedByOrderByCreatedAtDesc();
        model.addAttribute("ideasPendingCount", pendingIdeas.size());
        model.addAttribute("planningPendingCount", countByStatus(allPlans, WorkflowStatus.PLRV));
        model.addAttribute("shootPendingCount", countByStatus(allPlans, WorkflowStatus.SRV));
        model.addAttribute("editPendingCount", countByStatus(allPlans, WorkflowStatus.ERV));

        switch (tab) {
            case "planning" -> buildPlanTab(user, allPlans, WorkflowStatus.PLRV, GateType.PLANNING_REVIEW,
                    OperationalPermission.PERM_03_PLANNING_REVIEW, q, mode, priority, false, selectedId, page, pageSize, model);
            case "shoot" -> buildPlanTab(user, allPlans, WorkflowStatus.SRV, GateType.SHOOT_REVIEW,
                    OperationalPermission.PERM_05_SHOOT_REVIEW, q, null, null, delayedOnly, selectedId, page, pageSize, model);
            case "edit" -> buildPlanTab(user, allPlans, WorkflowStatus.ERV, GateType.EDIT_REVIEW,
                    OperationalPermission.PERM_07_EDIT_REVIEW, q, null, null, delayedOnly, selectedId, page, pageSize, model);
            default -> buildIdeasTab(user, pendingIdeas, ideaView, q, dateFrom, dateTo, sort, selectedId, page, pageSize, model);
        }
        return isAjax(requestedWith) ? "reviews-content" : "reviews";
    }

    // --------------------------------------------------------------- Ideas

    private void buildIdeasTab(User user, List<Idea> pendingIdeas, String ideaView, String q, LocalDate dateFrom, LocalDate dateTo,
                                String sort, UUID selectedId, int page, int pageSize, Model model) {
        // "Retained" sub-view (added after Idea Queue was removed from CEO/MM nav): a RETAIN
        // decision moves an idea's workflow status to RET (dormant), not a label while it stays
        // PA - so it drops out of the pending queue above entirely. With no more Idea Queue screen
        // to browse/reopen a retained idea from, this sub-view inside Reviews -> Ideas is now the
        // only place to find one, using the exact same queue/detail split, row DTO and history
        // rendering as the pending view - only the source list, status, and the bottom action
        // (Reopen instead of Approve/Reject/Retain) differ.
        boolean retainedView = "retained".equals(ideaView);
        List<Idea> retainedIdeas = ideaRepository.findByWorkflowInstance_CurrentStatusCodeOrderBySubmittedAtAsc(WorkflowStatus.RET);
        model.addAttribute("ideaView", retainedView ? "retained" : "pending");
        model.addAttribute("ideasRetainedCount", retainedIdeas.size());
        WorkflowStatus rowStatus = retainedView ? WorkflowStatus.RET : WorkflowStatus.PA;
        List<Idea> pending = retainedView ? retainedIdeas : pendingIdeas;

        List<UUID> instanceIds = pending.stream().map(i -> i.getWorkflowInstance().getId()).toList();
        Map<UUID, List<WorkflowTransitionHistory>> lifecycleByInstance = transitionHistoryRepository
                .findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(instanceIds).stream()
                .filter(t -> IdeaMvcController.IDEA_LIFECYCLE_TRIGGER_COMMANDS.contains(t.getTriggerCommand()))
                .collect(Collectors.groupingBy(t -> t.getWorkflowInstance().getId()));

        List<IdeaQueueRow> allRows = pending.stream().map(idea -> {
            List<WorkflowTransitionHistory> lifecycle = lifecycleByInstance
                    .getOrDefault(idea.getWorkflowInstance().getId(), List.of());
            String latestTrigger = lifecycle.isEmpty() ? null : lifecycle.get(lifecycle.size() - 1).getTriggerCommand();
            String label = IdeaMvcController.statusLabel(rowStatus, latestTrigger);
            boolean canAct = retainedView ? canReopenIdea(user, idea) : canDecideIdea(user, idea);
            return new IdeaQueueRow(idea.getId(), idea.getBusinessIdeaCode(), idea.getTitle(),
                    idea.getSubmittedBy().getId(), idea.getSubmittedBy().getFullName(), idea.getSubmittedAt(),
                    label, IdeaMvcController.statusCssClass(label), canAct);
        }).toList();

        String qLower = q == null ? "" : q.trim().toLowerCase();
        Instant fromInstant = dateFrom == null ? null : dateFrom.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant toInstant = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        List<IdeaQueueRow> filtered = allRows.stream()
                .filter(r -> qLower.isEmpty() || r.getTitle().toLowerCase().contains(qLower)
                        || r.getBusinessIdeaCode().toLowerCase().contains(qLower))
                .filter(r -> fromInstant == null || (r.getSubmittedAt() != null && !r.getSubmittedAt().isBefore(fromInstant)))
                .filter(r -> toInstant == null || (r.getSubmittedAt() != null && r.getSubmittedAt().isBefore(toInstant)))
                .collect(Collectors.toCollection(ArrayList::new));

        // ENG: sorting deliberately limited to Idea ID / Submitted On, matching the same
        // "do not add sorting everywhere" rule ENG-088's Idea Queue already follows.
        Comparator<IdeaQueueRow> comparator = switch (sort == null ? "DATE_DESC" : sort) {
            case "ID_ASC" -> Comparator.comparing(IdeaQueueRow::getBusinessIdeaCode);
            case "ID_DESC" -> Comparator.comparing(IdeaQueueRow::getBusinessIdeaCode).reversed();
            case "DATE_ASC" -> Comparator.comparing(IdeaQueueRow::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(IdeaQueueRow::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        };
        filtered.sort(comparator);

        List<IdeaQueueRow> pageRows = paginate(filtered, page, pageSize, model);
        model.addAttribute("queueRows", pageRows);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("sort", sort == null ? "DATE_DESC" : sort);

        UUID chosen = filtered.stream().map(IdeaQueueRow::getIdeaId).anyMatch(id -> id.equals(selectedId)) ? selectedId
                : (filtered.isEmpty() ? null : filtered.get(0).getIdeaId());
        model.addAttribute("selectedId", chosen);
        if (chosen == null) {
            return;
        }
        Idea idea = pending.stream().filter(i -> i.getId().equals(chosen)).findFirst().orElse(null);
        if (idea == null) {
            return;
        }
        List<WorkflowTransitionHistory> lifecycle = ideaLifecycleHistoryDesc(idea);
        String latestTrigger = lifecycle.isEmpty() ? null : lifecycle.get(0).getTriggerCommand();
        String label = IdeaMvcController.statusLabel(idea.getWorkflowInstance().getCurrentStatusCode(), latestTrigger);
        model.addAttribute("selectedIdea", idea);
        model.addAttribute("ideaStatusLabel", label);
        model.addAttribute("ideaStatusCssClass", IdeaMvcController.statusCssClass(label));
        model.addAttribute("ideaStatusHistory", IdeaMvcController.toHistoryEvents(lifecycle));
        model.addAttribute("canDecideSelected", retainedView ? canReopenIdea(user, idea) : canDecideIdea(user, idea));
    }

    private List<WorkflowTransitionHistory> ideaLifecycleHistoryDesc(Idea idea) {
        List<WorkflowTransitionHistory> history = new ArrayList<>(transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(idea.getWorkflowInstance()));
        history.removeIf(t -> !IdeaMvcController.IDEA_LIFECYCLE_TRIGGER_COMMANDS.contains(t.getTriggerCommand()));
        Collections.reverse(history);
        return history;
    }

    /** Mirrors IdeaMvcController#canDecide exactly (same authorizationService call, same self-conflict rule). */
    private boolean canDecideIdea(User currentUser, Idea idea) {
        try {
            var grant = authorizationService.requireAuthority(currentUser, OperationalPermission.PERM_01_IDEA_REVIEW,
                    LifecycleStage.IDEA_MANAGEMENT, idea.getWorkflowInstance());
            boolean selfConflict = grant.isPresent() && currentUser.getId().equals(idea.getSubmittedBy().getId());
            return !selfConflict;
        } catch (DomainException e) {
            return false;
        }
    }

    @PostMapping("/ideas/{id}/decision")
    public ResponseEntity<?> decideIdea(@PathVariable UUID id, @RequestParam IdeaReviewDecision decision,
                                         @RequestParam(required = false) String reason,
                                         @RequestParam(required = false) BigDecimal cameramanMark,
                                         @RequestParam(required = false) BigDecimal editorMark,
                                         @AuthenticationPrincipal KcpcUserPrincipal principal, HttpServletRequest request) {
        try {
            ideaService.decide(principal.user(), id, decision, reason, cameramanMark, editorMark);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (DomainException e) {
            return ajaxError(e, request);
        }
    }

    /** Reviews -> Ideas "Retained" sub-view's only action - same {@link IdeaService#reopen} the
     *  REST API already exposed, just now reachable from the UI now that the Idea Queue screen
     *  (the old way to browse/reopen a retained idea) is no longer in CEO/MM navigation. */
    @PostMapping("/ideas/{id}/reopen")
    public ResponseEntity<?> reopenIdea(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                         HttpServletRequest request) {
        try {
            ideaService.reopen(principal.user(), id);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (DomainException e) {
            return ajaxError(e, request);
        }
    }

    /** Mirrors {@link IdeaService#reopen}'s own authorization check exactly (same permission,
     *  same stage) - unlike {@link #canDecideIdea}, reopening is administrative rather than a
     *  review judgment, so it carries no self-conflict restriction (the service itself has none). */
    private boolean canReopenIdea(User currentUser, Idea idea) {
        try {
            authorizationService.requireAuthority(currentUser, OperationalPermission.PERM_01_IDEA_REVIEW,
                    LifecycleStage.IDEA_MANAGEMENT, idea.getWorkflowInstance());
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    // ----------------------------------------------------- Planning/Shoot/Edit (shared shape)

    private void buildPlanTab(User user, List<ContentPlan> allPlans, WorkflowStatus pendingStatus, GateType gateType,
                               OperationalPermission reviewPermission, String q, String modeFilter, String priorityFilter,
                               boolean delayedOnly, UUID selectedId, int page, int pageSize, Model model) {
        List<ContentPlan> pendingPlans = allPlans.stream()
                .filter(p -> p.getWorkflowInstance().getCurrentStatusCode() == pendingStatus)
                .toList();
        List<PipelineRow> rows = pipelineDashboardService.buildRows(pendingPlans);
        List<ReviewPlanItem> allItems = new ArrayList<>(pendingPlans.size());
        for (int i = 0; i < pendingPlans.size(); i++) {
            allItems.add(new ReviewPlanItem(pendingPlans.get(i), rows.get(i)));
        }

        String qLower = q == null ? "" : q.trim().toLowerCase();
        List<ReviewPlanItem> filtered = allItems.stream()
                .filter(it -> qLower.isEmpty() || it.getRow().getContentId().toLowerCase().contains(qLower)
                        || (it.getRow().getIdeaTitle() != null && it.getRow().getIdeaTitle().toLowerCase().contains(qLower)))
                .filter(it -> modeFilter == null || modeFilter.isBlank() || "ALL".equalsIgnoreCase(modeFilter)
                        || (it.getPlan().getPlanningMode() != null && it.getPlan().getPlanningMode().name().equalsIgnoreCase(modeFilter)))
                .filter(it -> priorityFilter == null || priorityFilter.isBlank() || "ALL".equalsIgnoreCase(priorityFilter)
                        || priorityFilter.equalsIgnoreCase(it.getRow().getPriority()))
                .filter(it -> !delayedOnly || it.getRow().isDelayed())
                .collect(Collectors.toCollection(ArrayList::new));

        List<ReviewPlanItem> pageItems = paginate(filtered, page, pageSize, model);
        model.addAttribute("queueRows", pageItems);
        // Queue's own "Submitted On" column (Shoot/Edit tabs) - only for the current page's rows,
        // not the whole pending set, to keep this bounded to page size regardless of queue length.
        Map<UUID, Instant> submittedAtByPlanId = new LinkedHashMap<>();
        for (ReviewPlanItem it : pageItems) {
            submittedAtByPlanId.put(it.getPlan().getId(), pendingSubmittedAt(it.getPlan(), gateType));
        }
        model.addAttribute("submittedAtByPlanId", submittedAtByPlanId);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("modeFilter", modeFilter == null || modeFilter.isBlank() ? "ALL" : modeFilter);
        model.addAttribute("priorityFilter", priorityFilter == null || priorityFilter.isBlank() ? "ALL" : priorityFilter);
        model.addAttribute("delayedOnly", delayedOnly);

        UUID chosen = filtered.stream().map(it -> it.getPlan().getId()).anyMatch(id -> id.equals(selectedId)) ? selectedId
                : (filtered.isEmpty() ? null : filtered.get(0).getPlan().getId());
        model.addAttribute("selectedId", chosen);
        if (chosen == null) {
            return;
        }
        ReviewPlanItem item = filtered.stream().filter(it -> it.getPlan().getId().equals(chosen)).findFirst().orElseThrow();
        ContentPlan plan = item.getPlan();
        model.addAttribute("selectedItem", item);

        boolean nativeAuthority = authorizationService.hasNativeAuthority(user);
        boolean selfBlocked = isParticipant(plan, gateType, user) && !nativeAuthority;
        boolean canDecide = allowed(user, reviewPermission, lifecycleStageFor(gateType), plan) && !selfBlocked;
        model.addAttribute("canDecideSelected", canDecide);
        model.addAttribute("selfReviewBlocked", selfBlocked);
        model.addAttribute("reviewHistory", reviewHistory(plan, gateType));
        model.addAttribute("pendingSubmittedAt", pendingSubmittedAt(plan, gateType));

        if (gateType == GateType.PLANNING_REVIEW) {
            List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan(plan);
            Map<OutputType, Long> countsByType = outputs.stream()
                    .collect(Collectors.groupingBy(PlannedOutput::getOutputType, Collectors.counting()));
            List<ReelType> reelTypes = outputs.stream().map(PlannedOutput::getReelType)
                    .filter(Objects::nonNull).distinct().sorted().toList();
            model.addAttribute("outputCountsByType", countsByType);
            model.addAttribute("outputReelTypes", reelTypes);
            model.addAttribute("talentEntries", talentEntryRepository.findByContentPlan(plan));
            model.addAttribute("shootingAssignments", shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan));
        } else if (gateType == GateType.SHOOT_REVIEW) {
            model.addAttribute("shootingAssignments", shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan));
            model.addAttribute("talentEntries", talentEntryRepository.findByContentPlan(plan));
            model.addAttribute("qualifyingParticipants", dedupeByUser(
                    shootingParticipantRepository.findByContentPlan(plan), ShootingExecutionParticipant::getCameraperson));
            // Manager review-consistency fix: Reviews -> Shoot must show the exact same Shoot
            // Comments thread as Content Detail -> Shoot (same StageCommentService call, same
            // canCommentOnShoot rule as DeliverableMvcController#view - never a second, reduced
            // comment source), so a Cameraperson's task comment is visible from either entry point.
            boolean canCommentOnShoot = nativeAuthority || shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                    .stream().anyMatch(a -> a.getCameraperson().getId().equals(user.getId()));
            model.addAttribute("canCommentOnShoot", canCommentOnShoot);
            model.addAttribute("shootComments", stageCommentService.listComments(plan.getId(), LifecycleStage.SHOOTING));
        } else if (gateType == GateType.EDIT_REVIEW) {
            model.addAttribute("editingAssignments", editingAssignmentRepository.findByContentPlanAndActiveTrue(plan));
            model.addAttribute("qualifyingParticipants", dedupeByUser(
                    editingParticipantRepository.findByContentPlan(plan), EditingExecutionParticipant::getEditor));
            model.addAttribute("canSeeEditDescription",
                    allowed(user, OperationalPermission.PERM_06_EDIT_ASSIGNMENT, LifecycleStage.EDITING, plan));
            // Same review-consistency fix as Shoot above, for Edit.
            boolean canCommentOnEdit = nativeAuthority || editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                    .stream().anyMatch(a -> a.getEditor().getId().equals(user.getId()));
            model.addAttribute("canCommentOnEdit", canCommentOnEdit);
            model.addAttribute("editComments", stageCommentService.listComments(plan.getId(), LifecycleStage.EDITING));
        }
    }

    @PostMapping("/planning/{id}/decision")
    public ResponseEntity<?> decidePlanning(@PathVariable UUID id, @RequestParam boolean approve,
                                             @RequestParam(required = false) String reason,
                                             @AuthenticationPrincipal KcpcUserPrincipal principal, HttpServletRequest request) {
        try {
            planningService.decidePlanningReview(principal.user(), id, approve, reason);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (DomainException e) {
            return ajaxError(e, request);
        }
    }

    @PostMapping("/shoot/{id}/decision")
    public ResponseEntity<?> decideShoot(@PathVariable UUID id, @RequestParam boolean approve,
                                          @RequestParam(required = false) String reason,
                                          @RequestParam(required = false) List<UUID> qualifyingRecipientUserIds,
                                          @AuthenticationPrincipal KcpcUserPrincipal principal, HttpServletRequest request) {
        try {
            shootingService.decideShootReview(principal.user(), id, approve, reason, qualifyingRecipientUserIds);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (DomainException e) {
            return ajaxError(e, request);
        }
    }

    @PostMapping("/edit/{id}/decision")
    public ResponseEntity<?> decideEdit(@PathVariable UUID id, @RequestParam boolean approve,
                                         @RequestParam(required = false) String reason,
                                         @RequestParam(required = false) List<UUID> qualifyingRecipientUserIds,
                                         @AuthenticationPrincipal KcpcUserPrincipal principal, HttpServletRequest request) {
        try {
            editingService.decideEditReview(principal.user(), id, approve, reason, qualifyingRecipientUserIds);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (DomainException e) {
            return ajaxError(e, request);
        }
    }

    // ------------------------------------------------------------- Helpers

    private boolean allowed(User user, OperationalPermission permission, LifecycleStage stage, ContentPlan plan) {
        try {
            authorizationService.requireAuthority(user, permission, stage, plan.getWorkflowInstance());
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    private boolean isParticipant(ContentPlan plan, GateType gateType, User user) {
        return switch (gateType) {
            case PLANNING_REVIEW -> planningPreparerRepository.findByContentPlan(plan).stream()
                    .anyMatch(p -> p.getPreparer().getId().equals(user.getId()));
            case SHOOT_REVIEW -> shootingParticipantRepository.findByContentPlan(plan).stream()
                    .anyMatch(p -> p.getCameraperson().getId().equals(user.getId()));
            case EDIT_REVIEW -> editingParticipantRepository.findByContentPlan(plan).stream()
                    .anyMatch(p -> p.getEditor().getId().equals(user.getId()));
            default -> false;
        };
    }

    private LifecycleStage lifecycleStageFor(GateType gateType) {
        return switch (gateType) {
            case PLANNING_REVIEW -> LifecycleStage.PLANNING;
            case SHOOT_REVIEW -> LifecycleStage.SHOOTING;
            case EDIT_REVIEW -> LifecycleStage.EDITING;
            default -> throw new IllegalArgumentException("Unsupported review gate: " + gateType);
        };
    }

    /** The reviewer's-eye-view history for one gate on one plan - every DECIDED cycle, newest first,
     * reusing {@link ShootFeedbackEntry} exactly as Content Detail's own Review Feedback History does. */
    private List<ShootFeedbackEntry> reviewHistory(ContentPlan plan, GateType gateType) {
        List<com.kcpc.mkt.workflow.domain.ReviewCycle> decided = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), gateType)
                .stream().filter(c -> c.getDecidedAt() != null).toList();
        // ReviewCycle.reviewer is LAZY and this controller isn't @Transactional (open-in-view is
        // disabled app-wide) - touch only the proxy's own .getId() (safe, no DB hit), then batch-
        // fetch the real User rows once, same established pattern DeliverableMvcController already
        // uses for the identical Shoot feedback history (ENG-062), rather than a second reduced
        // implementation that would re-introduce the exact LazyInitializationException it fixed.
        java.util.Set<java.util.UUID> reviewerIds = decided.stream().map(com.kcpc.mkt.workflow.domain.ReviewCycle::getReviewer)
                .filter(java.util.Objects::nonNull).map(User::getId).collect(java.util.stream.Collectors.toSet());
        Map<java.util.UUID, User> reviewersById = reviewerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(reviewerIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        return decided.stream()
                .map(c -> {
                    java.util.UUID reviewerId = c.getReviewer() == null ? null : c.getReviewer().getId();
                    User reviewer = reviewerId == null ? null : reviewersById.get(reviewerId);
                    return new ShootFeedbackEntry(gateType.name(), c.getCycleNumber(),
                            decisionLabel(c.getDecision()), decisionCssClass(c.getDecision()), c.getDecisionReason(),
                            reviewer != null ? reviewer.getFullName() : "—", false, c.getDecidedAt());
                })
                .toList();
    }

    /** The currently OPEN cycle's submittedAt (null if none is open, e.g. Approve already recorded elsewhere). */
    private Instant pendingSubmittedAt(ContentPlan plan, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(plan.getWorkflowInstance(), gateType)
                .stream().filter(c -> c.getDecidedAt() == null).findFirst()
                .map(com.kcpc.mkt.workflow.domain.ReviewCycle::getSubmittedAt).orElse(null);
    }

    private static String decisionLabel(String decision) {
        return "REQUEST_REWORK".equals(decision) ? "Rework Requested" : "Approved";
    }

    private static String decisionCssClass(String decision) {
        return "REQUEST_REWORK".equals(decision) ? "status-rejected" : "status-completed";
    }

    private static long countByStatus(List<ContentPlan> plans, WorkflowStatus status) {
        return plans.stream().filter(p -> p.getWorkflowInstance().getCurrentStatusCode() == status).count();
    }

    private static <T> List<T> dedupeByUser(List<T> rows, Function<T, User> userExtractor) {
        Map<UUID, T> byUserId = new LinkedHashMap<>();
        for (T row : rows) {
            byUserId.putIfAbsent(userExtractor.apply(row).getId(), row);
        }
        return new ArrayList<>(byUserId.values());
    }

    /** Same fromIndex/toIndex/totalPages math as IdeaMvcController#ideaQueue, generalized. */
    private <T> List<T> paginate(List<T> filtered, int page, int pageSize, Model model) {
        int effectiveSize = ALLOWED_PAGE_SIZES.contains(pageSize) ? pageSize : DEFAULT_PAGE_SIZE;
        int totalCount = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) effectiveSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = Math.min((currentPage - 1) * effectiveSize, totalCount);
        int toIndex = Math.min(fromIndex + effectiveSize, totalCount);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("fromIndex", totalCount == 0 ? 0 : fromIndex + 1);
        model.addAttribute("toIndex", toIndex);
        model.addAttribute("pageSize", effectiveSize);
        return totalCount == 0 ? List.of() : filtered.subList(fromIndex, toIndex);
    }

    private ResponseEntity<ApiErrorResponse> ajaxError(DomainException e, HttpServletRequest request) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiErrorResponse.of(e.getErrorCode(), e.getMessage(), request.getRequestURI()));
    }
}
