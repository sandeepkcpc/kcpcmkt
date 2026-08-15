package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.DomainException;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Same IdeaService as IdeaRestController (architecture rule: shared application/service layer). */
@Controller
public class IdeaMvcController {

    private final IdeaService ideaService;
    private final IdeaRepository ideaRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final AuthorizationService authorizationService;

    public IdeaMvcController(IdeaService ideaService, IdeaRepository ideaRepository,
                              ContentPlanRepository contentPlanRepository, AuthorizationService authorizationService) {
        this.ideaService = ideaService;
        this.ideaRepository = ideaRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/app/ideas/new")
    public String newIdeaForm() {
        return "idea-submit";
    }

    @PostMapping("/app/ideas")
    public String submit(@RequestParam String title, @RequestParam(required = false) String referenceLink,
                          @RequestParam(required = false) String notesRemarks,
                          @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        try {
            ideaService.submit(principal.user(), title, referenceLink, notesRemarks);
            return "redirect:/app/ideas";
        } catch (DomainException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "idea-submit";
        }
    }

    @GetMapping("/app/ideas")
    public String queue(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        User currentUser = principal.user();
        List<Idea> ideas = ideaRepository.findAllByOrderBySubmittedAtDesc();
        Map<UUID, Boolean> canDecideByIdea = new LinkedHashMap<>();
        for (Idea idea : ideas) {
            canDecideByIdea.put(idea.getId(), canDecide(currentUser, idea));
        }
        model.addAttribute("ideas", ideas);
        model.addAttribute("canDecideByIdea", canDecideByIdea);
        return "idea-queue";
    }

    @GetMapping("/app/ideas/{ideaId}")
    public String detail(@PathVariable UUID ideaId, @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        Idea idea = ideaRepository.findById(ideaId).orElseThrow(() -> DomainException.notFound("Idea not found"));
        model.addAttribute("idea", idea);
        model.addAttribute("canDecide", canDecide(principal.user(), idea));
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
