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
import com.kcpc.mkt.reporting.service.PipelineDashboardService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkHoldRecord;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final PersonalMarkAttributionRepository markAttributionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;
    private final PipelineDashboardService pipelineDashboardService;

    public LandingMvcController(IdeaRepository ideaRepository, ContentPlanRepository contentPlanRepository,
                                 ShootingAssignmentRepository shootingAssignmentRepository,
                                 EditingAssignmentRepository editingAssignmentRepository,
                                 PersonalMarkAttributionRepository markAttributionRepository,
                                 ReviewCycleRepository reviewCycleRepository,
                                 WorkHoldRecordRepository workHoldRecordRepository,
                                 PipelineDashboardService pipelineDashboardService) {
        this.ideaRepository = ideaRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.markAttributionRepository = markAttributionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
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

        List<ShootingAssignment> shootTasks = shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user);
        List<EditingAssignment> editTasks = editingAssignmentRepository.findByEditorAndActiveTrue(user);
        model.addAttribute("shootTasks", shootTasks);
        model.addAttribute("editTasks", editTasks);
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
