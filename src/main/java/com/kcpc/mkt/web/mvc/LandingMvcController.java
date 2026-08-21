package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.publishing.service.PublishingService;
import com.kcpc.mkt.reporting.service.PipelineDashboardService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.web.mvc.dto.ActiveWorkItem;
import com.kcpc.mkt.web.mvc.dto.CompletedWorkItem;
import com.kcpc.mkt.web.mvc.dto.MyShootRow;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkHoldRecord;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UI/UX Design Specification §9.2: role-appropriate landing - "My Work" for Employees, "Content
 * Pipeline" for CEO/MM. Privacy (SRS-REQ-067): My Work renders only the authenticated user's own
 * tasks/marks/ideas - never another user's, and there is no parameterized "view another user"
 * path anywhere in this controller.
 */
@Controller
public class LandingMvcController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ContentPlanRepository contentPlanRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final PersonalMarkAttributionRepository markAttributionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final UserRepository userRepository;
    private final PipelineDashboardService pipelineDashboardService;
    private final PublishingService publishingService;
    private final AuthorizationService authorizationService;

    private static final Set<WorkflowStatus> SHOOT_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.SA, WorkflowStatus.SIP, WorkflowStatus.SRV);
    private static final Set<WorkflowStatus> EDIT_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.EA, WorkflowStatus.ED, WorkflowStatus.ERV);
    private static final Set<WorkflowStatus> PUBLISH_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.RFP, WorkflowStatus.PUBG);

    // ENG-069: Content Pipeline KPI-card groupings - display-only, grouped by the row's own
    // friendly status label (WorkflowStatus.getStatusName()), never a new backend status.
    private static final Set<String> PLANNING_STATUS_LABELS = statusLabels(
            WorkflowStatus.PL, WorkflowStatus.PLRV, WorkflowStatus.PLAP);
    private static final Set<String> SHOOT_STATUS_LABELS = statusLabels(
            WorkflowStatus.SA, WorkflowStatus.SIP, WorkflowStatus.SRV, WorkflowStatus.SAP);
    private static final Set<String> EDIT_STATUS_LABELS = statusLabels(
            WorkflowStatus.EA, WorkflowStatus.ED, WorkflowStatus.ERV, WorkflowStatus.EAP);
    private static final Set<String> PUBLISHING_STATUS_LABELS = statusLabels(
            WorkflowStatus.RFP, WorkflowStatus.PUBG);
    private static final Set<String> PERFORMANCE_STATUS_LABELS = statusLabels(
            WorkflowStatus.PP, WorkflowStatus.PFUP);

    private static Set<String> statusLabels(WorkflowStatus... statuses) {
        Set<String> labels = new LinkedHashSet<>();
        for (WorkflowStatus s : statuses) {
            labels.add(s.getStatusName());
        }
        return labels;
    }

    public LandingMvcController(ContentPlanRepository contentPlanRepository,
                                 ShootingAssignmentRepository shootingAssignmentRepository,
                                 EditingAssignmentRepository editingAssignmentRepository,
                                 PublishingAssignmentRepository publishingAssignmentRepository,
                                 PersonalMarkAttributionRepository markAttributionRepository,
                                 ReviewCycleRepository reviewCycleRepository,
                                 WorkHoldRecordRepository workHoldRecordRepository,
                                 WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                 ContentPlanTalentEntryRepository talentEntryRepository,
                                 UserRepository userRepository,
                                 PipelineDashboardService pipelineDashboardService,
                                 PublishingService publishingService,
                                 AuthorizationService authorizationService) {
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.markAttributionRepository = markAttributionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.userRepository = userRepository;
        this.pipelineDashboardService = pipelineDashboardService;
        this.publishingService = publishingService;
        this.authorizationService = authorizationService;
    }

    /** Role-appropriate dispatch, kept as the shared post-login redirect target. */
    @GetMapping("/app/home")
    public String home(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        User user = principal.user();
        AccessClass accessClass = user.resolvedAccessClass();
        if (accessClass == AccessClass.CEO_OWNER || accessClass == AccessClass.MARKETING_MANAGER) {
            return "redirect:/app/pipeline";
        }
        // ENG-067: Model's own landing page is My Shoots (their assigned shoots as talent), not the
        // execution-focused My Work dashboard - Model isn't Camera Person/Video Editor/Publisher and
        // holds no stage assignment of their own to execute.
        String businessRoleName = user.getBusinessRole() == null ? null : user.getBusinessRole().getRoleName();
        if ("Model".equals(businessRoleName)) {
            return "redirect:/app/my-shoots";
        }
        return "redirect:/app/my-work";
    }

    @GetMapping("/app/my-work")
    public String myWork(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        model.addAttribute("user", user);
        model.addAttribute("accessClass", user.resolvedAccessClass());
        model.addAttribute("businessRoleName", user.getBusinessRole() == null ? null : user.getBusinessRole().getRoleName());

        // Task visibility comes from an active assignment, never Designation/Business Role alone -
        // and only for as long as that stage is still THIS employee's active work. Shoot
        // Assignment is created during Planning (Stage 3), before Planning Review even starts, so
        // an active ShootingAssignment can exist well before the plan is actually approved (hidden
        // entirely below, status still PL/PLRV); once a stage's own review has decided and the
        // plan has moved on to the next stage, that assignment drops out of Active Work and into
        // Completed Work instead - own-stage summary only, never the next stage's operational
        // detail (ENG-038).
        List<ShootingAssignment> rawShootTasks = shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user);
        List<EditingAssignment> rawEditTasks = editingAssignmentRepository.findByEditorAndActiveTrue(user);
        List<PublishingAssignment> rawPublishTasks = publishingAssignmentRepository.findByPublisherAndActiveTrue(user);

        // ENG-057: every workflow instance this page will need history/review data for, fetched in
        // two queries total up front instead of two-per-row (the previous shape) - avoids N+1
        // whether the row ends up in Active Work (rework-detection) or Completed Work.
        Set<UUID> instanceIds = new LinkedHashSet<>();
        Set<UUID> planIds = new LinkedHashSet<>();
        for (ShootingAssignment t : rawShootTasks) {
            instanceIds.add(t.getContentPlan().getWorkflowInstance().getId());
            planIds.add(t.getContentPlan().getId());
        }
        for (EditingAssignment t : rawEditTasks) {
            instanceIds.add(t.getContentPlan().getWorkflowInstance().getId());
            planIds.add(t.getContentPlan().getId());
        }
        for (PublishingAssignment t : rawPublishTasks) {
            instanceIds.add(t.getContentPlan().getWorkflowInstance().getId());
            planIds.add(t.getContentPlan().getId());
        }
        Map<UUID, List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory>> transitionsByInstance = instanceIds.isEmpty()
                ? Map.of()
                : transitionHistoryRepository.findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(instanceIds).stream()
                        .collect(Collectors.groupingBy(t -> t.getWorkflowInstance().getId()));
        Map<UUID, List<ReviewCycle>> reviewCyclesByInstance = instanceIds.isEmpty()
                ? Map.of()
                : reviewCycleRepository.findByWorkflowInstance_IdIn(instanceIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getWorkflowInstance().getId()));
        // ENG-058: Model(s) column - batch-loaded once for every relevant plan, same shape as the
        // transition/review maps above (avoids N+1 the same way).
        Map<UUID, String> modelsByPlan = planIds.isEmpty()
                ? Map.of()
                : talentEntryRepository.findByContentPlan_IdIn(planIds).stream()
                        .collect(Collectors.groupingBy(e -> e.getContentPlan().getId(),
                                Collectors.mapping(ContentPlanTalentEntry::getTalentName, Collectors.joining(", "))));

        List<ActiveWorkItem> activeWork = new ArrayList<>();
        List<CompletedWorkItem> completedWork = new ArrayList<>();
        boolean anyActiveShootLead = false;
        String shootLeadDisplayName = null;
        boolean anyActiveEditLead = false;
        String editLeadDisplayName = null;
        int pendingTargetsTotal = 0;
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        for (ShootingAssignment t : rawShootTasks) {
            ContentPlan plan = t.getContentPlan();
            WorkflowStatus s = plan.getWorkflowInstance().getCurrentStatusCode();
            if (s == WorkflowStatus.PL || s == WorkflowStatus.PLRV) {
                continue; // not visible at all yet - Planning not Approved (ENG-037)
            }
            if (SHOOT_ACTIVE_WINDOW.contains(s)) {
                ShootingAssignment lead = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .filter(ShootingAssignment::isLead).findFirst().orElse(null);
                boolean isLead = lead != null && lead.getCameraperson().getId().equals(user.getId());
                if (isLead) {
                    anyActiveShootLead = true;
                    shootLeadDisplayName = user.getFullName();
                }
                String statusLabel = activeStatusLabel(s, GateType.SHOOT_REVIEW, plan.getWorkflowInstance().getId(),
                        reviewCyclesByInstance);
                // ENG-058: past Planned Shoot Date and not yet completed - independent of statusLabel
                // (an In Review row can still be "delayed" from the employee's perspective).
                Integer delayDays = (plan.getPlannedShootDate() != null && plan.getPlannedShootDate().isBefore(today))
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(plan.getPlannedShootDate(), today)
                        : null;
                activeWork.add(new ActiveWorkItem(plan.getId(), plan.getContentId(), contentTitle(plan), "Cameraperson",
                        plan.getContentPriority() == null ? null : plan.getContentPriority().name(),
                        priorityCssClass(plan.getContentPriority()),
                        plan.getPlannedShootDate(), lead == null ? null : lead.getCameraperson().getFullName(), isLead,
                        modelsByPlan.get(plan.getId()), statusLabel, statusCssClass(statusLabel), delayDays,
                        actionLabel(statusLabel, delayDays != null, "Cameraperson"), plan.getFolderLink(), null));
            } else {
                completedWork.add(completedItem(plan, t.getId(), "SHOOT", GateType.SHOOT_REVIEW, SHOOT_ACTIVE_WINDOW,
                        plan.getPlannedShootDate(), modelsByPlan.get(plan.getId()), transitionsByInstance, reviewCyclesByInstance));
            }
        }
        for (EditingAssignment t : rawEditTasks) {
            ContentPlan plan = t.getContentPlan();
            WorkflowStatus s = plan.getWorkflowInstance().getCurrentStatusCode();
            if (EDIT_ACTIVE_WINDOW.contains(s)) {
                List<EditingAssignment> planEditors = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
                EditingAssignment lead = planEditors.stream().filter(EditingAssignment::isLead).findFirst().orElse(null);
                boolean isLead = lead != null && lead.getEditor().getId().equals(user.getId());
                if (isLead) {
                    anyActiveEditLead = true;
                    editLeadDisplayName = user.getFullName();
                }
                String editorNames = planEditors.stream().map(a -> a.getEditor().getFullName())
                        .collect(Collectors.joining(", "));
                String statusLabel = activeStatusLabel(s, GateType.EDIT_REVIEW, plan.getWorkflowInstance().getId(),
                        reviewCyclesByInstance);
                // Same "past planned date and not yet completed" rule as Shoot (ENG-058), based on
                // the Edit-specific planned date.
                Integer delayDays = (plan.getPlannedEditDate() != null && plan.getPlannedEditDate().isBefore(today))
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(plan.getPlannedEditDate(), today)
                        : null;
                activeWork.add(new ActiveWorkItem(plan.getId(), plan.getContentId(), contentTitle(plan), "Editor",
                        plan.getContentPriority() == null ? null : plan.getContentPriority().name(),
                        priorityCssClass(plan.getContentPriority()),
                        plan.getPlannedEditDate(), lead == null ? null : lead.getEditor().getFullName(), isLead,
                        editorNames, statusLabel, statusCssClass(statusLabel), delayDays,
                        actionLabel(statusLabel, delayDays != null, "Editor"), plan.getFolderLink(), null));
            } else {
                completedWork.add(completedItem(plan, t.getId(), "EDIT", GateType.EDIT_REVIEW, EDIT_ACTIVE_WINDOW,
                        plan.getPlannedEditDate(), null, transitionsByInstance, reviewCyclesByInstance));
            }
        }
        for (PublishingAssignment t : rawPublishTasks) {
            ContentPlan plan = t.getContentPlan();
            WorkflowStatus s = plan.getWorkflowInstance().getCurrentStatusCode();
            if (PUBLISH_ACTIVE_WINDOW.contains(s)) {
                String publisherNames = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                        .map(a -> a.getPublisher().getFullName()).collect(Collectors.joining(", "));
                PublishingService.TargetResolutionSummary targets = publishingService.summarizeTargets(plan);
                pendingTargetsTotal += targets.totalCount() - targets.resolvedCount();
                String statusLabel = activeStatusLabel(s, null, plan.getWorkflowInstance().getId(), reviewCyclesByInstance);
                activeWork.add(new ActiveWorkItem(plan.getId(), plan.getContentId(), contentTitle(plan), "Publisher",
                        plan.getContentPriority() == null ? null : plan.getContentPriority().name(),
                        priorityCssClass(plan.getContentPriority()),
                        plan.getPlannedLiveDate(), null, false, publisherNames, statusLabel, statusCssClass(statusLabel), null,
                        actionLabel(statusLabel, false, "Publisher"), plan.getFolderLink(),
                        targets.resolvedCount() + " / " + targets.totalCount()));
            } else {
                // No review/decision gate exists for Publishing - Final Result stays blank.
                completedWork.add(completedItem(plan, t.getId(), "PUBLISH", null, PUBLISH_ACTIVE_WINDOW,
                        plan.getPlannedLiveDate(), null, transitionsByInstance, reviewCyclesByInstance));
            }
        }
        completedWork.sort(Comparator.comparing(CompletedWorkItem::getCompletedOn,
                Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("activeWork", activeWork);
        model.addAttribute("completedWork", completedWork);
        model.addAttribute("isShootLead", anyActiveShootLead);
        model.addAttribute("shootLeadDisplayName", shootLeadDisplayName);
        model.addAttribute("isEditLead", anyActiveEditLead);
        model.addAttribute("editLeadDisplayName", editLeadDisplayName);
        model.addAttribute("today", today);

        // ENG-058: Cameraperson-focused dashboard (KPI cards + tabs + Shoot-flavored tables) shows
        // whenever this employee's Business Role is Camera Person; every other Business Role keeps
        // the ENG-057 generic Active/Completed Work layout unchanged. Both the KPI counts and the
        // Shoot-specific tables read from the exact same activeWork/completedWork lists already
        // built above (filtered by roleLabel/stageWorked) - never a separate count query - so a
        // count/table mismatch is structurally impossible.
        List<ActiveWorkItem> shootActiveWork = activeWork.stream()
                .filter(item -> "Cameraperson".equals(item.getRoleLabel())).toList();
        List<CompletedWorkItem> shootCompletedWork = completedWork.stream()
                .filter(item -> "SHOOT".equals(item.getStageWorked())).toList();
        model.addAttribute("shootActiveWork", shootActiveWork);
        model.addAttribute("shootCompletedWork", shootCompletedWork);
        model.addAttribute("activeShootsCount", shootActiveWork.size());
        model.addAttribute("reworkRequiredCount",
                shootActiveWork.stream().filter(item -> "Rework Required".equals(item.getStatusLabel())).count());
        model.addAttribute("delayedShootsCount", shootActiveWork.stream().filter(ActiveWorkItem::isDelayed).count());
        model.addAttribute("completedShootsCount", shootCompletedWork.size());

        // ENG-066: Editor-focused dashboard (KPI cards + tabs + Edit-flavored tables) - identical
        // pattern to the Cameraperson dashboard above, same activeWork/completedWork lists filtered
        // by roleLabel/stageWorked instead of a separate query, same count/table consistency guarantee.
        List<ActiveWorkItem> editActiveWork = activeWork.stream()
                .filter(item -> "Editor".equals(item.getRoleLabel())).toList();
        List<CompletedWorkItem> editCompletedWork = completedWork.stream()
                .filter(item -> "EDIT".equals(item.getStageWorked())).toList();
        model.addAttribute("editActiveWork", editActiveWork);
        model.addAttribute("editCompletedWork", editCompletedWork);
        model.addAttribute("activeEditsCount", editActiveWork.size());
        model.addAttribute("editReworkRequiredCount",
                editActiveWork.stream().filter(item -> "Rework Required".equals(item.getStatusLabel())).count());
        model.addAttribute("editDelayedCount", editActiveWork.stream().filter(ActiveWorkItem::isDelayed).count());
        model.addAttribute("editCompletedCount", editCompletedWork.size());

        // ENG-068: Publisher-focused dashboard - same pattern again, but only 2 KPI cards worth of
        // grouping concepts apply here (no Rework/Delayed - Publishing has no review gate and no
        // per-row delay tracking was requested); "Pending Targets" is a genuinely different kind of
        // count from the other two roles' KPIs - a sum of unresolved (Planned Output, Publication
        // Target) pairs across every active row, not a row count - accumulated once above in the
        // same loop that builds each row's own "resolved / total" Targets column, so the KPI number
        // and the table's per-row figures can never drift apart.
        List<ActiveWorkItem> publishActiveWork = activeWork.stream()
                .filter(item -> "Publisher".equals(item.getRoleLabel())).toList();
        List<CompletedWorkItem> publishCompletedWork = completedWork.stream()
                .filter(item -> "PUBLISH".equals(item.getStageWorked())).toList();
        model.addAttribute("publishActiveWork", publishActiveWork);
        model.addAttribute("publishCompletedWork", publishCompletedWork);
        model.addAttribute("activePublishingCount", publishActiveWork.size());
        model.addAttribute("pendingTargetsCount", pendingTargetsTotal);
        model.addAttribute("publishCompletedCount", publishCompletedWork.size());

        model.addAttribute("myMarks", markAttributionRepository.findByRecipient(user));

        return "my-work";
    }

    /**
     * Read-only "Completed Task Details" snapshot for one of this employee's own past Shoot
     * assignments, keyed by the assignment's OWN id - never the Content Plan id, and never the
     * shared {@code /app/deliverables/{id}} page. That page has no ownership/stage gate at all:
     * once a plan moves past Shoot, a Cameraperson landing on it via the old History link would
     * fall through to the full generic Content Detail view and see next-stage data (Editor,
     * Publisher, published URLs...) that isn't theirs to see. This endpoint only ever assembles
     * Shoot-scoped data (reusing the same {@link #completedItem} summary already proven safe for
     * the History table) and enforces ownership server-side - not merely hidden in the JSP -
     * before returning anything: neither a wrong assignmentId nor a manually-edited URL can reach
     * another employee's or another stage's data. Native CEO/MM may also view it (they see every
     * assignment via the Pipeline/Content Detail pages already; this endpoint isn't their primary
     * path but shouldn't 403 them either).
     */
    @GetMapping("/app/my-work/history/shoot/{assignmentId}")
    public String shootHistoryDetail(@PathVariable UUID assignmentId,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        ShootingAssignment assignment = shootingAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Assignment not found"));
        if (!assignment.getCameraperson().getId().equals(user.getId()) && !authorizationService.hasNativeAuthority(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Not your assignment");
        }
        ContentPlan plan = assignment.getContentPlan();
        String models = talentEntryRepository.findByContentPlan(plan).stream()
                .map(ContentPlanTalentEntry::getTalentName).collect(Collectors.joining(", "));
        model.addAttribute("stage", "SHOOT");
        model.addAttribute("summary", completedItem(plan, assignment.getId(), "SHOOT", GateType.SHOOT_REVIEW,
                SHOOT_ACTIVE_WINDOW, plan.getPlannedShootDate(), models, singlePlanTransitions(plan), singlePlanReviewCycles(plan)));
        model.addAttribute("assigneeName", assignment.getCameraperson().getFullName());
        model.addAttribute("isLead", assignment.isLead());
        model.addAttribute("assignedAt", assignment.getAssignedAt());
        model.addAttribute("plannedDate", plan.getPlannedShootDate());
        model.addAttribute("stageDescription", plan.getShootDescription());
        model.addAttribute("folderLink", plan.getFolderLink());
        return "my-work-history-detail";
    }

    /** Exact mirror of {@link #shootHistoryDetail} for a Video Editor's own past Edit assignment. */
    @GetMapping("/app/my-work/history/edit/{assignmentId}")
    public String editHistoryDetail(@PathVariable UUID assignmentId,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        EditingAssignment assignment = editingAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Assignment not found"));
        if (!assignment.getEditor().getId().equals(user.getId()) && !authorizationService.hasNativeAuthority(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Not your assignment");
        }
        ContentPlan plan = assignment.getContentPlan();
        model.addAttribute("stage", "EDIT");
        model.addAttribute("summary", completedItem(plan, assignment.getId(), "EDIT", GateType.EDIT_REVIEW,
                EDIT_ACTIVE_WINDOW, plan.getPlannedEditDate(), null, singlePlanTransitions(plan), singlePlanReviewCycles(plan)));
        model.addAttribute("assigneeName", assignment.getEditor().getFullName());
        model.addAttribute("isLead", assignment.isLead());
        model.addAttribute("assignedAt", assignment.getAssignedAt());
        model.addAttribute("plannedDate", plan.getPlannedEditDate());
        model.addAttribute("stageDescription", plan.getEditDescription());
        model.addAttribute("folderLink", plan.getFolderLink());
        return "my-work-history-detail";
    }

    /**
     * Exact mirror of {@link #shootHistoryDetail} for a Publisher's own past Publishing
     * assignment - no review gate exists for Publishing, so {@code summary}'s finalResult/remarks
     * stay null (matching the History table's own existing "no Final Result column" behavior).
     */
    @GetMapping("/app/my-work/history/publish/{assignmentId}")
    public String publishHistoryDetail(@PathVariable UUID assignmentId,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        PublishingAssignment assignment = publishingAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Assignment not found"));
        if (!assignment.getPublisher().getId().equals(user.getId()) && !authorizationService.hasNativeAuthority(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Not your assignment");
        }
        ContentPlan plan = assignment.getContentPlan();
        model.addAttribute("stage", "PUBLISH");
        model.addAttribute("summary", completedItem(plan, assignment.getId(), "PUBLISH", null,
                PUBLISH_ACTIVE_WINDOW, plan.getPlannedLiveDate(), null, singlePlanTransitions(plan), singlePlanReviewCycles(plan)));
        model.addAttribute("assigneeName", assignment.getPublisher().getFullName());
        model.addAttribute("isLead", false);
        model.addAttribute("assignedAt", assignment.getAssignedAt());
        model.addAttribute("plannedDate", plan.getPlannedLiveDate());
        model.addAttribute("stageDescription", plan.getPublishingDescription());
        model.addAttribute("folderLink", plan.getFolderLink());
        return "my-work-history-detail";
    }

    /** Single-plan-scoped variant of the batched map {@link #myWork} builds for its whole list. */
    private Map<UUID, List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory>> singlePlanTransitions(ContentPlan plan) {
        return transitionHistoryRepository
                .findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(Set.of(plan.getWorkflowInstance().getId())).stream()
                .collect(Collectors.groupingBy(t -> t.getWorkflowInstance().getId()));
    }

    /** Single-plan-scoped variant of the batched map {@link #myWork} builds for its whole list. */
    private Map<UUID, List<ReviewCycle>> singlePlanReviewCycles(ContentPlan plan) {
        return reviewCycleRepository.findByWorkflowInstance_IdIn(Set.of(plan.getWorkflowInstance().getId())).stream()
                .collect(Collectors.groupingBy(c -> c.getWorkflowInstance().getId()));
    }

    private static String contentTitle(ContentPlan plan) {
        return plan.getIdea() == null ? plan.getContentId() : plan.getIdea().getTitle();
    }

    /**
     * ENG-057/062: simplified Active Work status - "Assigned" (not yet started), "Submitted for
     * Review" (submitted, awaiting a decision), or, for the in-progress states (SIP/ED/PUBG),
     * "Rework Required" if a prior Request Rework decision exists for this exact gate on this
     * workflow instance, otherwise "In Progress". Publishing has no review gate at all, so it's
     * always Assigned/In Progress.
     */
    private String activeStatusLabel(WorkflowStatus status, GateType gateType, UUID workflowInstanceId,
                                      Map<UUID, List<ReviewCycle>> reviewCyclesByInstance) {
        if (status == WorkflowStatus.SA || status == WorkflowStatus.EA || status == WorkflowStatus.RFP) {
            return "Assigned";
        }
        if (status == WorkflowStatus.SRV || status == WorkflowStatus.ERV) {
            return "Submitted for Review";
        }
        if (gateType == null) {
            return "In Progress";
        }
        boolean needsChanges = reviewCyclesByInstance.getOrDefault(workflowInstanceId, List.of()).stream()
                .anyMatch(c -> c.getGateType() == gateType && "REQUEST_REWORK".equals(c.getDecision()));
        return needsChanges ? "Rework Required" : "In Progress";
    }

    private static String statusCssClass(String statusLabel) {
        return switch (statusLabel) {
            case "Assigned" -> "status-assigned";
            case "Submitted for Review" -> "status-inreview";
            case "Rework Required" -> "status-needschanges";
            default -> "status-inprogress";
        };
    }

    /**
     * ENG-058: the Active Work row's action link is a plain navigation to the deliverable detail
     * page (not an inline form/POST) - "Clicking the row/action should open the task detail
     * screen" - labeled per status so the employee knows what they're walking into. "Submitted for
     * Review" gets no button at all (nothing to do until a decision is made). A delayed row that
     * would otherwise read "Continue" reads "Resume" instead; a delayed-but-not-yet-started row
     * still reads "Start Shoot" (there's nothing to resume).
     */
    private static String actionLabel(String statusLabel, boolean delayed, String roleLabel) {
        return switch (statusLabel) {
            case "Assigned" -> "Editor".equals(roleLabel) ? "Start Edit"
                    : "Publisher".equals(roleLabel) ? "Start Publishing" : "Start Shoot";
            case "In Progress" -> delayed ? "Resume" : "Continue";
            case "Rework Required" -> "View Feedback";
            case "Submitted for Review" -> null;
            default -> "View Details";
        };
    }

    private static String priorityCssClass(com.kcpc.mkt.planning.domain.ContentPriority priority) {
        if (priority == null) {
            return "";
        }
        return switch (priority) {
            case HIGH -> "priority-high";
            case MEDIUM -> "priority-medium";
            case LOW -> "priority-low";
        };
    }

    /**
     * Builds a Completed Work row for a stage that has moved on. Completed On is the most recent
     * transition that took the plan OUT of that stage's active window (handles a reopen-then-reclose
     * cycle correctly by always reflecting the latest such exit, not the first); Final Result/Remarks
     * are the deciding review's outcome/reason for gates that have one (Shoot/Edit), or null for
     * Publishing, which has no review/decision gate at all. Reads only from the batched maps built in
     * {@link #myWork} - no per-row query (ENG-057).
     */
    private CompletedWorkItem completedItem(ContentPlan plan, UUID assignmentId, String stageWorked, GateType gateType,
                                             Set<WorkflowStatus> activeWindow, LocalDate stageDate, String models,
                                             Map<UUID, List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory>> transitionsByInstance,
                                             Map<UUID, List<ReviewCycle>> reviewCyclesByInstance) {
        UUID instanceId = plan.getWorkflowInstance().getId();
        Instant completedOn = transitionsByInstance.getOrDefault(instanceId, List.of()).stream()
                .filter(t -> activeWindow.contains(t.getFromStatusCode()) && !activeWindow.contains(t.getToStatusCode()))
                .reduce((first, second) -> second)
                .map(com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory::getTransitionTimestamp)
                .orElse(null);
        String finalResult = null;
        String remarks = null;
        if (gateType != null) {
            Optional<ReviewCycle> decided = reviewCyclesByInstance.getOrDefault(instanceId, List.of()).stream()
                    .filter(c -> c.getGateType() == gateType && c.getDecidedAt() != null)
                    .max(Comparator.comparing(ReviewCycle::getCycleNumber));
            finalResult = decided.map(c -> "APPROVED".equals(c.getDecision()) ? "Approved" : c.getDecision()).orElse(null);
            remarks = decided.map(ReviewCycle::getDecisionReason).orElse(null);
        }
        return new CompletedWorkItem(plan.getId(), assignmentId, plan.getContentId(), contentTitle(plan), stageWorked,
                stageDate, models, completedOn, finalResult, remarks);
    }

    /**
     * CEO Content Pipeline 18-column dashboard (docs/changes/CEO_CONTENT_PIPELINE_18_COLUMN_CHANGE.md).
     * Shared by CEO_OWNER and MARKETING_MANAGER, matching the existing role-appropriate landing
     * split; EMPLOYEE-class users are redirected rather than shown this company-wide view.
     */
    @GetMapping("/app/pipeline")
    public String pipeline(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false, defaultValue = "all") String stage,
                            @RequestParam(required = false) String sku,
                            @RequestParam(required = false) String idea,
                            @RequestParam(required = false) String priority,
                            @RequestParam(required = false) String cameraperson,
                            @RequestParam(name = "model", required = false) String modelFilter,
                            @RequestParam(required = false) String videoEditor,
                            @RequestParam(required = false) String platform,
                            @RequestParam(required = false) String channel,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String performanceState,
                            @RequestParam(required = false, defaultValue = "false") boolean delayed,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate plannedShootFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate plannedShootTo,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate plannedEditFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate plannedEditTo,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate plannedLiveFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate plannedLiveTo,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate actualShootFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate actualShootTo,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate actualEditFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate actualEditTo,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate actualLiveFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate actualLiveTo,
                            @RequestParam(required = false) String sortBy,
                            @RequestParam(required = false, defaultValue = "asc") String sortDir,
                            @RequestParam(required = false, defaultValue = "1") int page,
                            @RequestParam(required = false, defaultValue = "10") int size,
                            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        User user = principal.user();
        if (user.resolvedAccessClass() == AccessClass.EMPLOYEE) {
            return "redirect:/app/home";
        }
        model.addAttribute("user", user);
        model.addAttribute("accessClass", user.resolvedAccessClass());

        List<ContentPlan> plans = contentPlanRepository.findAllWithPreparedByOrderByCreatedAtDesc();
        model.addAttribute("plans", plans);
        List<com.kcpc.mkt.reporting.dto.PipelineRow> allRows = pipelineDashboardService.buildRows(plans);
        model.addAttribute("today", LocalDate.now(BUSINESS_ZONE));

        // Pipeline dashboard KPI cards - pure summary counts over the FULL unfiltered row set (a
        // stable at-a-glance company total, independent of whatever filter is currently applied to
        // the table below) - never a separate query, never a new backend status.
        model.addAttribute("planningCount", allRows.stream()
                .filter(r -> PLANNING_STATUS_LABELS.contains(r.getStatus())).count());
        model.addAttribute("shootCount", allRows.stream()
                .filter(r -> SHOOT_STATUS_LABELS.contains(r.getStatus())).count());
        model.addAttribute("editCount", allRows.stream()
                .filter(r -> EDIT_STATUS_LABELS.contains(r.getStatus())).count());
        model.addAttribute("publishingCount", allRows.stream()
                .filter(r -> PUBLISHING_STATUS_LABELS.contains(r.getStatus())).count());
        model.addAttribute("attentionDelayedCount", allRows.stream()
                .filter(com.kcpc.mkt.reporting.dto.PipelineRow::isDelayed).count());
        // ENG-073: Stage filter tabs replaced the KPI cards as the at-a-glance summary - each tab
        // shows its own count badge, same underlying groupings as before.
        model.addAttribute("allCount", allRows.size());
        model.addAttribute("performanceCount", allRows.stream()
                .filter(r -> PERFORMANCE_STATUS_LABELS.contains(r.getStatus())).count());
        model.addAttribute("completedCount", allRows.stream()
                .filter(r -> "Completed".equals(r.getStatus())).count());

        // ENG-071: options for the Status/Platform/Channel filter dropdowns - distinct values
        // actually present across the full (unfiltered) row set, so a dropdown never offers a
        // choice that would always return zero rows.
        Set<String> statusOptions = new LinkedHashSet<>();
        Set<String> platformOptions = new LinkedHashSet<>();
        Set<String> channelOptions = new LinkedHashSet<>();
        for (com.kcpc.mkt.reporting.dto.PipelineRow row : allRows) {
            statusOptions.add(row.getStatus());
            addSplitValues(platformOptions, row.getPlatforms());
            addSplitValues(channelOptions, row.getChannels());
        }
        model.addAttribute("statusOptions", statusOptions);
        model.addAttribute("platformOptions", platformOptions);
        model.addAttribute("channelOptions", channelOptions);

        com.kcpc.mkt.reporting.dto.PipelineFilterCriteria criteria = new com.kcpc.mkt.reporting.dto.PipelineFilterCriteria(
                q, stage, sku, idea, priority, cameraperson, modelFilter, videoEditor, platform, channel, status,
                performanceState, delayed, plannedShootFrom, plannedShootTo, plannedEditFrom, plannedEditTo,
                plannedLiveFrom, plannedLiveTo, actualShootFrom, actualShootTo, actualEditFrom, actualEditTo,
                actualLiveFrom, actualLiveTo, sortBy, sortDir);
        List<com.kcpc.mkt.reporting.dto.PipelineRow> filteredRows = pipelineDashboardService.filterAndSort(allRows, criteria);

        int totalCount = filteredRows.size();
        int safeSize = size <= 0 ? 10 : size;
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) safeSize));
        int safePage = Math.min(Math.max(1, page), totalPages);
        int fromIndex = totalCount == 0 ? 0 : (safePage - 1) * safeSize;
        int toIndex = Math.min(fromIndex + safeSize, totalCount);
        List<com.kcpc.mkt.reporting.dto.PipelineRow> pageRows = totalCount == 0
                ? List.of() : filteredRows.subList(fromIndex, toIndex);
        model.addAttribute("pipelineRows", pageRows);
        model.addAttribute("pipelineTotalCount", totalCount);
        model.addAttribute("pipelineCurrentPage", safePage);
        model.addAttribute("pipelineTotalPages", totalPages);
        model.addAttribute("pipelineFromIndex", totalCount == 0 ? 0 : fromIndex + 1);
        model.addAttribute("pipelineToIndex", toIndex);
        model.addAttribute("pipelineSize", safeSize);

        // Echo every current filter/sort value back for form re-population (the per-column popups)
        // and for building sort/pagination links that preserve the rest of the current filter state.
        model.addAttribute("qParam", q);
        model.addAttribute("stageParam", stage == null ? "all" : stage);
        model.addAttribute("skuParam", sku);
        model.addAttribute("ideaParam", idea);
        model.addAttribute("priorityParam", priority);
        model.addAttribute("camerapersonParam", cameraperson);
        model.addAttribute("modelParam", modelFilter);
        model.addAttribute("videoEditorParam", videoEditor);
        model.addAttribute("platformParam", platform);
        model.addAttribute("channelParam", channel);
        model.addAttribute("statusParam", status);
        model.addAttribute("performanceStateParam", performanceState);
        model.addAttribute("delayedParam", delayed);
        model.addAttribute("plannedShootFromParam", plannedShootFrom);
        model.addAttribute("plannedShootToParam", plannedShootTo);
        model.addAttribute("plannedEditFromParam", plannedEditFrom);
        model.addAttribute("plannedEditToParam", plannedEditTo);
        model.addAttribute("plannedLiveFromParam", plannedLiveFrom);
        model.addAttribute("plannedLiveToParam", plannedLiveTo);
        model.addAttribute("actualShootFromParam", actualShootFrom);
        model.addAttribute("actualShootToParam", actualShootTo);
        model.addAttribute("actualEditFromParam", actualEditFrom);
        model.addAttribute("actualEditToParam", actualEditTo);
        model.addAttribute("actualLiveFromParam", actualLiveFrom);
        model.addAttribute("actualLiveToParam", actualLiveTo);
        model.addAttribute("sortByParam", sortBy);
        model.addAttribute("sortDirParam", sortDir);

        // ENG-071: per-column "is a filter active on this column" flags, so the header's filter
        // icon can be highlighted - the only way a busy MM can tell at a glance which of the 15
        // per-column popups currently has something applied.
        model.addAttribute("skuFilterActive", notBlank(sku));
        model.addAttribute("ideaFilterActive", notBlank(idea));
        model.addAttribute("priorityFilterActive", notBlank(priority));
        model.addAttribute("camerapersonFilterActive", notBlank(cameraperson));
        model.addAttribute("modelFilterActive", notBlank(modelFilter));
        model.addAttribute("videoEditorFilterActive", notBlank(videoEditor));
        model.addAttribute("platformChannelFilterActive", notBlank(platform) || notBlank(channel));
        model.addAttribute("statusFilterActive", notBlank(status) || delayed);
        model.addAttribute("performanceFilterActive", notBlank(performanceState));
        model.addAttribute("plannedShootFilterActive", plannedShootFrom != null || plannedShootTo != null);
        model.addAttribute("plannedEditFilterActive", plannedEditFrom != null || plannedEditTo != null);
        model.addAttribute("plannedLiveFilterActive", plannedLiveFrom != null || plannedLiveTo != null);
        model.addAttribute("actualShootFilterActive", actualShootFrom != null || actualShootTo != null);
        model.addAttribute("actualEditFilterActive", actualEditFrom != null || actualEditTo != null);
        model.addAttribute("actualLiveFilterActive", actualLiveFrom != null || actualLiveTo != null);

        // ENG-071: one shared "current filters as a query string" (everything except sortBy/
        // sortDir/page, which each sort/pagination link appends its own value for) so every
        // sort/pagination/popup-apply link can just append &sortBy=...&page=... instead of each
        // individually repeating all ~20 filter params by hand in the JSP.
        org.springframework.web.util.UriComponentsBuilder filterQs = org.springframework.web.util.UriComponentsBuilder.newInstance();
        addQueryParam(filterQs, "q", q);
        addQueryParam(filterQs, "sku", sku);
        addQueryParam(filterQs, "idea", idea);
        addQueryParam(filterQs, "priority", priority);
        addQueryParam(filterQs, "cameraperson", cameraperson);
        addQueryParam(filterQs, "model", modelFilter);
        addQueryParam(filterQs, "videoEditor", videoEditor);
        addQueryParam(filterQs, "platform", platform);
        addQueryParam(filterQs, "channel", channel);
        addQueryParam(filterQs, "status", status);
        addQueryParam(filterQs, "performanceState", performanceState);
        if (delayed) {
            filterQs.queryParam("delayed", "true");
        }
        addQueryParam(filterQs, "plannedShootFrom", asString(plannedShootFrom));
        addQueryParam(filterQs, "plannedShootTo", asString(plannedShootTo));
        addQueryParam(filterQs, "plannedEditFrom", asString(plannedEditFrom));
        addQueryParam(filterQs, "plannedEditTo", asString(plannedEditTo));
        addQueryParam(filterQs, "plannedLiveFrom", asString(plannedLiveFrom));
        addQueryParam(filterQs, "plannedLiveTo", asString(plannedLiveTo));
        addQueryParam(filterQs, "actualShootFrom", asString(actualShootFrom));
        addQueryParam(filterQs, "actualShootTo", asString(actualShootTo));
        addQueryParam(filterQs, "actualEditFrom", asString(actualEditFrom));
        addQueryParam(filterQs, "actualEditTo", asString(actualEditTo));
        addQueryParam(filterQs, "actualLiveFrom", asString(actualLiveFrom));
        addQueryParam(filterQs, "actualLiveTo", asString(actualLiveTo));
        // size is deliberately NOT included here (same reason sortBy/sortDir aren't) - every
        // consumer of filterQueryString appends its own &size=... explicitly. Including it here
        // too used to produce a duplicate "size" query param (e.g. "...&size=10...&size=25" from
        // the per-page selector) - HttpServletRequest#getParameter returns only the FIRST value
        // for a repeated param, so the selector's own chosen value was silently ignored.
        model.addAttribute("filterQueryString", filterQs.build().encode().getQuery());

        List<WorkHoldRecord> openHolds = workHoldRecordRepository.findByResumedAtIsNull();
        Set<java.util.UUID> onHoldWorkflowInstanceIds = openHolds.stream()
                .map(h -> h.getWorkflowInstance().getId()).collect(Collectors.toSet());
        model.addAttribute("onHoldWorkflowInstanceIds", onHoldWorkflowInstanceIds);

        // ENG-081: pipeline-dashboard.js's fetch() calls (filter/sort/pagination/stage-tab clicks)
        // send this same header the rest of the app already uses for AJAX detection (see
        // DeliverableMvcController#isAjax) - same model, same query/filter/sort/pagination logic,
        // only the view differs: "pipeline-content" renders just the dynamic region (stage tabs
        // through the footer note) with no <html>/<head>/nav wrapper, so the client can drop the
        // response straight into #pipelineDynamicRegion. A plain browser navigation (no header,
        // including every existing bookmark/shared link) still gets the full "pipeline" page.
        return isAjax(requestedWith) ? "pipeline-content" : "pipeline";
    }

    /** See {@code DeliverableMvcController#isAjax} - same convention, kept local to this controller. */
    private static boolean isAjax(String requestedWith) {
        return "fetch".equals(requestedWith);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String asString(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static void addQueryParam(org.springframework.web.util.UriComponentsBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(name, value);
        }
    }

    private static void addSplitValues(Set<String> target, String commaJoined) {
        if (commaJoined == null || "—".equals(commaJoined)) {
            return;
        }
        for (String part : commaJoined.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    /**
     * ENG-067: "My Shoots" - a Model employee's own dedicated, read-only shoot-participation
     * screen. Scoped by the {@code ContentPlanTalentEntry.talentUser} link (not free-text name
     * matching) so this is a real backend-enforced own-data-only view, matching the privacy rule
     * (SRS-REQ-067) already applied to My Work/My Ideas.
     */
    @GetMapping("/app/my-shoots")
    public String myShoots(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        model.addAttribute("user", user);

        List<ContentPlanTalentEntry> myEntries = talentEntryRepository.findByTalentUser(user);
        Set<UUID> planIds = myEntries.stream()
                .map(e -> e.getContentPlan().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, ContentPlan> plansById = contentPlanRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(ContentPlan::getId, p -> p));
        Map<UUID, List<ContentPlanTalentEntry>> allTalentByPlan = talentEntryRepository.findByContentPlan_IdIn(planIds)
                .stream().collect(Collectors.groupingBy(e -> e.getContentPlan().getId()));

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<MyShootRow> upcoming = new ArrayList<>();
        List<MyShootRow> past = new ArrayList<>();
        for (ContentPlanTalentEntry myEntry : myEntries) {
            ContentPlan plan = plansById.get(myEntry.getContentPlan().getId());
            if (plan == null) {
                continue;
            }
            boolean isUpcoming = plan.getPlannedShootDate() == null || !plan.getPlannedShootDate().isBefore(today);
            String otherTalent = allTalentByPlan.getOrDefault(plan.getId(), List.of()).stream()
                    .filter(e -> !e.getId().equals(myEntry.getId()))
                    .map(ContentPlanTalentEntry::getTalentName)
                    .collect(Collectors.joining(", "));
            MyShootRow row = new MyShootRow(plan.getId(), plan.getContentId(), contentTitle(plan),
                    plan.getPlannedShootDate(), "Model", otherTalent,
                    plan.getWorkflowInstance().getCurrentStatusCode().getStatusName());
            (isUpcoming ? upcoming : past).add(row);
        }
        upcoming.sort(Comparator.comparing(MyShootRow::getPlannedShootDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        past.sort(Comparator.comparing(MyShootRow::getPlannedShootDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("upcomingShoots", upcoming);
        model.addAttribute("pastShoots", past);
        model.addAttribute("upcomingShootsCount", upcoming.size());

        Optional<LocalDate> nextShootDate = upcoming.stream()
                .map(MyShootRow::getPlannedShootDate)
                .filter(java.util.Objects::nonNull)
                .findFirst();
        model.addAttribute("nextShootDateDisplay",
                nextShootDate.map(d -> d.format(DateTimeFormatter.ofPattern("dd MMM"))).orElse("--"));

        return "my-shoots";
    }
}
