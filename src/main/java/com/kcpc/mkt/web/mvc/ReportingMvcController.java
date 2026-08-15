package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.audit.dto.AuditLogResponse;
import com.kcpc.mkt.audit.repository.SystemAuditLogRepository;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.reporting.dto.KpiValue;
import com.kcpc.mkt.reporting.service.AdminReportingService;
import com.kcpc.mkt.reporting.service.KpiService;
import com.kcpc.mkt.reporting.service.MultiFormatExportService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI/UX Design Specification v0.2 Group B (Analytics & Reporting, §7) and Group C (Data Export,
 * §8): Team Workload, Team KPI, 30-KPI Console, Admin-Action Report, Delayed Deliverables,
 * Audit-History Viewer, and Export. Every handler calls the same application/service layer as the
 * equivalent REST controller.
 */
@Controller
@org.springframework.web.bind.annotation.RequestMapping("/app")
public class ReportingMvcController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<String> CATEGORY_ORDER =
            List.of("OPERATIONAL", "PRODUCTIVITY", "CONTENT_UNITS", "APPROVAL_REVIEW", "DELAY_SLA");
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "OPERATIONAL", "Operational (KPI-001..007)",
            "PRODUCTIVITY", "Productivity (KPI-008..011)",
            "CONTENT_UNITS", "Content & Published Units (KPI-012..020)",
            "APPROVAL_REVIEW", "Approval & Review (KPI-021..024)",
            "DELAY_SLA", "Delay/SLA/On-Time (KPI-025..030)");

    private final KpiService kpiService;
    private final AdminReportingService adminReportingService;
    private final SystemAuditLogRepository auditLogRepository;
    private final AuthorizationService authorizationService;
    private final MultiFormatExportService multiFormatExportService;

    public ReportingMvcController(KpiService kpiService, AdminReportingService adminReportingService,
                                   SystemAuditLogRepository auditLogRepository, AuthorizationService authorizationService,
                                   MultiFormatExportService multiFormatExportService) {
        this.kpiService = kpiService;
        this.adminReportingService = adminReportingService;
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
        this.multiFormatExportService = multiFormatExportService;
    }

    private boolean allowed(KcpcUserPrincipal principal, OperationalPermission permission) {
        try {
            authorizationService.requireAuthority(principal.user(), permission, null, null);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    @GetMapping("/reports/workload")
    public String workload(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW)) {
            return "redirect:/app/home";
        }
        model.addAttribute("data", kpiService.teamWorkload(principal.user()));
        return "reports-workload";
    }

    @GetMapping("/reports/team-kpis")
    public String teamKpis(@RequestParam(required = false) LocalDate startDate,
                            @RequestParam(required = false) LocalDate endDate,
                            @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_15_TEAM_KPI_VIEW)) {
            return "redirect:/app/home";
        }
        model.addAttribute("data", kpiService.teamKpis(principal.user(), startDate, endDate));
        return "reports-team-kpis";
    }

    @GetMapping("/reports/kpis")
    public String kpiConsole(@RequestParam(required = false) String category,
                              @RequestParam(required = false) String kpiCode,
                              @RequestParam(required = false) LocalDate startDate,
                              @RequestParam(required = false) LocalDate endDate,
                              @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_15_TEAM_KPI_VIEW)) {
            return "redirect:/app/home";
        }
        List<KpiValue> all = kpiService.queryGovernedKpis(principal.user(), category, kpiCode, startDate, endDate);
        Map<String, List<KpiValue>> grouped = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) {
            List<KpiValue> inCategory = all.stream().filter(k -> k.getCategory().equals(cat)).toList();
            if (!inCategory.isEmpty()) {
                grouped.put(CATEGORY_LABELS.get(cat), inCategory);
            }
        }
        model.addAttribute("grouped", grouped);
        return "reports-kpi-console";
    }

    @GetMapping("/reports/admin-actions")
    public String adminActions(@RequestParam(required = false) LocalDate startDate,
                                @RequestParam(required = false) LocalDate endDate,
                                @RequestParam(required = false) String actionType,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW)) {
            return "redirect:/app/home";
        }
        model.addAttribute("report",
                adminReportingService.administrativeActionsReport(principal.user(), startDate, endDate, actionType));
        return "reports-admin-actions";
    }

    /** All authenticated roles, read-scoped server-side (SRS-REQ-002/066/067) - no separate MVC gate needed. */
    @GetMapping("/reports/delayed")
    public String delayed(@RequestParam(required = false) String stage, @RequestParam(required = false) String priority,
                           @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        model.addAttribute("rows", adminReportingService.delayedDeliverables(principal.user(), stage, priority));
        return "reports-delayed";
    }

    @GetMapping("/audit")
    public String auditHistory(@RequestParam(required = false) String actionType,
                                @RequestParam(required = false) LocalDate startDate,
                                @RequestParam(required = false) LocalDate endDate,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW)) {
            return "redirect:/app/home";
        }
        Instant from = startDate != null ? startDate.atStartOfDay(BUSINESS_ZONE).toInstant() : null;
        Instant to = endDate != null ? endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant() : null;
        List<AuditLogResponse> logs = auditLogRepository.findAllByOrderByEventTimestampDesc().stream()
                .filter(log -> actionType == null || actionType.isBlank() || log.getEventType().equalsIgnoreCase(actionType))
                .filter(log -> from == null || !log.getEventTimestamp().isBefore(from))
                .filter(log -> to == null || log.getEventTimestamp().isBefore(to))
                .map(AuditLogResponse::from)
                .limit(500)
                .toList();
        model.addAttribute("logs", logs);
        return "audit-history";
    }

    @GetMapping("/export")
    public String exportScreen(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (principal.user().resolvedAccessClass() != AccessClass.CEO_OWNER
                && principal.user().resolvedAccessClass() != AccessClass.MARKETING_MANAGER) {
            return "redirect:/app/home";
        }
        model.addAttribute("governedTables", multiFormatExportService.governedTables());
        return "export";
    }
}
