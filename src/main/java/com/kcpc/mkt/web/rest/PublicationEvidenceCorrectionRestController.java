package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.publishing.dto.CorrectEvidenceUrlRequest;
import com.kcpc.mkt.publishing.dto.PublicationEvidenceCorrectionResponse;
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

/** API-OP-041: Correct Publication Evidence URL (Permission #8 Action). */
@RestController
@RequestMapping("/api/v1/publishing/events/{eventId}")
public class PublicationEvidenceCorrectionRestController {

    private final PublishingService publishingService;

    public PublicationEvidenceCorrectionRestController(PublishingService publishingService) {
        this.publishingService = publishingService;
    }

    @PostMapping("/evidence-corrections")
    public ResponseEntity<PublicationEvidenceCorrectionResponse> correctEvidence(
            @PathVariable UUID eventId, @Valid @RequestBody CorrectEvidenceUrlRequest request,
            @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var correction = publishingService.correctEvidenceUrl(principal.user(), eventId, request.correctedEvidenceUrl(),
                request.correctionReason());
        return ResponseEntity.ok(PublicationEvidenceCorrectionResponse.from(correction));
    }
}
