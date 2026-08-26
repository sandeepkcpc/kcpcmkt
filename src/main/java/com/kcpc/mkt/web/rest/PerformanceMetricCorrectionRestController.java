package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.performance.dto.CorrectScorecardMetricsRequest;
import com.kcpc.mkt.performance.dto.PerformanceMetricCorrectionResponse;
import com.kcpc.mkt.performance.service.PerformanceService;
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

/** API-OP-046: Correct Performance Scorecard Metrics (Permission #9 Action). */
@RestController
@RequestMapping("/api/v1/performance/scorecards/{scorecardId}")
public class PerformanceMetricCorrectionRestController {

    private final PerformanceService performanceService;

    public PerformanceMetricCorrectionRestController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @PostMapping("/corrections")
    public ResponseEntity<PerformanceMetricCorrectionResponse> correctMetrics(
            @PathVariable UUID scorecardId, @Valid @RequestBody CorrectScorecardMetricsRequest request,
            @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var correction = performanceService.correctMetrics(principal.user(), scorecardId, request.correctedHookRatePercent(),
                request.correctedHookRateIsNa(), request.correctedHoldRatePercent(), request.correctedHoldRateIsNa(),
                request.correctedViews(), request.correctedAverageViewDurationSeconds(),
                request.correctedAvgViewDurationIsNa(), request.correctionReason());
        return ResponseEntity.ok(PerformanceMetricCorrectionResponse.from(correction));
    }
}
