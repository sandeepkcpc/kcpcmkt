package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.planning.dto.ContentPlanResponse;
import com.kcpc.mkt.production.dto.AssignEditorRequest;
import com.kcpc.mkt.production.dto.ReviewDecisionRequest;
import com.kcpc.mkt.production.service.EditingService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API-OP-034..037 class: Editor assignment, editing execution, and Edit Review. */
@RestController
@RequestMapping("/api/v1/content-plans/{id}/editing")
public class EditingRestController {

    private final EditingService editingService;
    private final UserRepository userRepository;

    public EditingRestController(EditingService editingService, UserRepository userRepository) {
        this.editingService = editingService;
        this.userRepository = userRepository;
    }

    @PostMapping("/assignments")
    public ResponseEntity<Void> assign(@PathVariable UUID id, @Valid @RequestBody AssignEditorRequest request,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        User editor = userRepository.findById(request.editorUserId())
                .orElseThrow(() -> DomainException.notFound("User not found: " + request.editorUserId()));
        editingService.assignEditor(principal.user(), id, editor);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        editingService.startEditing(principal.user(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/review/submit")
    public ResponseEntity<Void> submitReview(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        editingService.submitEditReview(principal.user(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/review/decision")
    public ResponseEntity<ContentPlanResponse> decide(@PathVariable UUID id, @RequestBody ReviewDecisionRequest request,
                                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var plan = editingService.decideEditReview(principal.user(), id, request.approve(), request.reason(),
                request.qualifyingRecipientUserIds());
        return ResponseEntity.ok(ContentPlanResponse.from(plan));
    }
}
