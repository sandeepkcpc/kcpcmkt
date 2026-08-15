package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.reporting.dto.DelayedDeliverableRow;
import com.kcpc.mkt.reporting.dto.KpiValue;
import com.kcpc.mkt.reporting.service.AdminReportingService;
import com.kcpc.mkt.reporting.service.KpiService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * API-DOM-009 (`SAD-COMP-009`) Analytics & Reporting: Team Workload (`API-OP-056`), Team KPIs
 * (`API-OP-057`), and the 30 governed KPIs (`API-OP-058`).
 */
@RestController
public class ReportingRestController {

    private final KpiService kpiService;
    private final AdminReportingService adminReportingService;

    public ReportingRestController(KpiService kpiService, AdminReportingService adminReportingService) {
        this.kpiService = kpiService;
        this.adminReportingService = adminReportingService;
    }

    @GetMapping("/api/v1/team/workload")
    public Map<String, Object> teamWorkload(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return kpiService.teamWorkload(principal.user());
    }

    @GetMapping("/api/v1/team/kpis")
    public Map<String, Object> teamKpis(@RequestParam(required = false) LocalDate startDate,
                                         @RequestParam(required = false) LocalDate endDate,
                                         @AuthenticationPrincipal KcpcUserPrincipal principal) {
        return kpiService.teamKpis(principal.user(), startDate, endDate);
    }

    @GetMapping("/api/v1/reports/kpis")
    public List<KpiValue> governedKpis(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) String kpiCode,
                                        @RequestParam(required = false) LocalDate startDate,
                                        @RequestParam(required = false) LocalDate endDate,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        return kpiService.queryGovernedKpis(principal.user(), category, kpiCode, startDate, endDate);
    }

    @GetMapping("/api/v1/reports/administrative-actions")
    public Map<String, Object> administrativeActions(@RequestParam(required = false) LocalDate startDate,
                                                       @RequestParam(required = false) LocalDate endDate,
                                                       @RequestParam(required = false) String actionType,
                                                       @AuthenticationPrincipal KcpcUserPrincipal principal) {
        return adminReportingService.administrativeActionsReport(principal.user(), startDate, endDate, actionType);
    }

    @GetMapping("/api/v1/reports/delayed-deliverables")
    public List<DelayedDeliverableRow> delayedDeliverables(@RequestParam(required = false) String stage,
                                                             @RequestParam(required = false) String priority,
                                                             @AuthenticationPrincipal KcpcUserPrincipal principal) {
        return adminReportingService.delayedDeliverables(principal.user(), stage, priority);
    }
}
