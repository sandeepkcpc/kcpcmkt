package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.reporting.service.PipelineDashboardService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.web.mvc.dto.CompletedWorkItem;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    private final IdeaRepository ideaRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final PersonalMarkAttributionRepository markAttributionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;
    private final PipelineDashboardService pipelineDashboardService;

    private static final Set<WorkflowStatus> SHOOT_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.SA, WorkflowStatus.SIP, WorkflowStatus.SRV);
    private static final Set<WorkflowStatus> EDIT_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.EA, WorkflowStatus.ED, WorkflowStatus.ERV);
    private static final Set<WorkflowStatus> PUBLISH_ACTIVE_WINDOW =
            EnumSet.of(WorkflowStatus.RFP, WorkflowStatus.PUBG);

    public LandingMvcController(IdeaRepository ideaRepository, ContentPlanRepository contentPlanRepository,
                                 ShootingAssignmentRepository shootingAssignmentRepository,
                                 EditingAssignmentRepository editingAssignmentRepository,
                                 PublishingAssignmentRepository publishingAssignmentRepository,
                                 PersonalMarkAttributionRepository markAttributionRepository,
                                 ReviewCycleRepository reviewCycleRepository,
                                 WorkHoldRecordRepository workHoldRecordRepository,
                                 WorkflowTransitionHistoryRepository transitionHistoryRepository,
                                 PipelineDashboardService pipelineDashboardService) {
        this.ideaRepository = ideaRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.markAttributionRepository = markAttributionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
        this.pipelineDashboardService = pipelineDashboardService;
    }

    /** Role-appropriate dispatch, kept as the shared post-login redirect target. */
    @GetMapping("/app/home")
    public String home(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        AccessClass accessClass = principal.user().resolvedAccessClass();
        if (accessClass == AccessClass.CEO_OWNER || accessClass == AccessClass.MARKETING_MANAGER) {
            return "redirect:/app/pipeline";
        }
        return "redirect:/app/my-work";
    }

    @GetMapping("/app/my-work")
    public String myWork(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        model.addAttribute("user", user);
        model.addAttribute("accessClass", user.resolvedAccessClass());

        // Task visibility comes from an active assignment, never Designation/Business Role alone -
        // and only for as long as that stage is still THIS employee's active work. Shoot
        // Assignment is created during Planning (Stage 3), before Planning Review even starts, so
        // an active ShootingAssignment can exist well before the plan is actually approved (hidden
        // entirely below, status still PL/PLRV); once a stage's own review has decided and the
        // plan has moved on to the next stage, that assignment drops out of "Active Assignments"
        // and into "My Completed Work / History" instead - own-stage summary only, never the next
        // stage's operational detail (ENG-038).
        List<ShootingAssignment> shootTasks = new ArrayList<>();
        List<EditingAssignment> editTasks = new ArrayList<>();
        List<PublishingAssignment> publishTasks = new ArrayList<>();
        List<CompletedWorkItem> completedWork = new ArrayList<>();

        for (ShootingAssignment t : shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user)) {
            WorkflowStatus s = t.getContentPlan().getWorkflowInstance().getCurrentStatusCode();
            if (s == WorkflowStatus.PL || s == WorkflowStatus.PLRV) {
                continue; // not visible at all yet - Planning not Approved (ENG-037)
            }
            if (SHOOT_ACTIVE_WINDOW.contains(s)) {
                shootTasks.add(t);
            } else {
                completedWork.add(completedItem(t.getContentPlan(), "SHOOT", GateType.SHOOT_REVIEW, SHOOT_ACTIVE_WINDOW));
            }
        }
        for (EditingAssignment t : editingAssignmentRepository.findByEditorAndActiveTrue(user)) {
            WorkflowStatus s = t.getContentPlan().getWorkflowInstance().getCurrentStatusCode();
            if (EDIT_ACTIVE_WINDOW.contains(s)) {
                editTasks.add(t);
            } else {
                completedWork.add(completedItem(t.getContentPlan(), "EDIT", GateType.EDIT_REVIEW, EDIT_ACTIVE_WINDOW));
            }
        }
        for (PublishingAssignment t : publishingAssignmentRepository.findByPublisherAndActiveTrue(user)) {
            WorkflowStatus s = t.getContentPlan().getWorkflowInstance().getCurrentStatusCode();
            if (PUBLISH_ACTIVE_WINDOW.contains(s)) {
                publishTasks.add(t);
            } else {
                // No review/decision gate exists for Publishing - Final Result stays blank.
                completedWork.add(completedItem(t.getContentPlan(), "PUBLISH", null, PUBLISH_ACTIVE_WINDOW));
            }
        }
        completedWork.sort(Comparator.comparing(CompletedWorkItem::getCompletedOn,
                Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("shootTasks", shootTasks);
        model.addAttribute("editTasks", editTasks);
        model.addAttribute("publishTasks", publishTasks);
        model.addAttribute("completedWork", completedWork);
        model.addAttribute("today", LocalDate.now(BUSINESS_ZONE));

        List<Idea> myIdeas = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getSubmittedBy().getId().equals(user.getId())).toList();
        model.addAttribute("myIdeas", myIdeas);

        model.addAttribute("myMarks", markAttributionRepository.findByRecipient(user));

        // Own review feedback: decided reviews on gates this user submitted work into.
        List<ReviewCycle> myReviewFeedback = reviewCycleRepository
                .findBySubmittedByAndDecidedAtIsNotNullOrderByDecidedAtDesc(user);
        model.addAttribute("myReviewFeedback", myReviewFeedback);

        return "my-work";
    }

    /**
     * Builds a "My Completed Work / History" row for a stage that has moved on. Completed On is
     * the most recent transition that took the plan OUT of that stage's active window (handles a
     * reopen-then-reclose cycle correctly by always reflecting the latest such exit, not the
     * first); Final Result is the deciding review's outcome for gates that have one (Shoot/Edit),
     * or null for Publishing, which has no review/decision gate at all.
     */
    private CompletedWorkItem completedItem(ContentPlan plan, String stageWorked, GateType gateType,
                                             Set<WorkflowStatus> activeWindow) {
        Instant completedOn = transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(plan.getWorkflowInstance()).stream()
                .filter(t -> activeWindow.contains(t.getFromStatusCode()) && !activeWindow.contains(t.getToStatusCode()))
                .reduce((first, second) -> second)
                .map(com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory::getTransitionTimestamp)
                .orElse(null);
        String finalResult = null;
        if (gateType != null) {
            WorkflowInstance workflowInstance = plan.getWorkflowInstance();
            Optional<ReviewCycle> decided = reviewCycleRepository
                    .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType).stream()
                    .filter(c -> c.getDecidedAt() != null)
                    .findFirst();
            finalResult = decided.map(c -> "APPROVED".equals(c.getDecision()) ? "Approved" : c.getDecision()).orElse(null);
        }
        return new CompletedWorkItem(plan.getContentId(), stageWorked, completedOn, finalResult);
    }

    /**
     * CEO Content Pipeline 18-column dashboard (docs/changes/CEO_CONTENT_PIPELINE_18_COLUMN_CHANGE.md).
     * Shared by CEO_OWNER and MARKETING_MANAGER, matching the existing role-appropriate landing
     * split; EMPLOYEE-class users are redirected rather than shown this company-wide view.
     */
    @GetMapping("/app/pipeline")
    public String pipeline(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User user = principal.user();
        if (user.resolvedAccessClass() == AccessClass.EMPLOYEE) {
            return "redirect:/app/home";
        }
        model.addAttribute("user", user);
        model.addAttribute("accessClass", user.resolvedAccessClass());

        List<ContentPlan> plans = contentPlanRepository.findAllWithPreparedByOrderByCreatedAtDesc();
        model.addAttribute("plans", plans);
        model.addAttribute("pipelineRows", pipelineDashboardService.buildRows(plans));
        model.addAttribute("today", LocalDate.now(BUSINESS_ZONE));

        Set<String> statuses = new LinkedHashSet<>();
        for (ContentPlan plan : plans) {
            statuses.add(plan.getWorkflowInstance().getCurrentStatusCode().name());
        }
        model.addAttribute("statuses", statuses);

        List<WorkHoldRecord> openHolds = workHoldRecordRepository.findByResumedAtIsNull();
        Set<java.util.UUID> onHoldWorkflowInstanceIds = openHolds.stream()
                .map(h -> h.getWorkflowInstance().getId()).collect(Collectors.toSet());
        model.addAttribute("onHoldWorkflowInstanceIds", onHoldWorkflowInstanceIds);

        return "pipeline";
    }
}
