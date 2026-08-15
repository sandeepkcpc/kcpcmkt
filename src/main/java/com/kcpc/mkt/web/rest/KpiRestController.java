package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.reporting.dto.KpiSummaryResponse;
import com.kcpc.mkt.reporting.service.KpiService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BFD §7 / Permission #15 (Team KPI Visibility). */
@RestController
@RequestMapping("/api/v1/kpis")
public class KpiRestController {

    private final KpiService kpiService;

    public KpiRestController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/summary")
    public KpiSummaryResponse summary(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return kpiService.summary(principal.user());
    }
}
