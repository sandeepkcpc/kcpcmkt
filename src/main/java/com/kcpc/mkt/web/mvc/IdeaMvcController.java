package com.kcpc.mkt.web.mvc;

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
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.web.mvc.dto.IdeaHistoryEvent;
import com.kcpc.mkt.web.mvc.dto.MyIdeaRow;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Same IdeaService as IdeaRestController (architecture rule: shared application/service layer). */
@Controller
public class IdeaMvcController {

    private final IdeaService ideaService;
    private final IdeaRepository ideaRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final AuthorizationService authorizationService;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionHistoryRepository transitionHistoryRepository;

    private static final int MY_IDEAS_PAGE_SIZE = 6;
    private static final Set<String> IDEA_LIFECYCLE_TRIGGER_COMMANDS =
            Set.of("SUBMIT_IDEA", "APPROVE_IDEA", "REJECT_IDEA", "RETAIN_IDEA", "REOPEN_IDEA");

    public IdeaMvcController(IdeaService ideaService, IdeaRepository ideaRepository,
                              ContentPlanRepository contentPlanRepository, AuthorizationService authorizationService,
                              ReviewCycleRepository reviewCycleRepository,
                              WorkflowTransitionHistoryRepository transitionHistoryRepository) {
        this.ideaService = ideaService;
        this.ideaRepository = ideaRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.authorizationService = authorizationService;
        this.reviewCycleRepository = reviewCycleRepository;
        this.transitionHistoryRepository = transitionHistoryRepository;
    }

    @GetMapping("/app/ideas/new")
    public String newIdeaForm() {
        return "idea-submit";
    }

    @PostMapping("/app/ideas")
    public String submit(@RequestParam String title, @RequestParam(required = false) String referenceLink,
                          @RequestParam(required = false) String notesRemarks,
                          @RequestParam(required = false) String additionalNote,
                          @AuthenticationPrincipal KcpcUserPrincipal principal, Model model,
                          RedirectAttributes redirectAttributes) {
        try {
            ideaService.submit(principal.user(), title, referenceLink, notesRemarks, additionalNote);
            redirectAttributes.addFlashAttribute("successMessage", "Idea submitted successfully.");
            return "redirect:/app/ideas";
        } catch (DomainException e) {
            // ENG-060: client-side JS validates the same rules first in the common case, but
            // backend validation stays authoritative (e.g. no-JS fallback) - on failure, preserve
            // every entered value so nothing already typed is lost, and point the message at the
            // specific field it concerns rather than only a generic banner.
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("errorField", e.getMessage().contains("Reference Link") ? "referenceLink" : "title");
            model.addAttribute("title", title);
            model.addAttribute("referenceLink", referenceLink);
            model.addAttribute("notesRemarks", notesRemarks);
            model.addAttribute("additionalNote", additionalNote);
            return "idea-submit";
        }
    }

    @GetMapping("/app/ideas")
    public String queue(@AuthenticationPrincipal KcpcUserPrincipal principal,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false, defaultValue = "ALL") String status,
                         @RequestParam(required = false, defaultValue = "ALL") String range,
                         @RequestParam(required = false, defaultValue = "1") int page,
                         Model model) {
        User currentUser = principal.user();
        // ENG-059: EMPLOYEE-class users get "My Ideas" (own submissions only, KPI cards +
        // search/filter/pagination) instead of the company-wide Idea Queue CEO/MM see - same route,
        // branched by accessClass, matching how /app/my-work and /app/pipeline already work.
        if (currentUser.resolvedAccessClass() == AccessClass.EMPLOYEE) {
            return myIdeas(currentUser, q, status, range, page, model);
        }
        List<Idea> ideas = ideaRepository.findAllByOrderBySubmittedAtDesc();
        Map<UUID, Boolean> canDecideByIdea = new LinkedHashMap<>();
        for (Idea idea : ideas) {
            canDecideByIdea.put(idea.getId(), canDecide(currentUser, idea));
        }
        model.addAttribute("ideas", ideas);
        model.addAttribute("canDecideByIdea", canDecideByIdea);
        return "idea-queue";
    }

    /**
     * ENG-059: KPI counts and the table both read from the exact same {@code allRows} list (built
     * once from the Employee's own ideas) - counts are taken BEFORE search/status/date filtering is
     * applied to produce the paginated subset, so a count/table mismatch is structurally impossible.
     * Search/filter/pagination are plain GET query params (full-page reload) rather than a new
     * AJAX mechanism - no such client-side table-filtering pattern exists anywhere else in this
     * app to reuse, and inventing one here would cut against "reuse existing JSP/CSS/vanilla JS."
     */
    private String myIdeas(User user, String q, String statusFilter, String range, int page, Model model) {
        List<Idea> ideas = ideaRepository.findBySubmittedByOrderBySubmittedAtDesc(user);
        List<UUID> instanceIds = ideas.stream().map(i -> i.getWorkflowInstance().getId()).toList();

        // ENG-061: batched (2 queries total, not one per idea) so the Reopened-vs-Under-Review
        // distinction and the Feedback column stay N+1-free for the whole table.
        Map<UUID, List<WorkflowTransitionHistory>> lifecycleByInstance = transitionHistoryRepository
                .findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(instanceIds).stream()
                .filter(t -> IDEA_LIFECYCLE_TRIGGER_COMMANDS.contains(t.getTriggerCommand()))
                .collect(Collectors.groupingBy(t -> t.getWorkflowInstance().getId()));
        Map<UUID, List<ReviewCycle>> cyclesByInstance = reviewCycleRepository
                .findByWorkflowInstance_IdIn(instanceIds).stream()
                .collect(Collectors.groupingBy(c -> c.getWorkflowInstance().getId()));

        List<MyIdeaRow> allRows = ideas.stream()
                .map(idea -> toMyIdeaRow(idea,
                        lifecycleByInstance.getOrDefault(idea.getWorkflowInstance().getId(), List.of()),
                        cyclesByInstance.getOrDefault(idea.getWorkflowInstance().getId(), List.of())))
                .toList();

        // ENG-061: Idea workflow is separate from Planning - once Approved, the Idea Status stays
        // "Approved" forever regardless of downstream Planning/Shoot/Edit/Publishing progress, so
        // there is no "In Planning" bucket here. A Reopened idea is folded into the "Under Review"
        // KPI count (it is, again, awaiting a review decision) while still filterable/displayed as
        // its own distinct "Reopened" status on the row itself.
        model.addAttribute("totalIdeasCount", allRows.size());
        model.addAttribute("underReviewCount", allRows.stream()
                .filter(r -> "Under Review".equals(r.getStatusLabel()) || "Reopened".equals(r.getStatusLabel())).count());
        model.addAttribute("approvedCount", allRows.stream().filter(r -> "Approved".equals(r.getStatusLabel())).count());
        model.addAttribute("retainedCount", allRows.stream().filter(r -> "Retained".equals(r.getStatusLabel())).count());
        model.addAttribute("rejectedCount", allRows.stream().filter(r -> "Rejected".equals(r.getStatusLabel())).count());

        String qLower = q == null ? "" : q.trim().toLowerCase();
        Instant rangeStart = rangeStart(range);
        List<MyIdeaRow> filtered = allRows.stream()
                .filter(r -> qLower.isEmpty() || r.getTitle().toLowerCase().contains(qLower)
                        || r.getBusinessIdeaCode().toLowerCase().contains(qLower))
                .filter(r -> "ALL".equals(statusFilter) || statusFilter.equals(r.getStatusLabel()))
                .filter(r -> rangeStart == null || (r.getSubmittedAt() != null && !r.getSubmittedAt().isBefore(rangeStart)))
                .toList();

        int totalCount = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) MY_IDEAS_PAGE_SIZE));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = Math.min((currentPage - 1) * MY_IDEAS_PAGE_SIZE, totalCount);
        int toIndex = Math.min(fromIndex + MY_IDEAS_PAGE_SIZE, totalCount);

        model.addAttribute("myIdeaRows", filtered.subList(fromIndex, toIndex));
        model.addAttribute("myIdeasQuery", q == null ? "" : q);
        model.addAttribute("myIdeasStatusFilter", statusFilter);
        model.addAttribute("myIdeasRangeFilter", range);
        model.addAttribute("myIdeasCurrentPage", currentPage);
        model.addAttribute("myIdeasTotalPages", totalPages);
        model.addAttribute("myIdeasFromIndex", totalCount == 0 ? 0 : fromIndex + 1);
        model.addAttribute("myIdeasToIndex", toIndex);
        model.addAttribute("myIdeasTotalCount", totalCount);
        return "my-ideas";
    }

    private MyIdeaRow toMyIdeaRow(Idea idea, List<WorkflowTransitionHistory> lifecycleHistoryAsc,
                                   List<ReviewCycle> cycles) {
        String latestTrigger = lifecycleHistoryAsc.isEmpty()
                ? null : lifecycleHistoryAsc.get(lifecycleHistoryAsc.size() - 1).getTriggerCommand();
        String label = statusLabel(idea.getWorkflowInstance().getCurrentStatusCode(), latestTrigger);
        String feedback = cycles.stream()
                .filter(c -> c.getGateType() == GateType.IDEA_REVIEW && c.getDecidedAt() != null)
                .max(Comparator.comparing(ReviewCycle::getCycleNumber))
                .map(ReviewCycle::getDecisionReason).orElse(null);
        return new MyIdeaRow(idea.getId(), idea.getBusinessIdeaCode(), idea.getTitle(), idea.getNotesRemarks(),
                idea.getSubmittedAt(), label, statusCssClass(label), feedback);
    }

    /**
     * ENG-061: Idea workflow is separate from Planning (BRS-REQ-014..019) - once approved (PL and
     * every status beyond it, all the way through the resulting content's own Shoot/Edit/Publishing
     * pipeline), the IDEA's own status must read "Approved" forever, never a downstream Planning
     * workflow state name. PA is ambiguous on its own (a brand-new submission and a
     * just-reopened-from-Retained idea are both PA) - the most recent idea-lifecycle trigger
     * command disambiguates it into "Under Review" vs. "Reopened".
     */
    private static String statusLabel(WorkflowStatus status, String latestLifecycleTrigger) {
        return switch (status) {
            case PA -> "REOPEN_IDEA".equals(latestLifecycleTrigger) ? "Reopened" : "Under Review";
            case RJ -> "Rejected";
            case RET -> "Retained";
            default -> "Approved"; // PL and everything downstream of it
        };
    }

    private static String statusCssClass(String statusLabel) {
        return switch (statusLabel) {
            case "Under Review", "Reopened" -> "status-underreview";
            case "Approved" -> "status-completed";
            case "Retained" -> "status-retained";
            case "Rejected" -> "status-rejected";
            default -> "status-inprogress";
        };
    }

    private static Instant rangeStart(String range) {
        if (range == null) {
            return null;
        }
        return switch (range) {
            case "7" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> null;
        };
    }

    /** Latest DECIDED Idea Review cycle's reason - null for Approve (no reason recorded), text for Reject/Retain. */
    private String latestFeedback(Idea idea) {
        return reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(idea.getWorkflowInstance(), GateType.IDEA_REVIEW)
                .stream().filter(c -> c.getDecidedAt() != null).findFirst()
                .map(ReviewCycle::getDecisionReason).orElse(null);
    }

    /**
     * ENG-059/061: only the idea's OWN review lifecycle (submit/approve/reject/retain/reopen) -
     * never the resulting content's downstream Planning/Shoot/Edit/Publishing events. Newest-first.
     */
    private List<WorkflowTransitionHistory> ideaLifecycleHistory(Idea idea) {
        List<WorkflowTransitionHistory> history = new ArrayList<>(transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(idea.getWorkflowInstance()));
        history.removeIf(t -> !IDEA_LIFECYCLE_TRIGGER_COMMANDS.contains(t.getTriggerCommand()));
        java.util.Collections.reverse(history);
        return history;
    }

    /**
     * ENG-061: re-labels each raw transition into Idea-domain vocabulary (never a raw
     * {@code WorkflowStatus} name like "Planning") for the "Idea Decision History" panel.
     */
    private static List<IdeaHistoryEvent> toHistoryEvents(List<WorkflowTransitionHistory> lifecycleHistory) {
        return lifecycleHistory.stream()
                .map(t -> new IdeaHistoryEvent(lifecycleEventLabel(t.getTriggerCommand()), t.getTransitionTimestamp(),
                        t.getTriggeredBy().getFullName(), t.getTransitionReason()))
                .toList();
    }

    private static String lifecycleEventLabel(String triggerCommand) {
        return switch (triggerCommand) {
            case "SUBMIT_IDEA" -> "Submitted";
            case "APPROVE_IDEA" -> "Approved";
            case "REJECT_IDEA" -> "Rejected";
            case "RETAIN_IDEA" -> "Retained";
            case "REOPEN_IDEA" -> "Reopened";
            default -> triggerCommand;
        };
    }

    @GetMapping("/app/ideas/{ideaId}")
    public String detail(@PathVariable UUID ideaId, @AuthenticationPrincipal KcpcUserPrincipal principal, Model model,
                          RedirectAttributes redirectAttributes) {
        User currentUser = principal.user();
        Idea idea = ideaRepository.findById(ideaId).orElse(null);
        // ENG-059: an Employee may only view their OWN idea's details, never another user's - a
        // direct URL/ID guess is treated the same as a genuinely-nonexistent idea (SRS-REQ-067's
        // privacy principle, already established for My Work, enforced here too - not just hidden
        // from the table). Redirects with a flash message rather than throwing uncaught - this
        // controller has no MVC-side DomainException handler (only IdeaRestController's REST one
        // does), so an uncaught throw here would otherwise surface as a raw 500 error page.
        boolean ownershipDenied = idea != null && currentUser.resolvedAccessClass() == AccessClass.EMPLOYEE
                && !idea.getSubmittedBy().getId().equals(currentUser.getId());
        if (idea == null || ownershipDenied) {
            redirectAttributes.addFlashAttribute("errorMessage", "Idea not found.");
            return "redirect:/app/ideas";
        }
        List<WorkflowTransitionHistory> lifecycleHistory = ideaLifecycleHistory(idea); // newest-first
        String latestTrigger = lifecycleHistory.isEmpty() ? null : lifecycleHistory.get(0).getTriggerCommand();
        String ideaStatusLabel = statusLabel(idea.getWorkflowInstance().getCurrentStatusCode(), latestTrigger);

        model.addAttribute("idea", idea);
        model.addAttribute("canDecide", canDecide(currentUser, idea));
        model.addAttribute("ideaStatusLabel", ideaStatusLabel);
        model.addAttribute("ideaStatusCssClass", statusCssClass(ideaStatusLabel));
        model.addAttribute("ideaFeedback", latestFeedback(idea));
        model.addAttribute("ideaStatusHistory", toHistoryEvents(lifecycleHistory));
        contentPlanRepository.findByIdea(idea).ifPresent(plan -> model.addAttribute("contentPlanId", plan.getId()));
        return "idea-detail";
    }

    /** View-model only: the server-authoritative check re-runs unconditionally inside IdeaService.decide. */
    private boolean canDecide(User currentUser, Idea idea) {
        try {
            var grant = authorizationService.requireAuthority(currentUser, OperationalPermission.PERM_01_IDEA_REVIEW,
                    LifecycleStage.IDEA_MANAGEMENT, idea.getWorkflowInstance());
            boolean selfConflict = grant.isPresent() && currentUser.getId().equals(idea.getSubmittedBy().getId());
            return !selfConflict;
        } catch (DomainException e) {
            return false;
        }
    }

    @PostMapping("/app/ideas/{ideaId}/review")
    public String decide(@PathVariable UUID ideaId, @RequestParam IdeaReviewDecision decision,
                          @RequestParam(required = false) String reason,
                          @RequestParam(required = false) BigDecimal cameramanMark,
                          @RequestParam(required = false) BigDecimal editorMark,
                          @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes redirectAttributes) {
        try {
            ideaService.decide(principal.user(), ideaId, decision, reason, cameramanMark, editorMark);
            redirectAttributes.addFlashAttribute("successMessage", "Review decision recorded.");
        } catch (DomainException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/ideas/" + ideaId;
    }

    @PostMapping("/app/ideas/{ideaId}/reopen")
    public String reopen(@PathVariable UUID ideaId, @AuthenticationPrincipal KcpcUserPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        try {
            ideaService.reopen(principal.user(), ideaId);
            redirectAttributes.addFlashAttribute("successMessage", "Idea reopened to Pending Approval.");
        } catch (DomainException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/ideas/" + ideaId;
    }
}
