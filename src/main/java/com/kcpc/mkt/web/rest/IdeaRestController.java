package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.dto.IdeaResponse;
import com.kcpc.mkt.idea.dto.IdeaReviewDecisionRequest;
import com.kcpc.mkt.idea.dto.IdeaSubmissionRequest;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.idea.service.IdeaService;
import com.kcpc.mkt.marks.dto.CorrectPredefinedMarksRequest;
import com.kcpc.mkt.marks.dto.PredefinedMarkCorrectionResponse;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** API-OP-011..019 class: Idea Submission and Idea Review. */
@RestController
@RequestMapping("/api/v1/ideas")
public class IdeaRestController {

    private final IdeaService ideaService;
    private final IdeaRepository ideaRepository;

    public IdeaRestController(IdeaService ideaService, IdeaRepository ideaRepository) {
        this.ideaService = ideaService;
        this.ideaRepository = ideaRepository;
    }

    @PostMapping
    public ResponseEntity<IdeaResponse> submit(@Valid @RequestBody IdeaSubmissionRequest request,
                                                @AuthenticationPrincipal KcpcUserPrincipal principal) {
        Idea idea = ideaService.submit(principal.user(), request.title(), request.referenceLink(), request.notesRemarks());
        return ResponseEntity.status(HttpStatus.CREATED).body(IdeaResponse.from(idea));
    }

    @GetMapping
    public ResponseEntity<List<IdeaResponse>> list(@RequestParam(required = false) WorkflowStatus status) {
        List<Idea> ideas = status != null
                ? ideaRepository.findByWorkflowInstance_CurrentStatusCodeOrderBySubmittedAtAsc(status)
                : ideaRepository.findAllByOrderBySubmittedAtDesc();
        return ResponseEntity.ok(ideas.stream().map(IdeaResponse::from).toList());
    }

    @GetMapping("/{ideaId}")
    public ResponseEntity<IdeaResponse> get(@PathVariable UUID ideaId) {
        Idea idea = ideaRepository.findById(ideaId).orElseThrow(() -> DomainException.notFound("Idea not found"));
        return ResponseEntity.ok(IdeaResponse.from(idea));
    }

    @PostMapping("/{ideaId}/review")
    public ResponseEntity<IdeaResponse> review(@PathVariable UUID ideaId,
                                                @Valid @RequestBody IdeaReviewDecisionRequest request,
                                                @AuthenticationPrincipal KcpcUserPrincipal principal) {
        Idea idea = ideaService.decide(principal.user(), ideaId, request.decision(), request.reason(),
                request.cameramanMark(), request.editorMark());
        return ResponseEntity.ok(IdeaResponse.from(idea));
    }

    @PostMapping("/{ideaId}/reopen")
    public ResponseEntity<IdeaResponse> reopen(@PathVariable UUID ideaId,
                                                @AuthenticationPrincipal KcpcUserPrincipal principal) {
        Idea idea = ideaService.reopen(principal.user(), ideaId);
        return ResponseEntity.ok(IdeaResponse.from(idea));
    }

    /** API-OP-033: Correct Predefined Role Marks (Permission #1 Action). */
    @PostMapping("/{ideaId}/predefined-marks/corrections")
    public ResponseEntity<PredefinedMarkCorrectionResponse> correctPredefinedMarks(
            @PathVariable UUID ideaId, @Valid @RequestBody CorrectPredefinedMarksRequest request,
            @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var correction = ideaService.correctPredefinedMarks(principal.user(), ideaId, request.newCamerapersonMarks(),
                request.newEditorMarks(), request.correctionReason());
        return ResponseEntity.ok(PredefinedMarkCorrectionResponse.from(correction));
    }
}
