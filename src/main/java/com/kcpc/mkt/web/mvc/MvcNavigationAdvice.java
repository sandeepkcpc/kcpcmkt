package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.notification.domain.Notification;
import com.kcpc.mkt.notification.service.NotificationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.workflow.service.WorkspaceAccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Makes {@code accessClass}/{@code businessRoleName}/{@code canSeeAdministration} available to
 * every MVC view so the shared header/nav fragment (fragments/nav.jsp) can gate CEO-only links,
 * branch the nav by Business Role (ENG-067: Model gets "My Shoots" instead of "My Work"), and show
 * the consolidated "Administration" nav entry only to users with real admin access - consistently,
 * regardless of whether the handling controller method also sets them explicitly.
 */
@ControllerAdvice(basePackages = "com.kcpc.mkt.web.mvc")
public class MvcNavigationAdvice {

    private final AuthorizationService authorizationService;
    private final WorkspaceAccessService workspaceAccessService;
    private final NotificationService notificationService;

    public MvcNavigationAdvice(AuthorizationService authorizationService, WorkspaceAccessService workspaceAccessService,
                               NotificationService notificationService) {
        this.authorizationService = authorizationService;
        this.workspaceAccessService = workspaceAccessService;
        this.notificationService = notificationService;
    }

    /** Header bell badge - cheap indexed count query, same per-request-attribute pattern as every
     * other flag here (never N+1: one query per page load, not one per notification). */
    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal == null ? 0 : notificationService.unreadCount(principal.user());
    }

    /** Header dropdown's own "latest 5" list - rendered server-side on every page load, same as
     * the count above, so opening the dropdown needs no extra request. */
    @ModelAttribute("latestNotifications")
    public List<Notification> latestNotifications(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal == null ? List.of() : notificationService.listRecent(principal.user(), 5);
    }

    @ModelAttribute("accessClass")
    public AccessClass accessClass(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal == null ? null : principal.user().resolvedAccessClass();
    }

    @ModelAttribute("businessRoleName")
    public String businessRoleName(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null || principal.user().getBusinessRole() == null) {
            return null;
        }
        return principal.user().getBusinessRole().getRoleName();
    }

    /** Header profile menu (fragments/nav.jsp): the logged-in user's own name/email, never
     * hardcoded - same principal every other flag here already reads. */
    @ModelAttribute("currentUserFullName")
    public String currentUserFullName(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal == null ? null : principal.user().getFullName();
    }

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal == null ? null : principal.user().getEmail();
    }

    /**
     * Centralized workflow-participation nav rule (see AuthorizationService#isNonProductionEmployee,
     * the single source of truth this delegates to entirely - never re-derived here from role name
     * or permission grants): true only for an EMPLOYEE whose Business Role does not participate in
     * the Content Production workflow, in which case fragments/nav.jsp shows only My Ideas/Submit
     * Idea. WorkflowParticipationInterceptor enforces the same rule server-side for direct URLs.
     */
    @ModelAttribute("nonProductionEmployee")
    public boolean nonProductionEmployee(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal != null && authorizationService.isNonProductionEmployee(principal.user());
    }

    /**
     * Administration nav visibility: never designation-based. Users/Business Roles/Permissions
     * stay CEO_OWNER-only (see AdminMvcController#isCeo, unchanged); Publishing Catalogue alone
     * also accepts a delegated PERM_17_PLATFORM_CATALOGUE_MANAGE grant (AdminMvcController
     * #catalogue's own existing check, mirrored here exactly, not re-derived) - so a non-CEO/MM
     * Employee holding that one grant still sees "Administration" (landing on Catalogue only),
     * while a Marketing Manager with no delegated grant does not see it at all. The backend
     * re-validates every /app/admin/* route unconditionally regardless of this nav flag.
     */
    @ModelAttribute("canSeeAdministration")
    public boolean canSeeAdministration(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (authorizationService.hasNativeAuthority(principal.user())) {
            return true;
        }
        try {
            authorizationService.requireAuthority(principal.user(), OperationalPermission.PERM_17_PLATFORM_CATALOGUE_MANAGE,
                    LifecycleStage.ADMINISTRATIVE, null);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /**
     * My Work/My Shoots nav link for an EMPLOYEE: always shown for a Business Role that
     * participates in the workflow by default (unchanged prior behavior), and now ALSO shown for a
     * non-participating-by-default EMPLOYEE who holds an explicit PERM_18/19/08 grant or an active
     * Shoot/Edit/Publishing assignment - mirrors {@link WorkspaceAccessService#canReachMyWork}
     * exactly, the same method {@code WorkflowParticipationInterceptor} enforces server-side, so nav
     * and route reachability can never disagree.
     */
    @ModelAttribute("employeeCanSeeMyWork")
    public boolean employeeCanSeeMyWork(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        User user = principal.user();
        if (!authorizationService.isNonProductionEmployee(user)) {
            return true;
        }
        return workspaceAccessService.canReachMyWork(user);
    }

    /**
     * "My Work" is permission/assignment-driven end to end (see LandingMvcController#myWork's own
     * showShootTab/showEditTab/showPublishTab - each keyed to a live PERM_18/19/08 grant or a real
     * Shoot/Edit/Publishing assignment, never Business Role) - this flag is the raw version of that
     * same rule ({@link WorkspaceAccessService#canReachMyWork}, NOT short-circuited by "participates
     * in workflow by default" the way {@link #employeeCanSeeMyWork} is above), used specifically to
     * decide whether a Model/Talent - who always sees "My Shoots" regardless - should ALSO see a
     * "My Work" link. A Model who is separately granted e.g. EDIT_EXECUTION (or has a real Shoot/
     * Edit/Publishing assignment) gets exactly the same "My Work" reachability any other qualifying
     * Employee does; a Model with no such grant/assignment does not see the link, even though the
     * route itself already tolerates a direct visit (it would just render every stage tab hidden -
     * unchanged, pre-existing behavior, not a new gap this introduces).
     */
    @ModelAttribute("employeeHasMyWorkExecutionAccess")
    public boolean employeeHasMyWorkExecutionAccess(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal != null && workspaceAccessService.canReachMyWork(principal.user());
    }

    /**
     * "My Performance" nav link (placed right after "Submit Idea" in nav.jsp): shown to every
     * production-participating EMPLOYEE (Cameraperson/Editor/Publisher/Model alike - unchanged
     * default, same as {@link #employeeCanSeeMyWork}), and to a non-participating-by-default
     * EMPLOYEE only once they have real performance-bearing reachability ({@link
     * WorkspaceAccessService#canReachMyPerformance}) - mirrors the same route-reachability rule
     * {@code WorkflowParticipationInterceptor} enforces server-side.
     */
    @ModelAttribute("employeeCanSeeMyPerformance")
    public boolean employeeCanSeeMyPerformance(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        User user = principal.user();
        if (!authorizationService.isNonProductionEmployee(user)) {
            return true;
        }
        return workspaceAccessService.canReachMyPerformance(user);
    }

    /** Reviews nav link for an EMPLOYEE holding any review permission (spec §16.3). */
    @ModelAttribute("employeeCanSeeReviews")
    public boolean employeeCanSeeReviews(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal != null && workspaceAccessService.canReachReviews(principal.user());
    }

    /** Team nav link for an EMPLOYEE holding PERM_14_TEAM_WORKLOAD_VIEW (spec §16.4). */
    @ModelAttribute("employeeCanSeeTeam")
    public boolean employeeCanSeeTeam(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal != null && workspaceAccessService.canReachTeamWorkload(principal.user());
    }

    /** Reports nav link for an EMPLOYEE holding PERM_15 (KPI) and/or PERM_16 (Logs) - spec §16.5. */
    @ModelAttribute("employeeCanSeeReports")
    public boolean employeeCanSeeReports(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        User user = principal.user();
        return workspaceAccessService.canReachKpiReports(user) || workspaceAccessService.canReachLogs(user);
    }

    /** Where the EMPLOYEE "Reports" link should land: KPI Dashboard if reachable, else Logs. */
    @ModelAttribute("employeeReportsHref")
    public String employeeReportsHref(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null) {
            return "/app/reports/kpis";
        }
        User user = principal.user();
        if (workspaceAccessService.canReachKpiReports(user)) {
            return "/app/reports/kpis";
        }
        return "/app/audit";
    }
}
