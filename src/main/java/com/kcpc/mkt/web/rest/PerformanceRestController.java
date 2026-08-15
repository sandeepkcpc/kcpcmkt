package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.performance.dto.ScorecardDraftRequest;
import com.kcpc.mkt.performance.dto.ScorecardResponse;
import com.kcpc.mkt.performance.service.PerformanceService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API-OP-046..051 class: Performance obligations and the Creative Performance Scorecard. */
@RestController
@RequestMapping("/api/v1/performance-obligations/{obligationId}/scorecard")
public class PerformanceRestController {

    private final PerformanceService performanceService;

    public PerformanceRestController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @PostMapping("/draft")
    public ResponseEntity<ScorecardResponse> saveDraft(@PathVariable UUID obligationId,
                                                         @RequestBody ScorecardDraftRequest request,
                                                         @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var scorecard = performanceService.saveDraft(principal.user(), obligationId, request.views3sec(),
                request.views3secIsNa(), request.plays(), request.averageWatchTimeSeconds(), request.watchTimeIsNa(),
                request.videoLengthSeconds(), request.videoLengthIsNa(), request.linkClicks(), request.clicksIsNa(),
                request.impressions());
        return ResponseEntity.ok(ScorecardResponse.from(scorecard));
    }

    @PostMapping("/submit")
    public ResponseEntity<ScorecardResponse> submit(@PathVariable UUID obligationId,
                                                      @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var scorecard = performanceService.submit(principal.user(), obligationId);
        return ResponseEntity.ok(ScorecardResponse.from(scorecard));
    }
}
