<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="currentPath" value="${empty requestScope['jakarta.servlet.forward.request_uri'] ? pageContext.request.requestURI : requestScope['jakarta.servlet.forward.request_uri']}" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<%-- Header refinement: logo, primary nav and the notification/profile actions all now live in one
     unified row (.app-header) instead of a separate brand row + a second, darker nav strip below
     it - same nav links/routes/active-state logic, same notification/profile markup, just
     relocated into a single flex row so the whole header reads as one cohesive unit. --%>
<header class="app-header">
    <span class="brand">
        <img src="${ctx}/images/kcpc-logo.png" alt="KCPC Bandhani" class="brand-logo">
    </span>
    <nav class="app-nav">
    <%-- ENG-057: EMPLOYEE-class users (Cameraperson/Editor/Publisher/Model etc.) get a minimal
         3-tab nav - the full company-wide reporting/admin nav below is CEO/MM-only territory
         regardless of any individual permission grant an Employee might separately hold, matching
         the "no management/admin controls for employees" rule the My Work redesign asked for. --%>
    <c:choose>
        <c:when test="${accessClass == 'EMPLOYEE'}">
            <%-- Permission-driven multi-function workflow: a Business Role that participates in
                 the workflow by default keeps its existing My Work/My Shoots link unchanged. A
                 non-participating-by-default EMPLOYEE (e.g. HR Manager) now ALSO sees the relevant
                 module link the moment they hold the matching explicit permission grant (or, for
                 My Work, an active execution assignment) - never a blanket "any permission unlocks
                 everything" rule, one flag per module (${employeeCanSeeMyWork}/
                 ${employeeCanSeeReviews}/${employeeCanSeeTeam}/${employeeCanSeeReports}), each
                 mirroring WorkspaceAccessService exactly, the same source WorkflowParticipation-
                 Interceptor enforces server-side - nav and route reachability can never disagree. --%>
            <c:if test="${employeeCanSeeMyWork}">
                <%-- ENG-067: Model employees always get "My Shoots" (their own dedicated,
                     read-only shoot-participation screen), in place of "My Work" - a Model holds
                     no execution assignment/permission by default, so the task-execution "My
                     Work" concept doesn't apply to them out of the box. Permission-based, not
                     role-based: if a Model is separately granted an execution permission
                     (SHOOT_EXECUTION/EDIT_EXECUTION/PUBLISH_EXECUTION) or holds a real Shoot/Edit/
                     Publishing assignment, "My Work" becomes ADDITIONALLY reachable alongside "My
                     Shoots" - landing on the exact same page every other qualifying Employee uses,
                     whose own Shoot/Edit/Publishing tabs are already entirely permission/
                     assignment-driven (LandingMvcController#myWork's showShootTab/showEditTab/
                     showPublishTab - never Business-Role-checked), so a Model with
                     EDIT_EXECUTION sees exactly an Edit tab there, no Model-specific code needed. --%>
                <c:choose>
                    <c:when test="${businessRoleName == 'Model'}">
                        <a class="${currentPath == ctx.concat('/app/my-shoots') ? 'active' : ''}" href="${ctx}/app/my-shoots">My Shoots</a>
                        <c:if test="${employeeHasMyWorkExecutionAccess}">
                            <a class="${currentPath == ctx.concat('/app/my-work') ? 'active' : ''}" href="${ctx}/app/my-work">My Work</a>
                        </c:if>
                    </c:when>
                    <c:otherwise>
                        <a class="${currentPath == ctx.concat('/app/my-work') ? 'active' : ''}" href="${ctx}/app/my-work">My Work</a>
                    </c:otherwise>
                </c:choose>
            </c:if>
            <c:if test="${employeeCanSeeReviews}">
                <a class="${currentPath == ctx.concat('/app/reviews') ? 'active' : ''}" href="${ctx}/app/reviews">Reviews</a>
            </c:if>
            <c:if test="${employeeCanSeeTeam}">
                <a class="${currentPath == ctx.concat('/app/reports/workload') or currentPath == ctx.concat('/app/reports/team-kpis') ? 'active' : ''}"
                   href="${ctx}/app/reports/workload">Team</a>
            </c:if>
            <c:if test="${employeeCanSeeReports}">
                <a class="${currentPath == ctx.concat('/app/reports/kpis') or currentPath == ctx.concat('/app/audit') ? 'active' : ''}"
                   href="${ctx}${employeeReportsHref}">Reports</a>
            </c:if>
            <%-- ENG-059: /app/ideas now serves a dedicated own-ideas-only "My Ideas" page for
                 EMPLOYEE-class users (same route as the CEO/MM Idea Queue, branched server-side) -
                 no longer the my-work.jsp#my-ideas anchor ENG-057 used as a stopgap. --%>
            <a class="${currentPath == ctx.concat('/app/ideas') ? 'active' : ''}" href="${ctx}/app/ideas">My Ideas</a>
            <a class="${currentPath == ctx.concat('/app/ideas/new') ? 'active' : ''}" href="${ctx}/app/ideas/new">Submit Idea</a>
            <%-- My Performance: the employee's own self-service marks/completion/delay dashboard
                 (split out of My Work's former Marks sub-tab) - shown alongside My Work rather than
                 nested inside it, mirroring the same permission/assignment-driven visibility rule
                 (${employeeCanSeeMyPerformance}, from WorkspaceAccessService#canReachMyPerformance,
                 the same source WorkflowParticipationInterceptor enforces server-side). --%>
            <c:if test="${employeeCanSeeMyPerformance}">
                <a class="${currentPath == ctx.concat('/app/my-performance') ? 'active' : ''}" href="${ctx}/app/my-performance">My Performance</a>
            </c:if>
            <%-- spec §16.6 / audit-identified nav-backend mismatch fix: canSeeAdministration
                 already correctly evaluates true for a PERM_17-holding EMPLOYEE (it never checks
                 Access Class, only native authority OR the PERM_17 grant), but was previously only
                 rendered in the CEO/MM branch below, leaving such an EMPLOYEE with backend route
                 access to Catalogue and no nav link to reach it. Reused verbatim here (not
                 re-derived) - lands on Catalogue only, never Users/Business Roles/Permissions,
                 since AdminMvcController's own gates are unchanged. --%>
            <c:if test="${canSeeAdministration}">
                <a class="${currentPath == ctx.concat('/app/admin/catalogue') ? 'active' : ''}" href="${ctx}/app/admin/catalogue">Administration</a>
            </c:if>
        </c:when>
        <c:otherwise>
            <%-- CEO_OWNER and MARKETING_MANAGER share this branch (the only two remaining
                 AccessClass values here). Idea Queue is gone for both: Idea review now lives
                 entirely inside Reviews -> Ideas, and "My Ideas" below is each user's own-
                 submissions history, never the review queue - see IdeaMvcController#queue, which
                 now branches every AccessClass other than none... EMPLOYEE, MARKETING_MANAGER AND
                 CEO_OWNER all reuse the exact same #myIdeas() method/query rather than a parallel
                 implementation. Order is deliberately fixed here (not alphabetical/incidental):
                 Content Pipeline, Reviews, Team, Reports, My Ideas, Submit Idea, Administration
                 last. --%>
            <a class="${currentPath == ctx.concat('/app/pipeline') ? 'active' : ''}" href="${ctx}/app/pipeline">Content Pipeline</a>
            <a class="${currentPath == ctx.concat('/app/reviews') ? 'active' : ''}" href="${ctx}/app/reviews">Reviews</a>
            <%-- ENG-087: "Team Workload"/"Team KPI" consolidated into one "Team" nav entry with
                 Workload/Performance tabs inside the page itself (reports-workload.jsp/
                 reports-team-kpis.jsp both render the same tab header) - both pages/routes/
                 permission gates (PERM_14/PERM_15) are unchanged, this is nav presentation only. --%>
            <a class="${currentPath == ctx.concat('/app/reports/workload') or currentPath == ctx.concat('/app/reports/team-kpis') ? 'active' : ''}"
               href="${ctx}/app/reports/workload">Team</a>
            <%-- CEO/MM Reports Workspace: KPI Console/Delayed Deliverables/Admin Actions/Logs/Export
                 consolidated into one "Reports" nav entry (same ENG-087 pattern as "Team" above) -
                 the secondary tab bar inside reports-tabs.jspf handles switching between the 5
                 screens; this is nav presentation only, none of the 5 routes/permission gates change. --%>
            <a class="${currentPath == ctx.concat('/app/reports/kpis') or currentPath == ctx.concat('/app/reports/delayed')
                        or currentPath == ctx.concat('/app/reports/admin-actions') or currentPath == ctx.concat('/app/audit')
                        or currentPath == ctx.concat('/app/export') ? 'active' : ''}"
               href="${ctx}/app/reports/kpis">Reports</a>
            <a class="${currentPath == ctx.concat('/app/ideas') ? 'active' : ''}" href="${ctx}/app/ideas">My Ideas</a>
            <a class="${currentPath == ctx.concat('/app/ideas/new') ? 'active' : ''}" href="${ctx}/app/ideas/new">Submit Idea</a>
            <%-- Administration shell: Users/Business Roles/Permissions/Publishing Catalogue
                 consolidated into one nav entry (same ENG-087 pattern as "Team"/"Reports" above) -
                 the secondary tab bar inside admin-tabs.jspf handles switching between the 4
                 screens. Visibility is real-access-based (${canSeeAdministration}, from
                 MvcNavigationAdvice - mirrors AdminMvcController's own gates exactly), never just
                 "Marketing Manager" or any other designation check. Default landing page is
                 Users for a full CEO_OWNER, or Publishing Catalogue for anyone who only holds the
                 delegated catalogue-management grant (Users/Business Roles/Permissions would just
                 redirect them straight back out). --%>
            <c:if test="${canSeeAdministration}">
                <a class="${currentPath == ctx.concat('/app/admin/users') or currentPath == ctx.concat('/app/admin/business-roles')
                            or currentPath == ctx.concat('/app/admin/permissions') or currentPath == ctx.concat('/app/admin/catalogue')
                            or fn:startsWith(currentPath, ctx.concat('/app/admin/users/')) ? 'active' : ''}"
                   href="${accessClass == 'CEO_OWNER' ? ctx.concat('/app/admin/users') : ctx.concat('/app/admin/catalogue')}">Administration</a>
            </c:if>
        </c:otherwise>
    </c:choose>
    </nav>
    <div class="app-header-actions">
        <%-- Notification bell: real data, server-rendered on every page load (MvcNavigationAdvice's
             unreadNotificationCount/latestNotifications - same per-request-attribute pattern as
             currentUserFullName above), so opening the dropdown needs no extra request. Only
             meaningful workflow events reach here (assignment/reassignment/review/reschedule/
             cancel/completion) - never raw activity/UI logging - see NotificationService's own
             class javadoc. --%>
        <div class="app-header-notification">
            <button type="button" class="app-header-notification-btn" id="headerNotificationTrigger"
                    aria-haspopup="true" aria-expanded="false">
                &#128276;
                <c:if test="${unreadNotificationCount > 0}">
                    <span class="app-header-notification-badge">${unreadNotificationCount > 99 ? '99+' : unreadNotificationCount}</span>
                </c:if>
            </button>
            <div class="app-header-notification-menu hidden" id="headerNotificationMenu" role="menu">
                <div class="app-header-notification-menu-header">
                    <span>Notifications</span>
                    <c:if test="${unreadNotificationCount > 0}">
                        <button type="button" class="app-header-notification-mark-all"
                                id="headerNotificationMarkAllRead" data-url="${ctx}/app/notifications/mark-all-read">Mark all as read</button>
                    </c:if>
                </div>
                <div class="app-header-notification-list">
                    <c:forEach var="n" items="${latestNotifications}">
                        <%-- targetTab (e.g. a comment's own stage) lands click-through directly on that
                             section instead of always the generic Overview tab - null/empty for every
                             other notification type, whose link is unchanged. Plain EL concatenation,
                             deliberately not <c:url>: that tag calls response.encodeURL() under the hood,
                             which can append ";jsessionid=..." to the href and break byte-identical-response
                             assumptions elsewhere (see MyPerformanceViewLinkTest) - this app never relies on
                             URL-based session tracking (cookie-only JWT auth), so it's never needed here. --%>
                        <c:choose>
                            <c:when test="${not empty n.targetTab}">
                                <c:set var="notifItemHref" value="${ctx}/app/deliverables/${n.contentPlan.id}?tab=${n.targetTab}"/>
                            </c:when>
                            <c:otherwise>
                                <c:set var="notifItemHref" value="${ctx}/app/deliverables/${n.contentPlan.id}"/>
                            </c:otherwise>
                        </c:choose>
                        <a class="app-header-notification-item ${n.unread ? 'app-header-notification-item-unread' : ''}"
                           href="${notifItemHref}" data-mark-read-url="${ctx}/app/notifications/${n.id}/read"
                           data-unread="${n.unread}">
                            <span class="app-header-notification-dot" aria-hidden="true"></span>
                            <span class="app-header-notification-body">
                                <span class="app-header-notification-title"><c:out value="${n.title}"/></span>
                                <span class="app-header-notification-message"><c:out value="${n.message}"/></span>
                                <span class="app-header-notification-time" data-created-at="${n.createdAt}"></span>
                            </span>
                        </a>
                    </c:forEach>
                    <c:if test="${empty latestNotifications}">
                        <p class="app-header-notification-empty muted">No notifications yet.</p>
                    </c:if>
                </div>
                <a class="app-header-notification-view-all" href="${ctx}/app/notifications">View all notifications</a>
            </div>
        </div>
        <span class="app-header-divider"></span>
        <div class="app-header-profile">
            <button type="button" class="app-header-profile-trigger" id="headerProfileTrigger"
                    aria-haspopup="true" aria-expanded="false">
                <span class="app-header-avatar" data-fullname="${currentUserFullName}"></span>
                <span class="app-header-profile-text">
                    <span class="app-header-profile-name"><c:out value="${currentUserFullName}"/></span>
                    <c:if test="${not empty businessRoleName}">
                        <span class="app-header-profile-role"><c:out value="${businessRoleName}"/></span>
                    </c:if>
                </span>
                <span class="app-header-profile-chevron">&#9662;</span>
            </button>
            <div class="app-header-profile-menu hidden" id="headerProfileMenu" role="menu">
                <div class="app-header-profile-menu-identity">
                    <span class="app-header-avatar" data-fullname="${currentUserFullName}"></span>
                    <span class="app-header-profile-menu-details">
                        <span class="app-header-profile-menu-name"><c:out value="${currentUserFullName}"/></span>
                        <span class="app-header-profile-menu-email"><c:out value="${currentUserEmail}"/></span>
                    </span>
                </div>
                <div class="app-header-profile-menu-divider"></div>
                <form method="post" action="${ctx}/logout" class="logout-form">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <button type="submit" class="app-header-profile-menu-signout">&#8618; Sign out</button>
                </form>
            </div>
        </div>
    </div>
</header>
<script src="${ctx}/js/header-user-menu.js" defer></script>
<script src="${ctx}/js/header-notifications.js" defer></script>
