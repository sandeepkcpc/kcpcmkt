package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.workflow.dto.HoldRequest;
import com.kcpc.mkt.workflow.service.HoldService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** BR-063 / API-OP administrative class: Hold and Resume, keyed by Content Plan for API ergonomics. */
@RestController
@RequestMapping("/api/v1/content-plans/{id}")
public class HoldRestController {

    private final HoldService holdService;
    private final ContentPlanRepository contentPlanRepository;

    public HoldRestController(HoldService holdService, ContentPlanRepository contentPlanRepository) {
        this.holdService = holdService;
        this.contentPlanRepository = contentPlanRepository;
    }

    @PostMapping("/hold")
    public ResponseEntity<Void> hold(@PathVariable UUID id, @Valid @RequestBody HoldRequest request,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var plan = contentPlanRepository.findById(id).orElseThrow(() -> DomainException.notFound("Content Plan not found"));
        holdService.placeHold(principal.user(), plan.getWorkflowInstance(), request.reason(), request.expectedResumeDate());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resume")
    public ResponseEntity<Void> resume(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var plan = contentPlanRepository.findById(id).orElseThrow(() -> DomainException.notFound("Content Plan not found"));
        holdService.resume(principal.user(), plan.getWorkflowInstance());
        return ResponseEntity.ok().build();
    }
}
