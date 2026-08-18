package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.publishing.dto.AssignPublisherRequest;
import com.kcpc.mkt.publishing.dto.RecordPublicationRequest;
import com.kcpc.mkt.publishing.dto.TargetNaRequest;
import com.kcpc.mkt.publishing.service.PublishingService;
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

/** API-OP-038..045 class: Publishing execution, Actual Publication events, Target N/A. */
@RestController
@RequestMapping("/api/v1/content-plans/{id}/publishing")
public class PublishingRestController {

    private final PublishingService publishingService;
    private final UserRepository userRepository;

    public PublishingRestController(PublishingService publishingService, UserRepository userRepository) {
        this.publishingService = publishingService;
        this.userRepository = userRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        publishingService.startPublishing(principal.user(), id);
        return ResponseEntity.ok().build();
    }

    /** Not in the frozen API spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-035 (Model(s)-style Publishing Assignment chip-picker). */
    @PostMapping("/assignments")
    public ResponseEntity<Void> assignPublisher(@PathVariable UUID id, @Valid @RequestBody AssignPublisherRequest request,
                                                 @AuthenticationPrincipal KcpcUserPrincipal principal) {
        User publisher = userRepository.findById(request.publisherUserId())
                .orElseThrow(() -> DomainException.notFound("User not found: " + request.publisherUserId()));
        publishingService.assignPublisher(principal.user(), id, publisher);
        return ResponseEntity.ok().build();
    }

    /** Not in the frozen API spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-035 (Model(s)-style Publishing Assignment chip-picker). */
    @PostMapping("/assignments/{publisherUserId}/remove")
    public ResponseEntity<Void> removePublisher(@PathVariable UUID id, @PathVariable UUID publisherUserId,
                                                 @AuthenticationPrincipal KcpcUserPrincipal principal) {
        publishingService.removePublisher(principal.user(), id, publisherUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/events")
    public ResponseEntity<Void> recordEvent(@PathVariable UUID id, @Valid @RequestBody RecordPublicationRequest request,
                                             @AuthenticationPrincipal KcpcUserPrincipal principal) {
        publishingService.recordActualPublication(principal.user(), id, request.plannedOutputId(),
                request.publicationTargetId(), request.eventType(), request.actualPublicationTimestamp(),
                request.evidenceUrl());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/targets/na")
    public ResponseEntity<Void> designateNa(@PathVariable UUID id, @RequestBody TargetNaRequest request,
                                             @AuthenticationPrincipal KcpcUserPrincipal principal) {
        publishingService.designateTargetNA(principal.user(), request.plannedOutputId(), request.publicationTargetId(),
                request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/targets/na/{naRecordId}/reverse")
    public ResponseEntity<Void> reverseNa(@PathVariable UUID id, @PathVariable UUID naRecordId,
                                           @RequestBody TargetNaRequest request,
                                           @AuthenticationPrincipal KcpcUserPrincipal principal) {
        publishingService.reverseTargetNA(principal.user(), naRecordId, request.reason());
        return ResponseEntity.ok().build();
    }
}
