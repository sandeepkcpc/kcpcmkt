package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.audit.domain.SystemAuditLog;
import com.kcpc.mkt.audit.dto.AuditLogResponse;
import com.kcpc.mkt.audit.repository.SystemAuditLogRepository;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.repository.BusinessRoleRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.reporting.dto.AdminActionRow;
import com.kcpc.mkt.reporting.dto.DelayedDeliverableRow;
import com.kcpc.mkt.reporting.service.AdminReportingService;
import com.kcpc.mkt.reporting.service.KpiDashboardService;
import com.kcpc.mkt.reporting.service.KpiService;
import com.kcpc.mkt.reporting.service.MultiFormatExportService;
import com.kcpc.mkt.reporting.service.TeamWorkloadService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UI/UX Design Specification v0.2 Group B (Analytics & Reporting, §7) and Group C (Data Export,
 * §8): KPI Dashboard, Delayed Deliverables, Administrative Actions, Audit History, and Export -
 * one unified CEO/MM Reports workspace, one shared secondary tab bar. Every handler calls the same
 * application/service layer as the equivalent REST controller.
 */
@Controller
@org.springframework.web.bind.annotation.RequestMapping("/app")
public class ReportingMvcController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Set<Integer> ADMIN_ACTIONS_PAGE_SIZES = Set.of(10, 25, 50);
    private static final Set<Integer> AUDIT_PAGE_SIZES = Set.of(10, 25, 50, 100);

    /** Export screen: business-friendly scope groups over the real governed-table union
     * (MultiFormatExportService#governedTables) - presentation-layer only, never exposed to the
     * backend differently than the raw table name it already accepts. Every table below is a real,
     * currently-governed table (confirmed against governedTables()); none invented, none dropped. */
    private static final Map<String, List<String>> EXPORT_SCOPE_GROUPS = new LinkedHashMap<>();
    static {
        EXPORT_SCOPE_GROUPS.put("Content", List.of("content_plans", "planned_outputs", "planning_preparers",
                "shooting_assignments", "editing_assignments", "shooting_execution_participants",
                "editing_execution_participants", "content_plan_talent_entries"));
        EXPORT_SCOPE_GROUPS.put("Ideas & Reviews", List.of("ideas", "review_cycles"));
        EXPORT_SCOPE_GROUPS.put("Publishing", List.of("publication_targets", "company_channels", "platforms",
                "planned_output_publication_target_mappings", "actual_publication_events",
                "publication_target_na_records", "publication_evidence_corrections"));
        EXPORT_SCOPE_GROUPS.put("Performance", List.of("performance_obligations", "creative_performance_scorecards",
                "performance_metric_corrections", "predefined_role_marks", "predefined_mark_corrections",
                "personal_mark_attributions"));
        EXPORT_SCOPE_GROUPS.put("Governance", List.of("workflow_instances", "workflow_transition_history",
                "reschedule_records", "reassignment_records", "reassignment_assignees", "cancellation_records",
                "reopen_records", "work_hold_records"));
    }
    private static final Map<String, String> TABLE_LABELS = Map.ofEntries(
            Map.entry("content_plans", "Content Pipeline"), Map.entry("planned_outputs", "Planned Outputs"),
            Map.entry("planning_preparers", "Planning Preparers"), Map.entry("shooting_assignments", "Shoot Assignments"),
            Map.entry("editing_assignments", "Edit Assignments"),
            Map.entry("shooting_execution_participants", "Shoot Participants"),
            Map.entry("editing_execution_participants", "Edit Participants"),
            Map.entry("content_plan_talent_entries", "Model Assignments"), Map.entry("ideas", "Ideas"),
            Map.entry("review_cycles", "Idea / Planning / Shoot / Edit Reviews"),
            Map.entry("publication_targets", "Publication Targets"), Map.entry("company_channels", "Channels"),
            Map.entry("platforms", "Platforms"),
            Map.entry("planned_output_publication_target_mappings", "Publication Scope Mappings"),
            Map.entry("actual_publication_events", "Publication Events"),
            Map.entry("publication_target_na_records", "Not-Applicable Records"),
            Map.entry("publication_evidence_corrections", "Evidence Corrections"),
            Map.entry("performance_obligations", "Performance Obligations"),
            Map.entry("creative_performance_scorecards", "Performance Scorecards"),
            Map.entry("performance_metric_corrections", "Performance Corrections"),
            Map.entry("predefined_role_marks", "Predefined Marks"),
            Map.entry("predefined_mark_corrections", "Mark Corrections"),
            Map.entry("personal_mark_attributions", "Personal Mark Attributions"),
            Map.entry("workflow_instances", "Workflow Instances"),
            Map.entry("workflow_transition_history", "Workflow Transition History"),
            Map.entry("reschedule_records", "Reschedules"), Map.entry("reassignment_records", "Reassignments"),
            Map.entry("reassignment_assignees", "Reassignment Assignees"),
            Map.entry("cancellation_records", "Cancellations"), Map.entry("reopen_records", "Reopens"),
            Map.entry("work_hold_records", "Holds"));

    private final KpiService kpiService;
    private final KpiDashboardService kpiDashboardService;
    private final AdminReportingService adminReportingService;
    private final SystemAuditLogRepository auditLogRepository;
    private final AuthorizationService authorizationService;
    private final MultiFormatExportService multiFormatExportService;
    private final TeamWorkloadService teamWorkloadService;
    private final BusinessRoleRepository businessRoleRepository;
    private final UserRepository userRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.kcpc.mkt.audit.service.AuditContentIdResolver auditContentIdResolver;

    public ReportingMvcController(KpiService kpiService, KpiDashboardService kpiDashboardService,
                                   AdminReportingService adminReportingService,
                                   SystemAuditLogRepository auditLogRepository, AuthorizationService authorizationService,
                                   MultiFormatExportService multiFormatExportService, TeamWorkloadService teamWorkloadService,
                                   BusinessRoleRepository businessRoleRepository, UserRepository userRepository,
                                   ContentPlanRepository contentPlanRepository,
                                   com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                   com.kcpc.mkt.audit.service.AuditContentIdResolver auditContentIdResolver) {
        this.kpiService = kpiService;
        this.kpiDashboardService = kpiDashboardService;
        this.adminReportingService = adminReportingService;
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
        this.multiFormatExportService = multiFormatExportService;
        this.teamWorkloadService = teamWorkloadService;
        this.businessRoleRepository = businessRoleRepository;
        this.userRepository = userRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.objectMapper = objectMapper;
        this.auditContentIdResolver = auditContentIdResolver;
    }

    /**
     * Upcoming Channel Plan calendar UX: serializes the exact same
     * {@code List<UpcomingPlanDateGroup>} the existing date-grouped list already renders (never a
     * second query/DTO) - embedded as a {@code <script type="application/json">} block (see
     * reports-kpi-overview.jspf), mirroring DeliverableMvcController#buildPlanningOptionsJson's
     * exact pattern. {@code "<"} is escaped so a Channel handle containing {@code </script>} can
     * never break out of the block.
     */
    private String buildUpcomingChannelPlanJson(List<com.kcpc.mkt.reporting.dto.UpcomingPlanDateGroup> groups) {
        try {
            return objectMapper.writeValueAsString(groups).replace("<", "\\u003c");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Upcoming Channel Plan calendar data", e);
        }
    }

    private static boolean isAjax(String requestedWith) {
        return "fetch".equals(requestedWith);
    }

    private boolean allowed(KcpcUserPrincipal principal, OperationalPermission permission) {
        try {
            authorizationService.requireAuthority(principal.user(), permission, null, null);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /**
     * ENG-087: "Team Workload"/"Team KPI" consolidated into one "Team" nav entry with
     * Workload/Performance tabs inside the page itself (reports-workload.jsp/
     * reports-team-kpis.jsp both render the same tab header) - both pages/routes/
     * permission gates (PERM_14/PERM_15) are unchanged, this is nav presentation only.
     */
    @GetMapping("/reports/workload")
    public String workload(@RequestParam(required = false) String businessRole,
                            @RequestParam(required = false) UUID employeeId,
                            @RequestParam(required = false) String stage,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateTo,
                            @RequestParam(required = false, defaultValue = "false") boolean delayedOnly,
                            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                            @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW)) {
            return "redirect:/app/home";
        }
        model.addAttribute("result", teamWorkloadService.teamWorkloadDashboard(
                principal.user(), businessRole, employeeId, stage, dateFrom, dateTo, delayedOnly));
        model.addAttribute("businessRoles", businessRoleRepository.findByActiveTrue());
        model.addAttribute("employees", employeeId == null && (businessRole == null || businessRole.isBlank() || "ALL".equalsIgnoreCase(businessRole))
                ? userRepository.findByActiveTrueOrderByFullNameAsc()
                : userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc(businessRole));
        model.addAttribute("selectedBusinessRole", businessRole == null ? "" : businessRole);
        model.addAttribute("selectedEmployeeId", employeeId);
        model.addAttribute("selectedStage", stage == null ? "" : stage);
        model.addAttribute("selectedDateFrom", dateFrom);
        model.addAttribute("selectedDateTo", dateTo);
        model.addAttribute("selectedDelayedOnly", delayedOnly);
        model.addAttribute("canViewTeamKpi", allowed(principal, OperationalPermission.PERM_15_TEAM_KPI_VIEW));
        return isAjax(requestedWith) ? "reports-workload-content" : "reports-workload";
    }

    @GetMapping("/reports/team-kpis")
    public String teamKpis(@RequestParam(required = false) LocalDate startDate,
                            @RequestParam(required = false) LocalDate endDate,
                            @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_15_TEAM_KPI_VIEW)) {
            return "redirect:/app/home";
        }
        model.addAttribute("data", kpiService.teamKpis(principal.user(), startDate, endDate));
        model.addAttribute("canViewTeamWorkload", allowed(principal, OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW));
        return "reports-team-kpis";
    }

    // ============================================================================
    // The 5-tab CEO/MM Reports workspace: KPI Dashboard / Delayed Deliverables /
    // Administrative Actions / Audit History / Export - one shared tab bar fragment,
    // one shared "Data as of" convention.
    // ============================================================================

    private static final Set<String> KPI_DASHBOARD_VIEWS = Set.of("overview", "workflow", "content", "quality", "performance");

    /**
     * KPI Dashboard (spec: Overview / Workflow &amp; SLA / Content &amp; Publishing / Quality &amp;
     * Reviews / Performance) - a real server-rendered {@code view=} query param, not client-only
     * JS state, so Refresh/Back/Forward/deep-links all work; an invalid/missing {@code view} falls
     * back to {@code overview}. The same {@code startDate}/{@code endDate} apply to every view
     * (carried forward via a hidden field in the shared date-range form - see
     * fragments/reports-kpi-dashboard-content.jspf). Supersedes the old flat KPI-001..030 console
     * at this same route; {@link KpiService#queryGovernedKpis} and its REST endpoint
     * ({@code GET /api/v1/reports/kpis}) are untouched for any other caller.
     */
    @GetMapping("/reports/kpis")
    public String kpiDashboard(@RequestParam(required = false) String view,
                                @RequestParam(required = false) LocalDate startDate,
                                @RequestParam(required = false) LocalDate endDate,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_15_TEAM_KPI_VIEW)) {
            return "redirect:/app/home";
        }
        String effectiveView = view != null && KPI_DASHBOARD_VIEWS.contains(view) ? view : "overview";
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate effectiveStart = startDate != null ? startDate : today.minusDays(29);
        LocalDate effectiveEnd = endDate != null ? endDate : today;
        // Date Range Filter Enhancement: an inverted range (e.g. a hand-edited URL bypassing the
        // client-side Apply-time validation) must never silently compute KPIs backwards - fall
        // back to the exact same default window the missing-param case already uses, rather than
        // swapping the values or rejecting the request (this AJAX region has no error-response
        // handling of its own; a 400 here would trigger reports-workspace.js's real-navigation
        // fallback, a jarring full-page reload for what is really just a malformed filter).
        if (effectiveStart.isAfter(effectiveEnd)) {
            effectiveStart = today.minusDays(29);
            effectiveEnd = today;
        }
        model.addAttribute("kpiView", effectiveView);
        model.addAttribute("selectedDateFrom", effectiveStart);
        model.addAttribute("selectedDateTo", effectiveEnd);
        model.addAttribute("selectedDatePreset", matchingDatePreset(effectiveStart, effectiveEnd, today));
        switch (effectiveView) {
            case "workflow" -> model.addAttribute("workflowSla",
                    kpiDashboardService.workflowSla(principal.user(), effectiveStart, effectiveEnd));
            case "content" -> model.addAttribute("contentPublishing",
                    kpiDashboardService.contentPublishing(principal.user(), effectiveStart, effectiveEnd));
            case "quality" -> model.addAttribute("qualityReviews",
                    kpiDashboardService.qualityReviews(principal.user(), effectiveStart, effectiveEnd));
            case "performance" -> model.addAttribute("performanceDashboard",
                    kpiDashboardService.performance(principal.user(), effectiveStart, effectiveEnd));
            default -> {
                var overview = kpiDashboardService.overview(principal.user(), effectiveStart, effectiveEnd);
                model.addAttribute("overview", overview);
                // Upcoming Channel Plan calendar UX: the exact same, already-computed
                // overview.upcomingChannelPlan the existing date-grouped list already renders -
                // embedded as JSON purely so the calendar can highlight/browse it client-side
                // without a second query or a second "outstanding" definition. No new business
                // calculation; this is a different presentation of data already on the page.
                model.addAttribute("upcomingChannelPlanJson",
                        buildUpcomingChannelPlanJson(overview.getUpcomingChannelPlan()));
            }
        }
        addReportsShellAttributes(model, "kpis", principal);
        return isAjax(requestedWith) ? "reports-kpi-console-content" : "reports-kpi-console";
    }

    /**
     * Date Range Filter Enhancement: presets are resolved to concrete {@code startDate}/
     * {@code endDate} entirely client-side before submit (kpiDashboard() above never receives or
     * needs a "preset" input - the existing startDate/endDate contract is completely unchanged) -
     * this is the reverse lookup purely for which {@code <option>} the Date Range dropdown should
     * render as selected, computed with the exact same Asia/Kolkata {@code today} the effective
     * range itself already uses. Display-only; never used for KPI filtering.
     */
    private String matchingDatePreset(LocalDate start, LocalDate end, LocalDate today) {
        if (start.equals(today) && end.equals(today)) {
            return "today";
        }
        LocalDate yesterday = today.minusDays(1);
        if (start.equals(yesterday) && end.equals(yesterday)) {
            return "yesterday";
        }
        if (start.equals(today.minusDays(6)) && end.equals(today)) {
            return "last7";
        }
        if (start.equals(today.minusDays(29)) && end.equals(today)) {
            return "last30";
        }
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);
        if (start.equals(firstOfThisMonth) && end.equals(today)) {
            return "thisMonth";
        }
        LocalDate firstOfLastMonth = firstOfThisMonth.minusMonths(1);
        LocalDate lastOfLastMonth = firstOfThisMonth.minusDays(1);
        if (start.equals(firstOfLastMonth) && end.equals(lastOfLastMonth)) {
            return "lastMonth";
        }
        return "custom";
    }

    /**
     * Overview -&gt; Current Work Ownership -&gt; "Open" drill-down: a read-only, on-demand fragment
     * for exactly one employee's currently pending/delayed work (never computed for every employee
     * up front on the main KPI page load). PERM_15_TEAM_KPI_VIEW is re-checked here independently -
     * this endpoint is its own authorization boundary, not merely hidden behind a link on a page
     * that already checked it. Renders a small fragment (never JSON) to match this app's existing
     * AJAX-partial convention; a request with no valid session lands on /login exactly like any
     * other /app/** route (see SecurityConfig's appFilterChain), never a raw 401/JSON leak.
     */
    @GetMapping("/reports/kpis/ownership-drilldown")
    public String ownershipDrilldown(@RequestParam UUID employeeId,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_15_TEAM_KPI_VIEW)) {
            return "redirect:/app/home";
        }
        var drillDown = kpiDashboardService.employeeWorkDrillDown(principal.user(), employeeId);
        model.addAttribute("drillDown", drillDown);
        return "reports-kpi-ownership-drilldown";
    }

    /** All authenticated roles, read-scoped server-side (SRS-REQ-002/066/067) - no separate MVC gate needed. */
    @GetMapping("/reports/delayed")
    public String delayed(@RequestParam(required = false) String q,
                           @RequestParam(required = false) String stage, @RequestParam(required = false) String priority,
                           @RequestParam(required = false, defaultValue = "1") int page,
                           @RequestParam(required = false, defaultValue = "10") int pageSize,
                           @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                           @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        List<DelayedDeliverableRow> all = adminReportingService.delayedDeliverables(principal.user(), stage, priority);
        String qLower = q == null ? "" : q.trim().toLowerCase();
        List<DelayedDeliverableRow> filtered = all.stream()
                .filter(r -> qLower.isEmpty() || r.getContentId().toLowerCase().contains(qLower)
                        || (r.getContentTitle() != null && r.getContentTitle().toLowerCase().contains(qLower)))
                .toList();

        int effectiveSize = ADMIN_ACTIONS_PAGE_SIZES.contains(pageSize) ? pageSize : DEFAULT_PAGE_SIZE;
        int totalCount = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) effectiveSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = Math.min((currentPage - 1) * effectiveSize, totalCount);
        int toIndex = Math.min(fromIndex + effectiveSize, totalCount);

        model.addAttribute("rows", totalCount == 0 ? List.of() : filtered.subList(fromIndex, toIndex));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("selectedStage", stage == null ? "" : stage);
        model.addAttribute("selectedPriority", priority == null ? "" : priority);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("fromIndex", totalCount == 0 ? 0 : fromIndex + 1);
        model.addAttribute("toIndex", toIndex);
        model.addAttribute("pageSize", effectiveSize);
        addReportsShellAttributes(model, "delayed", principal);
        return isAjax(requestedWith) ? "reports-delayed-content" : "reports-delayed";
    }

    @GetMapping("/reports/admin-actions")
    public String adminActions(@RequestParam(required = false) LocalDate startDate,
                                @RequestParam(required = false) LocalDate endDate,
                                @RequestParam(required = false) String actionType,
                                @RequestParam(required = false) String performedBy,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false, defaultValue = "1") int page,
                                @RequestParam(required = false, defaultValue = "10") int pageSize,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW)) {
            return "redirect:/app/home";
        }
        List<AdminActionRow> all = adminReportingService.administrativeActionRows(
                principal.user(), startDate, endDate, actionType);
        String qLower = q == null ? "" : q.trim().toLowerCase();
        List<AdminActionRow> filtered = all.stream()
                .filter(r -> performedBy == null || performedBy.isBlank() || performedBy.equalsIgnoreCase(r.getPerformedByName()))
                .filter(r -> qLower.isEmpty() || (r.getContentOrUserDisplay() != null && r.getContentOrUserDisplay().toLowerCase().contains(qLower)))
                .toList();

        int effectiveSize = ADMIN_ACTIONS_PAGE_SIZES.contains(pageSize) ? pageSize : DEFAULT_PAGE_SIZE;
        int totalCount = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) effectiveSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = Math.min((currentPage - 1) * effectiveSize, totalCount);
        int toIndex = Math.min(fromIndex + effectiveSize, totalCount);

        Set<String> performers = new LinkedHashSet<>();
        all.forEach(r -> performers.add(r.getPerformedByName()));

        model.addAttribute("rows", totalCount == 0 ? List.of() : filtered.subList(fromIndex, toIndex));
        model.addAttribute("performers", performers);
        model.addAttribute("selectedActionType", actionType == null ? "" : actionType);
        model.addAttribute("selectedPerformedBy", performedBy == null ? "" : performedBy);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("selectedDateFrom", startDate);
        model.addAttribute("selectedDateTo", endDate);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("fromIndex", totalCount == 0 ? 0 : fromIndex + 1);
        model.addAttribute("toIndex", toIndex);
        model.addAttribute("pageSize", effectiveSize);
        addReportsShellAttributes(model, "admin-actions", principal);
        return isAjax(requestedWith) ? "reports-admin-actions-content" : "reports-admin-actions";
    }

    /** Audit History: genuinely unbounded, so this is real server-side pagination
     * (SystemAuditLogRepository#search) - only one page is ever loaded, never the whole table. */
    @GetMapping("/audit")
    public String auditHistory(@RequestParam(required = false) UUID actorId,
                                @RequestParam(required = false) String actionType,
                                @RequestParam(required = false) String eventCategory,
                                @RequestParam(required = false) String contentId,
                                @RequestParam(required = false) LocalDate startDate,
                                @RequestParam(required = false) LocalDate endDate,
                                @RequestParam(required = false, defaultValue = "1") int page,
                                @RequestParam(required = false, defaultValue = "10") int pageSize,
                                @RequestParam(required = false, defaultValue = "desc") String sort,
                                @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!allowed(principal, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW)) {
            return "redirect:/app/home";
        }
        Instant from = startDate != null ? startDate.atStartOfDay(BUSINESS_ZONE).toInstant() : Instant.EPOCH;
        Instant to = endDate != null ? endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant() : Instant.now();

        // "Content ID" is a real, resolvable filter: look the string up against content_plans,
        // never a fake/partial match on an internal UUID.
        UUID targetEntityId = null;
        boolean contentIdHadNoMatch = false;
        if (contentId != null && !contentId.isBlank()) {
            var plan = contentPlanRepository.findByContentId(contentId.trim());
            if (plan.isPresent()) {
                targetEntityId = plan.get().getId();
            } else {
                contentIdHadNoMatch = true;
            }
        }

        int effectiveSize = AUDIT_PAGE_SIZES.contains(pageSize) ? pageSize : DEFAULT_PAGE_SIZE;
        int zeroBasedPage = Math.max(0, page - 1);
        Sort.Direction direction = "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        List<AuditLogResponse> logs;
        long totalCount;
        int totalPages;
        int currentPage;
        if (contentIdHadNoMatch) {
            logs = List.of();
            totalCount = 0;
            totalPages = 1;
            currentPage = 1;
        } else {
            Page<SystemAuditLog> resultPage = auditLogRepository.search(actorId,
                    actionType != null && !actionType.isBlank() ? actionType : null, targetEntityId,
                    eventCategory != null && !eventCategory.isBlank() ? eventCategory : null, from, to,
                    PageRequest.of(zeroBasedPage, effectiveSize, Sort.by(direction, "eventTimestamp")));
            // Content ID column (Logs page enhancement): resolved per row, display-only - never
            // changes which rows match the search/filters above, never a second "outstanding"
            // definition, never touches audit creation. See AuditContentIdResolver's own header
            // comment for the full target-entity-name -> Content Plan resolution map.
            logs = resultPage.getContent().stream()
                    .map(log -> AuditLogResponse.from(log,
                            auditContentIdResolver.resolveContentId(log.getTargetEntityName(), log.getTargetEntityId())))
                    .toList();
            totalCount = resultPage.getTotalElements();
            totalPages = Math.max(1, resultPage.getTotalPages());
            currentPage = resultPage.getNumber() + 1;
        }

        model.addAttribute("logs", logs);
        model.addAttribute("actors", userRepository.findByActiveTrueOrderByFullNameAsc());
        model.addAttribute("actionTypes", auditLogRepository.findDistinctEventTypes());
        model.addAttribute("eventCategories", auditLogRepository.findDistinctEventCategories());
        model.addAttribute("selectedActorId", actorId);
        model.addAttribute("selectedActionType", actionType == null ? "" : actionType);
        model.addAttribute("selectedEventCategory", eventCategory == null ? "" : eventCategory);
        model.addAttribute("selectedContentId", contentId == null ? "" : contentId);
        model.addAttribute("selectedDateFrom", startDate);
        model.addAttribute("selectedDateTo", endDate);
        model.addAttribute("sort", sort);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("fromIndex", totalCount == 0 ? 0 : (long) (currentPage - 1) * effectiveSize + 1);
        model.addAttribute("toIndex", Math.min((long) currentPage * effectiveSize, totalCount));
        model.addAttribute("pageSize", effectiveSize);
        addReportsShellAttributes(model, "audit", principal);
        return isAjax(requestedWith) ? "audit-history-content" : "audit-history";
    }

    @GetMapping("/export")
    public String exportScreen(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (principal.user().resolvedAccessClass() != AccessClass.CEO_OWNER
                && principal.user().resolvedAccessClass() != AccessClass.MARKETING_MANAGER) {
            return "redirect:/app/home";
        }
        // Friendly grouping is a display-layer constant over the real governedTables() union - the
        // checkbox values submitted are still the exact same raw table names the export endpoint
        // already accepts; nothing about permission/format/table-resolution logic changes.
        Set<String> governed = Set.copyOf(multiFormatExportService.governedTables());
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (var entry : EXPORT_SCOPE_GROUPS.entrySet()) {
            List<String> present = entry.getValue().stream().filter(governed::contains).toList();
            if (!present.isEmpty()) {
                groups.put(entry.getKey(), present);
            }
        }
        model.addAttribute("scopeGroups", groups);
        model.addAttribute("tableLabels", TABLE_LABELS);
        addReportsShellAttributes(model, "export", principal);
        return "export";
    }

    /**
     * Spec §16.5: the shared Reports tab bar must never expose a management-only section merely
     * because the viewer holds one report permission - each tab's visibility here mirrors that
     * tab's own MVC-level gate exactly (Delayed Deliverables/Export stay CEO/MM-only, matching
     * their controller methods above; KPI/Admin Actions/Logs mirror PERM_15/PERM_16).
     */
    private void addReportsShellAttributes(Model model, String activeReportTab, KcpcUserPrincipal principal) {
        model.addAttribute("activeReportTab", activeReportTab);
        model.addAttribute("reportsDataAsOf", Instant.now());
        boolean nativeAuthority = principal != null && authorizationService.hasNativeAuthority(principal.user());
        boolean canViewKpi = principal != null && allowed(principal, OperationalPermission.PERM_15_TEAM_KPI_VIEW);
        boolean canViewAuditPerm = principal != null && allowed(principal, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW);
        model.addAttribute("canViewKpiTab", canViewKpi);
        model.addAttribute("canViewDelayedTab", nativeAuthority);
        model.addAttribute("canViewAdminActionsTab", canViewAuditPerm);
        model.addAttribute("canViewLogsTab", canViewAuditPerm);
        model.addAttribute("canViewExportTab", nativeAuthority);
    }
}
