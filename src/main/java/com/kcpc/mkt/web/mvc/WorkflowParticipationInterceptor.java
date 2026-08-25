package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.workflow.service.WorkspaceAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Centralized server-side enforcement of workspace reachability for every {@code /app/**} page,
 * including a direct URL typed straight into the browser - deny-by-default so a future new
 * {@code /app/...} route can never accidentally leak through unchecked.
 * <p>
 * A Business Role that participates in the workflow by default (see
 * {@link AuthorizationService#isNonProductionEmployee}) is unaffected - full access, as before.
 * <p>
 * A non-participating-by-default EMPLOYEE is no longer an absolute all-or-nothing case: holding an
 * explicit operational permission grants reachability to ONLY that specific module (My Work,
 * Reviews, Team Workload, KPI Reports, Logs, Catalogue - see {@link WorkspaceAccessService}), never
 * a blanket "any permission unlocks every /app/** route" rule. Content Pipeline and full
 * Administration (Users/Business Roles/Permissions) remain CEO/MM-only regardless of any
 * permission an EMPLOYEE might hold. My Ideas/Submit Idea remain always reachable. This is a
 * reachability convenience only - every module's own controller/service still independently
 * re-checks the real authorization for the specific action/stage/item, exactly as before.
 */
public class WorkflowParticipationInterceptor implements HandlerInterceptor {

    private final AuthorizationService authorizationService;
    private final WorkspaceAccessService workspaceAccessService;

    public WorkflowParticipationInterceptor(AuthorizationService authorizationService,
                                             WorkspaceAccessService workspaceAccessService) {
        this.authorizationService = authorizationService;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof KcpcUserPrincipal principal)) {
            return true;
        }
        User user = principal.user();
        if (!authorizationService.isNonProductionEmployee(user)) {
            return true;
        }

        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();

        if (isUnder(uri, contextPath, "/app/ideas")) {
            return true;
        }
        if ((isUnder(uri, contextPath, "/app/my-work") || isUnder(uri, contextPath, "/app/deliverables"))
                && workspaceAccessService.canReachMyWork(user)) {
            return true;
        }
        if (isUnder(uri, contextPath, "/app/reviews") && workspaceAccessService.canReachReviews(user)) {
            return true;
        }
        if (isUnder(uri, contextPath, "/app/reports/workload") && workspaceAccessService.canReachTeamWorkload(user)) {
            return true;
        }
        if ((isUnder(uri, contextPath, "/app/reports/kpis") || isUnder(uri, contextPath, "/app/reports/team-kpis"))
                && workspaceAccessService.canReachKpiReports(user)) {
            return true;
        }
        if ((isUnder(uri, contextPath, "/app/audit") || isUnder(uri, contextPath, "/app/reports/admin-actions"))
                && workspaceAccessService.canReachLogs(user)) {
            return true;
        }
        if (isUnder(uri, contextPath, "/app/admin/catalogue") && workspaceAccessService.canReachCatalogue(user)) {
            return true;
        }
        // Content Pipeline, full Administration (Users/Business Roles/Permissions), /app/home
        // dispatch itself, and every module above without the matching permission: deny-by-default.
        response.sendRedirect(contextPath + "/app/ideas");
        return false;
    }

    private boolean isUnder(String uri, String contextPath, String path) {
        return uri.startsWith(contextPath + path);
    }
}
