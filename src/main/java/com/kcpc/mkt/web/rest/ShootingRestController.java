package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.planning.dto.ContentPlanResponse;
import com.kcpc.mkt.production.dto.ReviewDecisionRequest;
import com.kcpc.mkt.production.service.ShootingService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API-OP-030..033 class: Shooting execution and Shoot Review. */
@RestController
@RequestMapping("/api/v1/content-plans/{id}/shooting")
public class ShootingRestController {

    private final ShootingService shootingService;

    public ShootingRestController(ShootingService shootingService) {
        this.shootingService = shootingService;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        shootingService.startShooting(principal.user(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/review/submit")
    public ResponseEntity<Void> submitReview(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        shootingService.submitShootReview(principal.user(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/review/decision")
    public ResponseEntity<ContentPlanResponse> decide(@PathVariable UUID id, @RequestBody ReviewDecisionRequest request,
                                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var plan = shootingService.decideShootReview(principal.user(), id, request.approve(), request.reason(),
                request.qualifyingRecipientUserIds());
        return ResponseEntity.ok(ContentPlanResponse.from(plan));
    }
}
