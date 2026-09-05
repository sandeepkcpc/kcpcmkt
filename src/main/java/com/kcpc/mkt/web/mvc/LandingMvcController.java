package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.service.OperationalEligibilityService;
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
import com.kcpc.mkt.reporting.dto.PipelineChannelStatus;
import com.kcpc.mkt.reporting.dto.PipelinePlatformSummary;
import com.kcpc.mkt.reporting.service.PipelineDashboardService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.web.mvc.dto.ActiveWorkItem;
import com.kcpc.mkt.web.mvc.dto.CompletedWorkItem;
import com.kcpc.mkt.web.mvc.dto.MyShootRow;
import com.kcpc.mkt.web.mvc.dto.UpcomingWorkItem;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    private final com.kcpc.mkt.marks.repository.MarkCatalogueEntryRepository markCatalogueEntryRepository;
    private final com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository publicationEventRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final UserRepository userRepository;
    private final PipelineDashboardService pipelineDashboardService;
    private final PublishingService publishingService;
    private final AuthorizationService authorizationService;
    private final OperationalEligibilityService operationalEligibilityService;
    private final com.kcpc.mkt.workflow.service.AssignmentManagementQueueService assignmentManagementQueueService;

    private static final Set<WorkflowStatus> SHOOT_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.SA, WorkflowStatus.SIP, WorkflowStatus.SRV);
    private static final Set<WorkflowStatus> EDIT_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.EA, WorkflowStatus.ED, WorkflowStatus.ERV);
    private static final Set<WorkflowStatus> PUBLISH_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.RFP, WorkflowStatus.PUBG);
    // ENG-097: the genuinely PRE-Publishing statuses a Publisher's assigned plan can be at - Shoot/
    // Edit still in progress, including the two brief inter-stage transitional statuses (SAP, EAP).
    // Explicit allow-list (not "everything not Active/closed-out") on purpose: WorkflowStatus has
    // real statuses AFTER Publishing too (PP/PFUP - Performance Pending/Update, reached once
    // publication scope resolves - see PublishingService#recordActualPublication) that are neither
    // this Publishing active window nor a closed-out status, but are absolutely NOT "upcoming" from
    // a Publisher's own perspective (their actual-publication work is already done at that point) -
    // an allow-list for Upcoming, rather than a deny-list for History, is what correctly routes
    // those to History without needing to separately enumerate every non-Upcoming status that exists
    // or might be added later.
    private static final Set<WorkflowStatus> PUBLISH_UPCOMING_WINDOW = EnumSet.of(
            WorkflowStatus.SA, WorkflowStatus.SIP, WorkflowStatus.SRV, WorkflowStatus.SAP,
            WorkflowStatus.EA, WorkflowStatus.ED, WorkflowStatus.ERV, WorkflowStatus.EAP);

    // ENG-069: Content Pipeline KPI-card groupings - display-only, grouped by the row's own
    // friendly status label (WorkflowStatus.getStatusName()), never a new backend status.
    // Workflow redesign: no more Planning grouping - Planning is no longer a separate active-
    // workflow stage (see PipelineDashboardService#matchesStage).
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
                                 com.kcpc.mkt.marks.repository.MarkCatalogueEntryRepository markCatalogueEntryRepository,
                                 com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository publicationEventRepository,
                                 ReviewCycleRepository reviewCycleRepository,
                                 WorkHoldRecordRepository workHoldRecordRepository,
                                 WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                 ContentPlanTalentEntryRepository talentEntryRepository,
                                 UserRepository userRepository,
                                 PipelineDashboardService pipelineDashboardService,
                                 PublishingService publishingService,
                                 AuthorizationService authorizationService,
                                 OperationalEligibilityService operationalEligibilityService,
                                 com.kcpc.mkt.workflow.service.AssignmentManagementQueueService assignmentManagementQueueService) {
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.markAttributionRepository = markAttributionRepository;
        this.markCatalogueEntryRepository = markCatalogueEntryRepository;
        this.publicationEventRepository = publicationEventRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.userRepository = userRepository;
        this.pipelineDashboardService = pipelineDashboardService;
        this.publishingService = publishingService;
        this.authorizationService = authorizationService;
        this.operationalEligibilityService = operationalEligibilityService;
        this.assignmentManagementQueueService = assignmentManagementQueueService;
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

    /**
     * My Work &rarr; Dashboard filters (Publisher). Purely a display filter over the two Publishing
     * lists this method already builds - it never changes which assignments the Publisher is
     * allowed to see (that is decided solely by {@code findByPublisherAndActiveTrue} plus the
     * existing workflow-window bucketing above) and never touches permissions or workflow state.
     *
     * <p>Filtering is done server-side against real Content Plan data (Planned Live Date, and the
     * plan's actual Planned Output &rarr; Publication Target mappings), never by hiding rows in the
     * browser, so the rendered table and its count badge always agree with the underlying data.
     *
     * <p>The two tabs filter INDEPENDENTLY and are computed from different data sources - the
     * Dashboard's {@code dash*} parameters apply only to Upcoming Tasks, the Publishing tab's
     * {@code pub*} parameters only to Active Publishing Tasks. Neither tab's options, counts or
     * rows are derived from the other's list, and a submit from one tab round-trips the other's
     * parameters untouched, so changing a filter on one tab never disturbs the other.
     *
     * @param dashDate      Dashboard: exact Planned Live Date for Upcoming Tasks; null = every
     *                      date. A single date rather than a range - the Today/Tomorrow cards and
     *                      the picker all set this one parameter, so Today is simply
     *                      {@code dashDate=<today>} and carries no special server-side meaning
     * @param dashChannel   Dashboard: single channel handle; blank/null = All Channels
     * @param dashPlatforms Dashboard: zero or more platform names; empty/null = All Platforms.
     *                      ANDed with the other two - an Upcoming row must match all three
     * @param pubDate       Publishing: exact Planned Live Date for Active Publishing Tasks
     * @param pubChannel    Publishing: single channel handle; blank/null = All Channels
     * @param pubPlatforms  Publishing: zero or more platform names; empty/null = All Platforms
     * @param tab       stage tab to open on load ("dashboard"/"shoot"/"edit"/"publish"). Purely a
     *                  view concern: the filter panel is rendered above BOTH Publishing tables, so
     *                  this returns the user to whichever one they filtered from instead of
     *                  bouncing them to the first tab. Ignored if that tab is not rendered for
     *                  this employee, in which case my-work-tabs.js falls back to the first tab as
     *                  before. Never affects which rows or tabs the employee is allowed to see
     */
    @GetMapping("/app/my-work")
    public String myWork(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dashDate,
                         @RequestParam(required = false) String dashChannel,
                         @RequestParam(name = "dashPlatform", required = false) List<String> dashPlatforms,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate pubDate,
                         @RequestParam(required = false) String pubChannel,
                         @RequestParam(name = "pubPlatform", required = false) List<String> pubPlatforms,
                         @RequestParam(required = false) String tab) {
        User user = principal.user();
        model.addAttribute("user", user);
        model.addAttribute("accessClass", user.resolvedAccessClass());
        model.addAttribute("businessRoleName", user.getBusinessRole() == null ? null : user.getBusinessRole().getRoleName());

        // Task visibility comes from an active assignment, never Designation/Business Role alone -
        // and only for as long as that stage is still THIS employee's active work. The initial
        // Shoot Assignment is created at Idea Review approval time, atomically with the transition
        // to Shoot Assigned, so an active ShootingAssignment always coincides with the plan already
        // being approved; once a stage's own review has decided and the plan has moved on to the
        // next stage, that assignment drops out of Active Work and into Completed Work instead -
        // own-stage summary only, never the next stage's operational detail (ENG-038).
        List<ShootingAssignment> rawShootTasks = shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user);
        List<EditingAssignment> rawEditTasks = editingAssignmentRepository.findByEditorAndActiveTrue(user);
        List<PublishingAssignment> rawPublishTasks = publishingAssignmentRepository.findByPublisherAndActiveTrue(user);

        // BR-063 Hold/Resume: same "currently open hold" query TeamWorkloadService/
        // PipelineDashboardService already use - a purely supplementary badge, the row stays in
        // Active Work (never moved to History) and keeps its real stage/assignee either way.
        Set<UUID> onHoldWorkflowInstanceIds = workHoldRecordRepository.findByResumedAtIsNull().stream()
                .map(h -> h.getWorkflowInstance().getId()).collect(Collectors.toSet());

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
        // ENG-097: Publisher-only third bucket - a PublishingAssignment whose plan hasn't reached
        // Publishing yet (still Shoot/Edit) is neither Active nor genuinely completed History; kept
        // as its own list from the start (not filtered out of activeWork/completedWork afterward)
        // so it can never accidentally leak into either of those two stages' own tables.
        List<UpcomingWorkItem> upcomingPublishWork = new ArrayList<>();
        // My Work -> Dashboard filters: each Publishing row's real platform/channel breakdown,
        // keyed by Content Plan id. Populated in the Publishing loop below for BOTH the Active and
        // Upcoming branches (the Upcoming branch already built it for its own Platforms column -
        // it is captured here rather than recomputed, so no extra query is added for those rows),
        // and read only by the Channel/Platform filter predicate. Deliberately a side map rather
        // than a new ActiveWorkItem field: the filter needs this data, the Active Publishing table
        // does not render it, and ActiveWorkItem is shared with the Shoot/Edit rows.
        Map<UUID, List<PipelinePlatformSummary>> publishPlatformsByPlan = new HashMap<>();
        boolean anyActiveShootLead = false;
        String shootLeadDisplayName = null;
        boolean anyActiveEditLead = false;
        String editLeadDisplayName = null;
        int pendingTargetsTotal = 0;
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        for (ShootingAssignment t : rawShootTasks) {
            ContentPlan plan = t.getContentPlan();
            WorkflowStatus s = plan.getWorkflowInstance().getCurrentStatusCode();
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
                boolean onHold = onHoldWorkflowInstanceIds.contains(plan.getWorkflowInstance().getId());
                // Permission-driven workflow: the assignment is real (this loop only reaches
                // active-assignee rows), but Start/Continue/Submit itself additionally requires a
                // live PERM_18 grant covering this stage/item - if that has been revoked, the task
                // stays visible (never hidden) with execution suppressed instead.
                boolean shootBlocked = !operationalEligibilityService.isShootExecutionEligible(user, plan.getWorkflowInstance());
                activeWork.add(new ActiveWorkItem(plan.getId(), plan.getContentId(), contentTitle(plan), "Cameraperson",
                        plan.getContentPriority() == null ? null : plan.getContentPriority().name(),
                        priorityCssClass(plan.getContentPriority()),
                        plan.getPlannedShootDate(), lead == null ? null : lead.getCameraperson().getFullName(), isLead,
                        modelsByPlan.get(plan.getId()), statusLabel, statusCssClass(statusLabel), delayDays,
                        (onHold || shootBlocked) ? null : actionLabel(statusLabel, delayDays != null, "Cameraperson"),
                        plan.getFolderLink(), null, onHold, "SHOOT", false, shootBlocked));
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
                boolean onHold = onHoldWorkflowInstanceIds.contains(plan.getWorkflowInstance().getId());
                boolean editBlocked = !operationalEligibilityService.isEditExecutionEligible(user, plan.getWorkflowInstance());
                activeWork.add(new ActiveWorkItem(plan.getId(), plan.getContentId(), contentTitle(plan), "Editor",
                        plan.getContentPriority() == null ? null : plan.getContentPriority().name(),
                        priorityCssClass(plan.getContentPriority()),
                        plan.getPlannedEditDate(), lead == null ? null : lead.getEditor().getFullName(), isLead,
                        editorNames, statusLabel, statusCssClass(statusLabel), delayDays,
                        (onHold || editBlocked) ? null : actionLabel(statusLabel, delayDays != null, "Editor"),
                        plan.getFolderLink(), null, onHold, "EDIT", false, editBlocked));
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
                // Same "past planned date and not yet completed" rule as Shoot/Edit (ENG-058), based
                // on the Publishing-specific planned date - was previously hardcoded null, meaning a
                // Publishing task could never show as delayed even when genuinely past due.
                Integer delayDays = (plan.getPlannedLiveDate() != null && plan.getPlannedLiveDate().isBefore(today))
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(plan.getPlannedLiveDate(), today)
                        : null;
                boolean onHold = onHoldWorkflowInstanceIds.contains(plan.getWorkflowInstance().getId());
                boolean publishBlocked = !operationalEligibilityService.isPublishingExecutionEligible(user, plan.getWorkflowInstance());
                boolean repost = publishingService.currentPublishingCycleStart(plan.getWorkflowInstance()) != null;
                // Dashboard Channel/Platform filter input for this Active row - same builder the
                // Upcoming branch below already uses, so both lists filter on identical data.
                publishPlatformsByPlan.put(plan.getId(), pipelineDashboardService.buildPlatformSummariesForPlan(plan));
                activeWork.add(new ActiveWorkItem(plan.getId(), plan.getContentId(), contentTitle(plan), "Publisher",
                        plan.getContentPriority() == null ? null : plan.getContentPriority().name(),
                        priorityCssClass(plan.getContentPriority()),
                        plan.getPlannedLiveDate(), null, false, publisherNames, statusLabel, statusCssClass(statusLabel), delayDays,
                        (onHold || publishBlocked) ? null : actionLabel(statusLabel, delayDays != null, "Publisher"), plan.getFolderLink(),
                        targets.resolvedCount() + " / " + targets.totalCount(), onHold, "PUBLISH", repost, publishBlocked));
            } else if (PUBLISH_UPCOMING_WINDOW.contains(s)) {
                // ENG-097: still Shoot/Edit (not yet Publishing) - Upcoming, never History. Reuses
                // the same summarizeTargets call the Active branch above uses (no workflow-status
                // dependency in that method - safe regardless of current stage).
                PublishingService.TargetResolutionSummary targets = publishingService.summarizeTargets(plan);
                // My Work -> Dashboard: same "past Planned Live Date, not yet completed" formula
                // the Active branch above already uses - never a second/different delay calculation.
                Integer upcomingDelayDays = (plan.getPlannedLiveDate() != null && plan.getPlannedLiveDate().isBefore(today))
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(plan.getPlannedLiveDate(), today)
                        : null;
                // Built once and reused for both the row's own Platforms column and the Dashboard
                // Channel/Platform filter - never called twice for the same plan.
                List<PipelinePlatformSummary> upcomingPlatforms = pipelineDashboardService.buildPlatformSummariesForPlan(plan);
                publishPlatformsByPlan.put(plan.getId(), upcomingPlatforms);
                upcomingPublishWork.add(new UpcomingWorkItem(plan.getId(), plan.getContentId(), contentTitle(plan),
                        plan.getContentPriority() == null ? null : plan.getContentPriority().name(),
                        priorityCssClass(plan.getContentPriority()), plan.getPlannedLiveDate(), stageLabel(s),
                        targets.resolvedCount() + " / " + targets.totalCount(), plan.getFolderLink(), upcomingDelayDays,
                        upcomingPlatforms));
            } else {
                // Everything else (PP/PFUP once publication scope has resolved, COMP, and the
                // terminal/dormant CAN/RJ/RET) - genuinely done from the Publisher's own
                // perspective, never Upcoming. No review/decision gate exists for Publishing -
                // Final Result stays blank.
                completedWork.add(completedItem(plan, t.getId(), "PUBLISH", null, PUBLISH_ACTIVE_WINDOW,
                        plan.getPlannedLiveDate(), null, transitionsByInstance, reviewCyclesByInstance));
            }
        }
        upcomingPublishWork.sort(Comparator.comparing(UpcomingWorkItem::getPlannedDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        completedWork.sort(Comparator.comparing(CompletedWorkItem::getCompletedOn,
                Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("activeWork", activeWork);
        model.addAttribute("completedWork", completedWork);
        model.addAttribute("isShootLead", anyActiveShootLead);
        model.addAttribute("shootLeadDisplayName", shootLeadDisplayName);
        model.addAttribute("isEditLead", anyActiveEditLead);
        model.addAttribute("editLeadDisplayName", editLeadDisplayName);
        model.addAttribute("today", today);

        // Permission-driven multi-function My Work: Shoot/Edit/Publishing are now stage tabs
        // (never a single Business-Role-picked dashboard flavor), each shown when the employee
        // holds the matching live execution permission OR has real current/history assignment data
        // for that stage - so a permission holder with no assignment yet still sees their
        // (empty) tab, and someone with historical-only involvement still sees their history. The
        // KPI counts and tables both read from the exact same activeWork/completedWork lists built
        // above (filtered by roleLabel/stageWorked) - never a separate count query - so a
        // count/table mismatch is structurally impossible.
        List<ActiveWorkItem> shootActiveWork = activeWork.stream()
                .filter(item -> "Cameraperson".equals(item.getRoleLabel()))
                .sorted(Comparator.comparing(ActiveWorkItem::getPlannedDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<CompletedWorkItem> shootCompletedWork = completedWork.stream()
                .filter(item -> "SHOOT".equals(item.getStageWorked())).toList();
        model.addAttribute("shootActiveWork", shootActiveWork);
        model.addAttribute("shootCompletedWork", shootCompletedWork);
        model.addAttribute("activeShootsCount", shootActiveWork.size());
        model.addAttribute("reworkRequiredCount",
                shootActiveWork.stream().filter(item -> "Rework Required".equals(item.getStatusLabel())).count());
        model.addAttribute("delayedShootsCount", shootActiveWork.stream().filter(ActiveWorkItem::isDelayed).count());
        model.addAttribute("completedShootsCount", shootCompletedWork.size());
        boolean hasShootExecutionPermission =
                authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_18_SHOOT_EXECUTION);
        model.addAttribute("hasShootExecutionPermission", hasShootExecutionPermission);
        model.addAttribute("showShootTab",
                hasShootExecutionPermission || !shootActiveWork.isEmpty() || !shootCompletedWork.isEmpty());

        List<ActiveWorkItem> editActiveWork = activeWork.stream()
                .filter(item -> "Editor".equals(item.getRoleLabel()))
                .sorted(Comparator.comparing(ActiveWorkItem::getPlannedDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<CompletedWorkItem> editCompletedWork = completedWork.stream()
                .filter(item -> "EDIT".equals(item.getStageWorked())).toList();
        model.addAttribute("editActiveWork", editActiveWork);
        model.addAttribute("editCompletedWork", editCompletedWork);
        model.addAttribute("activeEditsCount", editActiveWork.size());
        model.addAttribute("editReworkRequiredCount",
                editActiveWork.stream().filter(item -> "Rework Required".equals(item.getStatusLabel())).count());
        model.addAttribute("editDelayedCount", editActiveWork.stream().filter(ActiveWorkItem::isDelayed).count());
        model.addAttribute("editCompletedCount", editCompletedWork.size());
        boolean hasEditExecutionPermission =
                authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_19_EDIT_EXECUTION);
        model.addAttribute("hasEditExecutionPermission", hasEditExecutionPermission);
        model.addAttribute("showEditTab",
                hasEditExecutionPermission || !editActiveWork.isEmpty() || !editCompletedWork.isEmpty());

        // "Pending Targets" is a genuinely different kind of count from the other two stages' KPIs -
        // a sum of unresolved (Planned Output, Publication Target) pairs across every active row,
        // not a row count - accumulated once above in the same loop that builds each row's own
        // "resolved / total" Targets column, so the KPI number and the table's per-row figures can
        // never drift apart.
        List<ActiveWorkItem> publishActiveWork = activeWork.stream()
                .filter(item -> "Publisher".equals(item.getRoleLabel()))
                .sorted(Comparator.comparing(ActiveWorkItem::getPlannedDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<CompletedWorkItem> publishCompletedWork = completedWork.stream()
                .filter(item -> "PUBLISH".equals(item.getStageWorked())).toList();

        // ----- My Work Publishing filters --------------------------------------------------
        // TWO INDEPENDENT filter scopes, one per tab, deliberately not one shared set:
        //
        //   Dashboard  (dash* parameters) filters Upcoming Tasks           - upcomingPublishWork
        //   Publishing (pub*  parameters) filters Active Publishing Tasks  - publishActiveWork
        //
        // Each tab's dropdown options, Today/Tomorrow counts and row filtering are computed from
        // that tab's OWN list only, so the two never borrow each other's numbers, and applying or
        // clearing a filter on one tab leaves the other exactly as it was. The panels round-trip
        // the other tab's parameters untouched (hidden inputs in the form, and encoded into the
        // quick-pick card links), which is what keeps them independent across a submit.
        Set<String> dashPlatformOptions = new TreeSet<>();
        Set<String> dashChannelOptions = new TreeSet<>();
        collectFilterOptions(upcomingPublishWork.stream().map(UpcomingWorkItem::getContentPlanId).toList(),
                publishPlatformsByPlan, dashPlatformOptions, dashChannelOptions);
        Set<String> pubPlatformOptions = new TreeSet<>();
        Set<String> pubChannelOptions = new TreeSet<>();
        collectFilterOptions(publishActiveWork.stream().map(ActiveWorkItem::getContentPlanId).toList(),
                publishPlatformsByPlan, pubPlatformOptions, pubChannelOptions);

        Set<String> dashSelectedPlatforms = cleanPlatforms(dashPlatforms);
        String dashSelectedChannel = notBlank(dashChannel) ? dashChannel : null;
        boolean dashFilterActive = dashDate != null || dashSelectedChannel != null || !dashSelectedPlatforms.isEmpty();

        Set<String> pubSelectedPlatforms = cleanPlatforms(pubPlatforms);
        String pubSelectedChannel = notBlank(pubChannel) ? pubChannel : null;
        boolean pubFilterActive = pubDate != null || pubSelectedChannel != null || !pubSelectedPlatforms.isEmpty();

        LocalDate tomorrow = today.plusDays(1);

        // Today/Tomorrow counts, per tab, from that tab's own list. They also honour that tab's
        // CURRENT Channel/Platform selection (only the date criterion is swapped for the card's
        // own date), so a card's number is exactly how many rows clicking it would show.
        long dashTodayCount = countRowsOn(today, upcomingPublishWork, UpcomingWorkItem::getContentPlanId,
                UpcomingWorkItem::getPlannedDate, publishPlatformsByPlan, dashSelectedChannel, dashSelectedPlatforms);
        long dashTomorrowCount = countRowsOn(tomorrow, upcomingPublishWork, UpcomingWorkItem::getContentPlanId,
                UpcomingWorkItem::getPlannedDate, publishPlatformsByPlan, dashSelectedChannel, dashSelectedPlatforms);
        long pubTodayCount = countRowsOn(today, publishActiveWork, ActiveWorkItem::getContentPlanId,
                ActiveWorkItem::getPlannedDate, publishPlatformsByPlan, pubSelectedChannel, pubSelectedPlatforms);
        long pubTomorrowCount = countRowsOn(tomorrow, publishActiveWork, ActiveWorkItem::getContentPlanId,
                ActiveWorkItem::getPlannedDate, publishPlatformsByPlan, pubSelectedChannel, pubSelectedPlatforms);

        // Quick-pick card links: a link submits no form, so each one must carry BOTH its own tab's
        // Channel/Platform selection AND the other tab's whole filter state, or clicking a card on
        // one tab would silently clear the other tab's filters.
        model.addAttribute("dashTodayQs", myWorkFilterQs(today, dashSelectedChannel, dashSelectedPlatforms,
                pubDate, pubSelectedChannel, pubSelectedPlatforms, true));
        model.addAttribute("dashTomorrowQs", myWorkFilterQs(tomorrow, dashSelectedChannel, dashSelectedPlatforms,
                pubDate, pubSelectedChannel, pubSelectedPlatforms, true));
        model.addAttribute("pubTodayQs", myWorkFilterQs(today, pubSelectedChannel, pubSelectedPlatforms,
                dashDate, dashSelectedChannel, dashSelectedPlatforms, false));
        model.addAttribute("pubTomorrowQs", myWorkFilterQs(tomorrow, pubSelectedChannel, pubSelectedPlatforms,
                dashDate, dashSelectedChannel, dashSelectedPlatforms, false));

        // Clear links: keep the OTHER tab's filter, drop only this tab's. Always ends in "?" or
        // "...&" so the JSP can append "tab=<name>" directly.
        model.addAttribute("dashClearQs", clearQs(pubDate, pubSelectedChannel, pubSelectedPlatforms, "pub"));
        model.addAttribute("pubClearQs", clearQs(dashDate, dashSelectedChannel, dashSelectedPlatforms, "dash"));

        model.addAttribute("todayDate", today);
        model.addAttribute("tomorrowDate", tomorrow);

        model.addAttribute("dashPlatformOptions", dashPlatformOptions);
        model.addAttribute("dashChannelOptions", dashChannelOptions);
        model.addAttribute("dashDateParam", dashDate);
        model.addAttribute("dashChannelParam", dashSelectedChannel);
        model.addAttribute("dashPlatformParams", dashSelectedPlatforms);
        model.addAttribute("dashFilterActive", dashFilterActive);
        model.addAttribute("dashTodayCount", dashTodayCount);
        model.addAttribute("dashTomorrowCount", dashTomorrowCount);
        model.addAttribute("dashTodaySelected", today.equals(dashDate));
        model.addAttribute("dashTomorrowSelected", tomorrow.equals(dashDate));
        // A date that is neither Today nor Tomorrow came from the "Select Date" picker - that
        // card is then the highlighted one.
        model.addAttribute("dashCustomDateSelected", dashDate != null
                && !today.equals(dashDate) && !tomorrow.equals(dashDate));

        model.addAttribute("pubPlatformOptions", pubPlatformOptions);
        model.addAttribute("pubChannelOptions", pubChannelOptions);
        model.addAttribute("pubDateParam", pubDate);
        model.addAttribute("pubChannelParam", pubSelectedChannel);
        model.addAttribute("pubPlatformParams", pubSelectedPlatforms);
        model.addAttribute("pubFilterActive", pubFilterActive);
        model.addAttribute("pubTodayCount", pubTodayCount);
        model.addAttribute("pubTomorrowCount", pubTomorrowCount);
        model.addAttribute("pubTodaySelected", today.equals(pubDate));
        model.addAttribute("pubTomorrowSelected", tomorrow.equals(pubDate));
        model.addAttribute("pubCustomDateSelected", pubDate != null
                && !today.equals(pubDate) && !tomorrow.equals(pubDate));

        // Counts/tab-visibility are deliberately computed from the UNFILTERED lists, BEFORE the
        // filter is applied: the KPI cards summarise this Publisher's whole workload (not the
        // current view), and - critically - showPublishTab must never depend on the filter, or a
        // filter matching zero rows would hide the Dashboard tab that carries the filter panel
        // itself, leaving no way to clear it.
        int upcomingPublishingTotal = upcomingPublishWork.size();
        int activePublishingTotal = publishActiveWork.size();
        long delayedPublishingTotal = publishActiveWork.stream().filter(ActiveWorkItem::isDelayed).count();
        boolean anyUnfilteredPublishWork = !upcomingPublishWork.isEmpty() || !publishActiveWork.isEmpty();

        List<UpcomingWorkItem> upcomingPublishWorkFiltered = upcomingPublishWork.stream()
                .filter(item -> matchesPublishFilter(item.getPlannedDate(), publishPlatformsByPlan.get(item.getContentPlanId()),
                        dashDate, dashSelectedChannel, dashSelectedPlatforms))
                .toList();
        List<ActiveWorkItem> publishActiveWorkFiltered = publishActiveWork.stream()
                .filter(item -> matchesPublishFilter(item.getPlannedDate(), publishPlatformsByPlan.get(item.getContentPlanId()),
                        pubDate, pubSelectedChannel, pubSelectedPlatforms))
                .toList();

        // View-only: pre-selects a stage tab. my-work-tabs.js already honours a server-rendered
        // "active" button and otherwise falls back to the first tab, so an unknown/absent value
        // simply keeps the existing default behaviour.
        model.addAttribute("activeStageTab", notBlank(tab) ? tab : null);

        model.addAttribute("publishActiveWork", publishActiveWorkFiltered);
        model.addAttribute("publishCompletedWork", publishCompletedWork);
        // Platforms column for the Publishing tab's Active Publishing Tasks table, keyed by
        // Content Plan id. Exactly the same PipelinePlatformSummary data the Dashboard's Upcoming
        // Tasks rows already carry inline (and that the filter above matches on), exposed as a map
        // rather than added to ActiveWorkItem: that DTO is shared with the Shoot and Edit rows,
        // which have no platform concept, and it is already built here for the filter - so this
        // adds a lookup, not a second data source or an extra query.
        model.addAttribute("publishPlatformsByPlan", publishPlatformsByPlan);
        // ENG-097: upcomingPublishWork is already its own dedicated list (built directly in the
        // main Publishing loop above, never filtered out of activeWork/completedWork afterward), so
        // it's used as-is - already sorted by Planned Live Date ascending.
        model.addAttribute("upcomingPublishWork", upcomingPublishWorkFiltered);
        // KPI-card counts: unfiltered totals (this Publisher's whole workload). The two filtered
        // tables show their own row counts via fn:length() on the lists above, so a filtered table
        // header never contradicts the rows underneath it.
        model.addAttribute("upcomingPublishingCount", upcomingPublishingTotal);
        model.addAttribute("activePublishingCount", activePublishingTotal);
        model.addAttribute("pendingTargetsCount", pendingTargetsTotal);
        model.addAttribute("delayedPublishingCount", delayedPublishingTotal);
        model.addAttribute("publishCompletedCount", publishCompletedWork.size());
        boolean hasPublishingExecutionPermission =
                authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_08_PUBLISHING_EXECUTION);
        model.addAttribute("hasPublishingExecutionPermission", hasPublishingExecutionPermission);
        // anyUnfilteredPublishWork (not the filtered lists) - see the note above: the Dashboard tab
        // carries the filter panel, so its visibility must never depend on the filter's own result.
        model.addAttribute("showPublishTab",
                hasPublishingExecutionPermission || anyUnfilteredPublishWork
                        || !publishCompletedWork.isEmpty());

        // Marks moved to the dedicated /app/my-performance page (see #myPerformance below) - My
        // Work stays scoped to work management (Active Work/History) only, never marks/performance.

        // Assignment Management (PERM_04/06/11 - assignment authority, distinct from execution) -
        // a delegated, actionable queue, never a historical/broader list (see
        // AssignmentManagementQueueService). Shown as a separate "Execution | Assignment
        // Management" tab tier only when the user actually holds one of these permissions -
        // otherwise the tier itself stays hidden, not just empty.
        boolean hasAssignmentManagementPermission = authorizationService.hasAnyActiveGrant(user,
                OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, OperationalPermission.PERM_06_EDIT_ASSIGNMENT,
                OperationalPermission.PERM_11_REASSIGN);
        model.addAttribute("showAssignmentManagementTier", hasAssignmentManagementPermission);
        if (hasAssignmentManagementPermission) {
            model.addAttribute("shootAssignmentQueue", assignmentManagementQueueService.shootQueue(user));
            model.addAttribute("editAssignmentQueue", assignmentManagementQueueService.editQueue(user));
        }

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

    // ================================================================= My Performance =========

    /**
     * "/app/my-performance" - the employee's own personal performance dashboard, split out of My
     * Work's former "Marks" sub-tab (ENG-067-follow-up). Strictly employee-scoped: {@code user} is
     * resolved from the authenticated principal ONLY, exactly like every other page in this
     * controller (My Work, My Shoots) - there is no request parameter anywhere on this route that
     * can name a different employee, so a direct/typed URL can never expose another employee's
     * marks/tasks (see also the tests: an attempt to add a userId/employeeId query param is simply
     * ignored, never bound to anything this handler reads).
     *
     * <p>Reuses the exact same sources of truth as My Work's own Completed Work list and My
     * Shoots' Upcoming/Past split, never a second/parallel definition:
     * <ul>
     *   <li>"completed" for Cameraperson/Editor/Publisher = the plan's current status has left that
     *   stage's own {@code *_ACTIVE_WINDOW} (identical to {@link #myWork}'s own bucketing).</li>
     *   <li>"completed" for Model/Talent = {@link #isShootTaskCompleted} (identical to My Shoots).</li>
     *   <li>marks = {@link PersonalMarkAttributionRepository#findByRecipient} (the one mark ledger,
     *   never re-derived) - Publisher rows simply have no mark, since RoleType has no PUBLISHER
     *   value and this system has never attributed one; they still appear for their completion/
     *   delay/on-time contribution, just excluded from Total/Average Mark.</li>
     *   <li>delay = completedOn (the same transition-history-derived timestamp {@link
     *   #completedItem} already computes) vs the stage's own planned date (plannedShootDate for
     *   Shoot/Model, plannedEditDate for Edit, plannedLiveDate for Publish) - there was no existing
     *   "was this already-finished task late" calculation anywhere in the codebase (the only
     *   existing delay logic, StageDelayPolicy, is for a still-open task's live overdue check), so
     *   this is newly written from existing inputs, not a duplicate of anything.</li>
     * </ul>
     *
     * <p>KPI cards and the Marks/Delay summaries are scoped to the date range ONLY (never the
     * stage/role/status/delay/search filters below them) - matching "(Selected Range)" answering
     * "how did I do in this window", independent of whichever slice of that window the Task
     * Performance table happens to be narrowed to. The table itself applies every filter together,
     * server-side, over the full row set (never a second client-side-filtered copy of the data).
     */
    @GetMapping("/app/my-performance")
    public String myPerformance(@AuthenticationPrincipal KcpcUserPrincipal principal,
                                 @RequestParam(required = false) String stage,
                                 @RequestParam(required = false) String role,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String delay,
                                 @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                 @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate toDate,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false, defaultValue = "1") int page,
                                 @RequestParam(required = false, defaultValue = "10") int pageSize,
                                 Model model) {
        User user = principal.user();
        model.addAttribute("user", user);
        model.addAttribute("businessRoleName", user.getBusinessRole() == null ? null : user.getBusinessRole().getRoleName());
        // Mark visibility (UI only, never affects the underlying calculation/data below): the
        // Total Marks KPI card and the Mark column are only meaningful for a Shoot/Cameraperson or
        // Edit/Editor-execution-eligible employee - a Publisher-only (or Model/Talent-only, which is
        // never a mark-eligibility signal on its own) employee has no mark scale of their own and
        // would otherwise see an always-empty "Mark: —" column. Base-role-blind by design, exactly
        // like every other tab-visibility gate in this controller (#myWork's showShootTab/
        // showEditTab/showPublishTab): permission-driven, never Business Role-driven.
        boolean markVisibilityEligible = authorizationService.hasAnyActiveGrant(user,
                OperationalPermission.PERM_18_SHOOT_EXECUTION, OperationalPermission.PERM_19_EDIT_EXECUTION);
        model.addAttribute("markVisibilityEligible", markVisibilityEligible);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("stage", stage == null ? "" : stage);
        model.addAttribute("role", role == null ? "" : role);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("delay", delay == null ? "" : delay);
        model.addAttribute("q", q == null ? "" : q);

        List<com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow> allRows = buildPerformanceRows(user);

        List<com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow> inRange = allRows.stream()
                .filter(r -> withinDateRange(r.getCompletedOn(), fromDate, toDate))
                .toList();

        // --------------------------------------------------------------- KPI cards (date range only)
        List<com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow> markedInRange =
                inRange.stream().filter(r -> r.getMark() != null).toList();
        java.math.BigDecimal totalMarks = markedInRange.stream().map(com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow::getMark)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal possibleMarks = markedInRange.stream().map(com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow::getMarkMax)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal averageMark = markedInRange.isEmpty() ? null
                : totalMarks.divide(java.math.BigDecimal.valueOf(markedInRange.size()), 2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal averageMarkMax = markedInRange.isEmpty() ? null
                : possibleMarks.divide(java.math.BigDecimal.valueOf(markedInRange.size()), 2, java.math.RoundingMode.HALF_UP);
        long tasksCompleted = inRange.size();
        long delayedTasks = inRange.stream().filter(r -> "DELAYED".equals(r.getDelayStatus())).count();
        long onTimeTasks = inRange.stream().filter(r -> "ON_TIME".equals(r.getDelayStatus())).count();
        Integer onTimeRatePercent = tasksCompleted == 0 ? null : (int) Math.round(onTimeTasks * 100.0 / tasksCompleted);

        model.addAttribute("totalMarks", totalMarks);
        model.addAttribute("possibleMarks", possibleMarks);
        model.addAttribute("averageMark", averageMark);
        model.addAttribute("averageMarkMax", averageMarkMax);
        model.addAttribute("tasksCompletedCount", tasksCompleted);
        model.addAttribute("delayedTasksCount", delayedTasks);
        model.addAttribute("onTimeTasksCount", onTimeTasks);
        model.addAttribute("onTimeRatePercent", onTimeRatePercent);

        // --------------------------------------------------------------- Marks Summary (date range only)
        Map<String, List<com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow>> markedByGroup = markedInRange.stream()
                .collect(Collectors.groupingBy(r -> stageLabel(r.getStage()) + " (" + r.getRoleLabel() + ")", LinkedHashMap::new, Collectors.toList()));
        List<com.kcpc.mkt.web.mvc.dto.PerformanceMarksSummaryEntry> marksSummary = markedByGroup.entrySet().stream()
                .map(e -> {
                    java.math.BigDecimal groupTotal = e.getValue().stream().map(com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow::getMark)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    java.math.BigDecimal groupMax = e.getValue().stream().map(com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow::getMarkMax)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    int percent = groupMax.signum() == 0 ? 0
                            : groupTotal.multiply(java.math.BigDecimal.valueOf(100))
                                    .divide(groupMax, 0, java.math.RoundingMode.HALF_UP).intValue();
                    return new com.kcpc.mkt.web.mvc.dto.PerformanceMarksSummaryEntry(e.getKey(), groupTotal, groupMax, percent);
                })
                .sorted(Comparator.comparing(com.kcpc.mkt.web.mvc.dto.PerformanceMarksSummaryEntry::getLabel))
                .toList();
        model.addAttribute("marksSummary", marksSummary);

        // --------------------------------------------------------------- Delay Summary (date range only)
        List<Integer> delayedDays = inRange.stream().filter(r -> "DELAYED".equals(r.getDelayStatus()))
                .map(com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow::getDelayDays).toList();
        Double averageDelayDays = delayedDays.isEmpty() ? null
                : delayedDays.stream().mapToInt(Integer::intValue).average().orElse(0);
        Integer mostDelayedDays = delayedDays.isEmpty() ? null : delayedDays.stream().max(Integer::compareTo).orElse(null);
        model.addAttribute("totalDelayedCount", delayedDays.size());
        model.addAttribute("averageDelayDays", averageDelayDays == null ? null
                : java.math.BigDecimal.valueOf(averageDelayDays).setScale(1, java.math.RoundingMode.HALF_UP));
        model.addAttribute("mostDelayedDays", mostDelayedDays);

        // --------------------------------------------------------------- Task Performance table (all filters)
        String qLower = q == null ? "" : q.trim().toLowerCase();
        List<com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow> filtered = allRows.stream()
                .filter(r -> withinDateRange(r.getCompletedOn(), fromDate, toDate))
                .filter(r -> isBlankOrAll(stage) || stage.equalsIgnoreCase(r.getStage()))
                .filter(r -> isBlankOrAll(role) || role.equalsIgnoreCase(r.getRoleLabel()))
                .filter(r -> matchesStatusFilter(r, status))
                .filter(r -> isBlankOrAll(delay) || delay.equalsIgnoreCase(r.getDelayStatus()))
                .filter(r -> qLower.isEmpty() || r.getContentId().toLowerCase().contains(qLower)
                        || r.getTitle().toLowerCase().contains(qLower))
                .sorted(Comparator.comparing(com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow::getCompletedOn,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int effectivePageSize = pageSize == 25 || pageSize == 50 ? pageSize : 10;
        int totalCount = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) effectivePageSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIdx = Math.min((currentPage - 1) * effectivePageSize, totalCount);
        int toIdx = Math.min(fromIdx + effectivePageSize, totalCount);
        model.addAttribute("performanceRows", filtered.subList(fromIdx, toIdx));
        model.addAttribute("performanceRowsTotalCount", totalCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", effectivePageSize);

        return "my-performance";
    }

    private static boolean isBlankOrAll(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value);
    }

    /** Status is deliberately literal per spec: "Completed" always matches (every row here already
     * IS a completed task by construction) and "Delayed"/"On Time" mirror the Delay filter's own
     * values - kept as two independently-working filters rather than merged, exactly as specified. */
    private static boolean matchesStatusFilter(com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow row, String status) {
        if (isBlankOrAll(status) || "COMPLETED".equalsIgnoreCase(status)) {
            return true;
        }
        return status.equalsIgnoreCase(row.getDelayStatus());
    }

    private static boolean withinDateRange(Instant completedOn, LocalDate fromDate, LocalDate toDate) {
        if (completedOn == null) {
            return fromDate == null && toDate == null;
        }
        LocalDate completedDate = completedOn.atZone(BUSINESS_ZONE).toLocalDate();
        if (fromDate != null && completedDate.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !completedDate.isAfter(toDate);
    }

    private static String stageLabel(String stage) {
        return switch (stage) {
            case "SHOOT" -> "Shoot";
            case "EDIT" -> "Edit";
            case "PUBLISH" -> "Publishing";
            default -> stage;
        };
    }

    /**
     * Every completed performance-bearing involvement this employee has ever had, across all four
     * participation mechanisms - unfiltered, all-time. Mirrors {@link #myWork}'s own batched-query
     * shape (transitions/review cycles fetched once for every relevant WorkflowInstance, never
     * per-row) to avoid N+1, and reuses {@link #completedItem} for finalResult/remarks (and, for
     * Shoot/Edit/Model, completedOn too - there is no completion signal in this data model finer
     * than the one shared stage-review-approval event those roles' teammates all share). Publisher
     * rows are the one exception: each Publisher's own {@code completedOn} is instead derived from
     * their own {@code ActualPublicationEvent} rows (see {@link #toPerformanceRow}), never the
     * plan-wide "all targets published" transition a co-assigned Publisher would otherwise share.
     */
    private List<com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow> buildPerformanceRows(User user) {
        List<com.kcpc.mkt.marks.domain.PersonalMarkAttribution> myMarks = markAttributionRepository.findByRecipient(user);
        Map<UUID, List<com.kcpc.mkt.marks.domain.PersonalMarkAttribution>> marksByPlan = myMarks.stream()
                .collect(Collectors.groupingBy(m -> m.getContentPlan().getId()));

        List<ShootingAssignment> shootTasks = shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user);
        List<EditingAssignment> editTasks = editingAssignmentRepository.findByEditorAndActiveTrue(user);
        List<PublishingAssignment> publishTasks = publishingAssignmentRepository.findByPublisherAndActiveTrue(user);
        List<ContentPlanTalentEntry> talentEntries = talentEntryRepository.findByTalentUser(user);

        // ContentPlanTalentEntry#contentPlan is LAZY (unlike ShootingAssignment/EditingAssignment/
        // PublishingAssignment#contentPlan, which are EAGER - see ShootingAssignment's own ENG-005
        // comment) - walking straight to .getWorkflowInstance() on it would throw
        // LazyInitializationException once the entry's own fetch has closed its session. #getId()
        // on the proxy itself is always safe (Hibernate resolves it from the FK column, no lazy
        // init needed), so batch-fetch the real ContentPlan rows the same way #myShoots already does.
        Set<UUID> talentPlanIds = talentEntries.stream().map(t -> t.getContentPlan().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, ContentPlan> talentPlansById = talentPlanIds.isEmpty() ? Map.of()
                : contentPlanRepository.findAllById(talentPlanIds).stream()
                        .collect(Collectors.toMap(ContentPlan::getId, p -> p));

        Set<UUID> instanceIds = new LinkedHashSet<>();
        shootTasks.forEach(t -> instanceIds.add(t.getContentPlan().getWorkflowInstance().getId()));
        editTasks.forEach(t -> instanceIds.add(t.getContentPlan().getWorkflowInstance().getId()));
        publishTasks.forEach(t -> instanceIds.add(t.getContentPlan().getWorkflowInstance().getId()));
        talentPlansById.values().forEach(p -> instanceIds.add(p.getWorkflowInstance().getId()));

        Map<UUID, List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory>> transitionsByInstance = instanceIds.isEmpty()
                ? Map.of()
                : transitionHistoryRepository.findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(instanceIds).stream()
                        .collect(Collectors.groupingBy(t -> t.getWorkflowInstance().getId()));
        Map<UUID, List<ReviewCycle>> reviewCyclesByInstance = instanceIds.isEmpty()
                ? Map.of()
                : reviewCycleRepository.findByWorkflowInstance_IdIn(instanceIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getWorkflowInstance().getId()));

        // Unlike Shoot/Edit (whose only completion signal is the one shared Shoot/Edit Review
        // approval event, identical for every teammate - see the class-level note on this method),
        // Publishing genuinely does carry a per-publisher completion signal: ActualPublicationEvent
        // records WHO (publishedBy) recorded WHICH target, WHEN (actualPublicationTimestamp) - so a
        // Publisher's own "Completed On" must be derived from their own events here, never from the
        // plan-wide "all targets published" transition every co-assigned Publisher would otherwise
        // share (see #toPerformanceRow's publisherIdentity handling below).
        Set<UUID> publishPlanIds = publishTasks.stream().map(t -> t.getContentPlan().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, List<com.kcpc.mkt.publishing.domain.ActualPublicationEvent>> publicationEventsByPlan = publishPlanIds.isEmpty()
                ? Map.of()
                : publicationEventRepository.findByContentPlan_IdIn(publishPlanIds).stream()
                        .collect(Collectors.groupingBy(e -> e.getContentPlan().getId()));

        List<com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow> rows = new ArrayList<>();

        for (ShootingAssignment t : shootTasks) {
            ContentPlan plan = t.getContentPlan();
            if (!SHOOT_ACTIVE_WINDOW.contains(plan.getWorkflowInstance().getCurrentStatusCode())) {
                rows.add(toPerformanceRow(plan, "SHOOT", "Cameraperson", com.kcpc.mkt.marks.domain.RoleType.CAMERAPERSON,
                        SHOOT_ACTIVE_WINDOW, GateType.SHOOT_REVIEW, plan.getPlannedShootDate(),
                        marksByPlan, transitionsByInstance, reviewCyclesByInstance, null, publicationEventsByPlan));
            }
        }
        for (EditingAssignment t : editTasks) {
            ContentPlan plan = t.getContentPlan();
            if (!EDIT_ACTIVE_WINDOW.contains(plan.getWorkflowInstance().getCurrentStatusCode())) {
                rows.add(toPerformanceRow(plan, "EDIT", "Editor", com.kcpc.mkt.marks.domain.RoleType.EDITOR,
                        EDIT_ACTIVE_WINDOW, GateType.EDIT_REVIEW, plan.getPlannedEditDate(),
                        marksByPlan, transitionsByInstance, reviewCyclesByInstance, null, publicationEventsByPlan));
            }
        }
        for (PublishingAssignment t : publishTasks) {
            ContentPlan plan = t.getContentPlan();
            if (!PUBLISH_ACTIVE_WINDOW.contains(plan.getWorkflowInstance().getCurrentStatusCode())) {
                // Publishing has no mark-attribution gate and no RoleType of its own (see class
                // javadoc) - roleType stays null, so toPerformanceRow never looks up a mark for it.
                // publisherIdentity = t.getPublisher(): this row's "Completed On" must be THIS
                // Publisher's own recorded publication(s), never a co-assigned Publisher's.
                rows.add(toPerformanceRow(plan, "PUBLISH", "Publisher", null,
                        PUBLISH_ACTIVE_WINDOW, null, plan.getPlannedLiveDate(),
                        marksByPlan, transitionsByInstance, reviewCyclesByInstance, t.getPublisher(), publicationEventsByPlan));
            }
        }
        for (ContentPlanTalentEntry t : talentEntries) {
            ContentPlan plan = talentPlansById.get(t.getContentPlan().getId());
            if (plan == null) {
                continue;
            }
            List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory> history =
                    transitionsByInstance.getOrDefault(plan.getWorkflowInstance().getId(), List.of());
            // Same completion definition My Shoots' own Upcoming/Past split already uses - a Model's
            // task is done once the Shoot phase itself has concluded, never re-derived a second way.
            if (isShootTaskCompleted(history)) {
                rows.add(toPerformanceRow(plan, "SHOOT", "Model", com.kcpc.mkt.marks.domain.RoleType.MODEL,
                        SHOOT_ACTIVE_WINDOW, GateType.SHOOT_REVIEW, plan.getPlannedShootDate(),
                        marksByPlan, transitionsByInstance, reviewCyclesByInstance, null, publicationEventsByPlan));
            }
        }
        return rows;
    }

    private com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow toPerformanceRow(
            ContentPlan plan, String stage, String roleLabel, com.kcpc.mkt.marks.domain.RoleType roleType,
            Set<WorkflowStatus> activeWindow, GateType gateType, LocalDate plannedDate,
            Map<UUID, List<com.kcpc.mkt.marks.domain.PersonalMarkAttribution>> marksByPlan,
            Map<UUID, List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory>> transitionsByInstance,
            Map<UUID, List<ReviewCycle>> reviewCyclesByInstance, User publisherIdentity,
            Map<UUID, List<com.kcpc.mkt.publishing.domain.ActualPublicationEvent>> publicationEventsByPlan) {
        CompletedWorkItem item = completedItem(plan, null, stage, gateType, activeWindow, plannedDate, null,
                transitionsByInstance, reviewCyclesByInstance);

        // Employee-specific completion: Shoot/Edit/Model have no completion signal finer than the
        // one shared stage-review-approval event (see #buildPerformanceRows), so item.getCompletedOn
        // stays authoritative for them. Publishing is different - ActualPublicationEvent genuinely
        // records WHO published WHAT, WHEN, so a Publisher's own "Completed On" must be THIS
        // publisher's own latest recorded event, never the plan-wide "all targets done" transition
        // every co-assigned Publisher would otherwise share. If this publisher personally recorded
        // no event on this plan, completedOn stays null (no fabricated/borrowed date) - the row is
        // then correctly excluded once any Completed-On date-range filter is applied.
        Instant completedOn = item.getCompletedOn();
        if (publisherIdentity != null) {
            completedOn = publicationEventsByPlan.getOrDefault(plan.getId(), List.of()).stream()
                    .filter(e -> e.getPublishedBy() != null && e.getPublishedBy().getId().equals(publisherIdentity.getId()))
                    .map(com.kcpc.mkt.publishing.domain.ActualPublicationEvent::getActualPublicationTimestamp)
                    .filter(java.util.Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(null);
        }

        Integer delayDays = null;
        String delayStatus = null;
        if (completedOn != null && plannedDate != null) {
            LocalDate completedDate = completedOn.atZone(BUSINESS_ZONE).toLocalDate();
            delayDays = (int) java.time.temporal.ChronoUnit.DAYS.between(plannedDate, completedDate);
            delayStatus = delayDays > 0 ? "DELAYED" : delayDays < 0 ? "EARLY" : "ON_TIME";
        }

        java.math.BigDecimal mark = null;
        java.math.BigDecimal markMax = null;
        if (roleType != null) {
            mark = marksByPlan.getOrDefault(plan.getId(), List.of()).stream()
                    .filter(m -> m.getRoleType() == roleType)
                    .map(com.kcpc.mkt.marks.domain.PersonalMarkAttribution::getAttributedMarkValue)
                    .findFirst().orElse(null);
            if (mark != null) {
                List<com.kcpc.mkt.marks.domain.MarkCatalogueEntry> activeEntries =
                        markCatalogueEntryRepository.findByRoleTypeAndActiveTrueOrderByMarkValueAsc(roleType);
                markMax = activeEntries.isEmpty() ? mark : activeEntries.get(activeEntries.size() - 1).getMarkValue();
            }
        }

        return new com.kcpc.mkt.web.mvc.dto.EmployeePerformanceRow(plan.getId(), plan.getContentId(), contentTitle(plan),
                stage, roleLabel, completedOn, plannedDate, delayDays, delayStatus, mark, markMax,
                item.getFinalResult(), item.getRemarks());
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

    /**
     * ENG-097: which stage a Content Plan is actually in right now - for the Upcoming Publishing
     * table's "Current Stage" column only (never the assignment's own role/stage, which is always
     * "PUBLISH" for a Publisher row). A local copy of the same grouping
     * KpiDashboardService#stageLabel already uses (kept local rather than shared/refactored, same
     * reasoning PUBLISH_ACTIVE_WINDOW/PUBLISH_CLOSED_OUT above are already local copies of
     * AssigneeActiveWindows) - never called for RFP/PUBG (Active) or CLOSED_OUT (History) rows.
     */
    private static String stageLabel(WorkflowStatus status) {
        // Same grouping as KpiDashboardService#stageLabel (EAP groups with Publishing, not Edit -
        // it is the transitional status immediately following Edit Review Approve, in the same
        // transaction as the RFP transition itself, kept consistent with that existing convention).
        return switch (status) {
            case SA, SIP, SRV, SAP -> "Shoot";
            case EA, ED, ERV -> "Edit";
            default -> "Publishing";
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
        // Cancelled tab: counted from the SAME unfiltered row set and with the SAME predicate
        // PipelineDashboardService#matchesStage uses for stage=cancelled, so the badge can never
        // disagree with the number of rows the tab actually returns.
        model.addAttribute("cancelledCount", allRows.stream()
                .filter(r -> "Cancelled".equals(r.getStatus())).count());

        // ENG-071: options for the Status/Channel filter dropdowns - distinct values actually
        // present across the full (unfiltered) row set, so a dropdown never offers a choice that
        // would always return zero rows. The Platform filter was replaced by the Channel one: the
        // Platforms COLUMN is unchanged and still rendered, it is only no longer a filter
        // dimension here (collectFilterOptions, which feeds the separate Publishing screen's own
        // platform filter, is untouched).
        Set<String> statusOptions = new LinkedHashSet<>();
        Set<String> channelOptions = new LinkedHashSet<>();
        for (com.kcpc.mkt.reporting.dto.PipelineRow row : allRows) {
            statusOptions.add(row.getStatus());
            addSplitValues(channelOptions, row.getChannels());
        }
        model.addAttribute("statusOptions", statusOptions);
        model.addAttribute("channelOptions", channelOptions);

        com.kcpc.mkt.reporting.dto.PipelineFilterCriteria criteria = new com.kcpc.mkt.reporting.dto.PipelineFilterCriteria(
                q, stage, sku, idea, priority, cameraperson, modelFilter, videoEditor, channel, status,
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
        model.addAttribute("platformChannelFilterActive", notBlank(channel));
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

        // BR-063 Hold/Resume: PipelineRow#isOnHold (PipelineDashboardService) is the actual source
        // of the On Hold badge rendered per row - this used to duplicate that lookup here without
        // ever being read by the JSP; removed rather than left as dead code.

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

    /**
     * My Work &rarr; Dashboard row filter (Publisher). All three criteria are combined with AND -
     * a row must satisfy the date range AND the channel AND the platform selection to survive.
     * Every unset criterion is a no-op, so no filter at all keeps every row.
     *
     * <p>Both the Channel and Platform criteria are evaluated against the plan's real Planned
     * Output &rarr; Publication Target mappings ({@code summaries}), never against a rendered
     * label - the same data the Content Pipeline dashboard's own platform/channel columns use.
     *
     * @param plannedDate the row's Planned Live Date; a row without one cannot equal a requested
     *                    date and is therefore excluded whenever {@code liveDate} is set
     * @param summaries   the plan's platform/channel breakdown; null/empty means the plan has no
     *                    resolved publication targets yet, so it cannot match a channel/platform
     *                    selection (but is unaffected when neither is selected)
     * @param liveDate    exact Planned Live Date to keep; null = no date criterion
     */
    private static boolean matchesPublishFilter(LocalDate plannedDate, List<PipelinePlatformSummary> summaries,
                                                LocalDate liveDate, String channel, Set<String> platforms) {
        if (liveDate != null && !liveDate.equals(plannedDate)) {
            return false;
        }
        boolean channelWanted = notBlank(channel);
        boolean platformWanted = platforms != null && !platforms.isEmpty();
        if (!channelWanted && !platformWanted) {
            return true;
        }
        if (summaries == null || summaries.isEmpty()) {
            return false;
        }
        boolean channelMatched = !channelWanted;
        boolean platformMatched = !platformWanted;
        for (PipelinePlatformSummary summary : summaries) {
            if (platformWanted && platforms.contains(summary.getPlatformName())) {
                platformMatched = true;
            }
            if (channelWanted) {
                for (PipelineChannelStatus cs : summary.getChannels()) {
                    if (channel.equals(cs.getChannelHandle())) {
                        channelMatched = true;
                        break;
                    }
                }
            }
        }
        return channelMatched && platformMatched;
    }

    /** Trims blanks out of a submitted multi-select and preserves the submitted order. */
    private static Set<String> cleanPlatforms(List<String> platforms) {
        return platforms == null ? Set.of()
                : platforms.stream().filter(LandingMvcController::notBlank)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Platform/channel dropdown options for ONE tab, gathered from that tab's own rows only.
     * Deliberately scoped per list rather than page-wide: the Dashboard must never offer a channel
     * that exists only on an Active Publishing row (and vice versa), or the dropdown would present
     * a choice that always returns zero rows on that tab.
     *
     * <p>Options come from the tab's UNFILTERED rows, so applying a filter never shrinks the list
     * of things you can still filter by.
     */
    private static void collectFilterOptions(List<UUID> planIds,
                                             Map<UUID, List<PipelinePlatformSummary>> platformsByPlan,
                                             Set<String> platformOptions, Set<String> channelOptions) {
        for (UUID planId : planIds) {
            List<PipelinePlatformSummary> summaries = platformsByPlan.get(planId);
            if (summaries == null) {
                continue;
            }
            for (PipelinePlatformSummary summary : summaries) {
                if (notBlank(summary.getPlatformName())) {
                    platformOptions.add(summary.getPlatformName());
                }
                for (PipelineChannelStatus cs : summary.getChannels()) {
                    if (notBlank(cs.getChannelHandle())) {
                        channelOptions.add(cs.getChannelHandle());
                    }
                }
            }
        }
    }

    /**
     * Rows in ONE tab's list whose Planned Live Date is exactly {@code date}, honouring that tab's
     * own Channel/Platform selection - i.e. the number its Today/Tomorrow quick-pick card shows,
     * and exactly what clicking that card will display.
     *
     * <p>Generic over the row type so the Dashboard's {@link UpcomingWorkItem} list and the
     * Publishing tab's {@link ActiveWorkItem} list are counted by the SAME code against the SAME
     * {@link #matchesPublishFilter} predicate the tables themselves are filtered with - a card's
     * count can therefore never disagree with the table it leads to. The two tabs differ only in
     * which list is passed in, never in the counting rule.
     */
    private static <T> long countRowsOn(LocalDate date, List<T> rows,
                                        java.util.function.Function<T, UUID> planIdOf,
                                        java.util.function.Function<T, LocalDate> plannedDateOf,
                                        Map<UUID, List<PipelinePlatformSummary>> platformsByPlan,
                                        String channel, Set<String> platforms) {
        return rows.stream()
                .filter(row -> matchesPublishFilter(plannedDateOf.apply(row),
                        platformsByPlan.get(planIdOf.apply(row)), date, channel, platforms))
                .count();
    }

    /**
     * Query string for one tab's Clear link: the OTHER tab's filter only, so clearing one tab
     * leaves the other exactly as it was. Always ends in {@code "?"} (nothing to keep) or
     * {@code "...&"}, so the caller can append {@code tab=<name>} without knowing which.
     */
    private static String clearQs(LocalDate otherDate, String otherChannel, Set<String> otherPlatforms,
                                  String otherPrefix) {
        org.springframework.web.util.UriComponentsBuilder qs =
                org.springframework.web.util.UriComponentsBuilder.newInstance();
        addQueryParam(qs, otherPrefix + "Date", asString(otherDate));
        addQueryParam(qs, otherPrefix + "Channel", otherChannel);
        for (String platform : otherPlatforms) {
            addQueryParam(qs, otherPrefix + "Platform", platform);
        }
        String encoded = qs.build().encode().toUriString();
        return encoded.isEmpty() ? "?" : encoded + "&";
    }

    /**
     * Query string for one tab's quick-pick card. Carries that tab's own date/channel/platform
     * selection AND the other tab's entire filter state, because a card is a link and submits no
     * form - without the second half, clicking a card on one tab would silently clear the other
     * tab's filters. Includes the leading "?".
     *
     * @param dashboardScope true when the card belongs to the Dashboard tab (so {@code date}/
     *                       {@code channel}/{@code platforms} are the dash* parameters and the
     *                       "other" arguments are the pub* ones); false for the Publishing tab
     */
    private static String myWorkFilterQs(LocalDate date, String channel, Set<String> platforms,
                                         LocalDate otherDate, String otherChannel, Set<String> otherPlatforms,
                                         boolean dashboardScope) {
        String ownPrefix = dashboardScope ? "dash" : "pub";
        String otherPrefix = dashboardScope ? "pub" : "dash";
        org.springframework.web.util.UriComponentsBuilder qs =
                org.springframework.web.util.UriComponentsBuilder.newInstance();
        addQueryParam(qs, ownPrefix + "Date", asString(date));
        addQueryParam(qs, ownPrefix + "Channel", channel);
        for (String platform : platforms) {
            addQueryParam(qs, ownPrefix + "Platform", platform);
        }
        addQueryParam(qs, otherPrefix + "Date", asString(otherDate));
        addQueryParam(qs, otherPrefix + "Channel", otherChannel);
        for (String platform : otherPlatforms) {
            addQueryParam(qs, otherPrefix + "Platform", platform);
        }
        return qs.build().encode().toUriString();
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
     *
     * <p>Deliberately never derives a Model's row from the Content Plan's overall
     * {@code WorkflowStatus} (see MyShootRow's own header comment) - a Model's participation is
     * independent of the downstream content lifecycle, so there is nothing here for Edit/Review/
     * Publishing to make "pending" again once the shoot itself is assigned.
     *
     * <p>Upcoming vs Past is likewise NOT the planned shoot date vs today (a future-dated shoot
     * whose Model task is already complete must still show as Past - see
     * {@link #isShootTaskCompleted}) - it is purely whether the Model's own task on that plan is
     * still outstanding or already complete per that same helper. The planned shoot date is only
     * ever used to order rows within whichever bucket they land in.
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
        Set<UUID> instanceIds = plansById.values().stream()
                .map(p -> p.getWorkflowInstance().getId()).collect(Collectors.toSet());
        Map<UUID, List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory>> historyByInstance =
                transitionHistoryRepository.findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(instanceIds).stream()
                        .collect(Collectors.groupingBy(t -> t.getWorkflowInstance().getId()));

        List<MyShootRow> upcoming = new ArrayList<>();
        List<MyShootRow> past = new ArrayList<>();
        for (ContentPlanTalentEntry myEntry : myEntries) {
            ContentPlan plan = plansById.get(myEntry.getContentPlan().getId());
            if (plan == null) {
                continue;
            }
            boolean taskCompleted = isShootTaskCompleted(
                    historyByInstance.getOrDefault(plan.getWorkflowInstance().getId(), List.of()));
            String otherTalent = allTalentByPlan.getOrDefault(plan.getId(), List.of()).stream()
                    .filter(e -> !e.getId().equals(myEntry.getId()))
                    .map(ContentPlanTalentEntry::getTalentName)
                    .collect(Collectors.joining(", "));
            MyShootRow row = new MyShootRow(plan.getId(), plan.getContentId(), contentTitle(plan),
                    plan.getPlannedShootDate(), "Model", otherTalent);
            (taskCompleted ? past : upcoming).add(row);
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

    /** The one real business event that ends the Shoot phase for THIS Content, and with it every
     * participant's own Shoot-side task (Cameraperson, Model/Talent, or any future assignee type)
     * - not a Model-specific concept, purely a property of the Shoot stage itself reaching its own
     * terminal state: either Shoot Review approving it ({@code APPROVE_SHOOT}, WorkflowStatus.SAP)
     * or an admin explicitly skipping the whole Shoot stage ({@code SKIP_SHOOT_STAGE}, straight to
     * WorkflowStatus.EA). Both are permanent {@code WorkflowTransitionHistory} rows, so once
     * either has fired it stays true forever regardless of anything that happens afterward
     * (Edit/Review/Publishing). Deliberately NOT the Content Plan's current
     * {@code WorkflowStatus} - that keeps changing long after the Shoot phase itself is over, and
     * checking "is status still SA/SIP/SRV" would incorrectly flip back and forth were the plan
     * ever reassigned/reopened, whereas this history check cannot. Package-visible: reused as-is
     * by DeliverableMvcController's own generic Shoot Execution routing decision (same concept,
     * same source of truth, never re-derived a second way) and by its Model/Talent-specific
     * hard-deny gate. */
    static boolean isShootTaskCompleted(List<com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory> historyForInstance) {
        return historyForInstance.stream()
                .map(com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory::getTriggerCommand)
                .anyMatch(t -> "APPROVE_SHOOT".equals(t) || "SKIP_SHOOT_STAGE".equals(t));
    }
}
