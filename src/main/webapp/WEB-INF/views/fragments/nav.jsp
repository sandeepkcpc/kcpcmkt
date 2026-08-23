<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="currentPath" value="${empty requestScope['jakarta.servlet.forward.request_uri'] ? pageContext.request.requestURI : requestScope['jakarta.servlet.forward.request_uri']}" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <form method="post" action="${ctx}/logout" class="logout-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <button type="submit" class="link-button">Sign out</button>
    </form>
</header>
<nav class="app-nav">
    <%-- ENG-057: EMPLOYEE-class users (Cameraperson/Editor/Publisher/Model etc.) get a minimal
         3-tab nav - the full company-wide reporting/admin nav below is CEO/MM-only territory
         regardless of any individual permission grant an Employee might separately hold, matching
         the "no management/admin controls for employees" rule the My Work redesign asked for. --%>
    <c:choose>
        <c:when test="${accessClass == 'EMPLOYEE'}">
            <%-- Centralized workflow-participation rule (AuthorizationService
                 #isNonProductionEmployee, exposed here as ${nonProductionEmployee} from
                 MvcNavigationAdvice - never a role-name/designation check, never based on
                 permission grants): an EMPLOYEE whose Business Role does not participate in the
                 Content Production workflow gets only My Ideas/Submit Idea below - no My Work/My
                 Shoots, no workflow/management nav at all. Enforced the same way server-side for
                 direct URLs by WorkflowParticipationInterceptor. --%>
            <c:if test="${!nonProductionEmployee}">
                <%-- ENG-067: Model employees get "My Shoots" (their own dedicated, read-only
                     shoot-participation screen) instead of "My Work" - Models hold no execution
                     assignment/permission on any stage, so the task-execution "My Work" concept
                     doesn't apply to them. --%>
                <c:choose>
                    <c:when test="${businessRoleName == 'Model'}">
                        <a class="${currentPath == ctx.concat('/app/my-shoots') ? 'active' : ''}" href="${ctx}/app/my-shoots">My Shoots</a>
                    </c:when>
                    <c:otherwise>
                        <a class="${currentPath == ctx.concat('/app/my-work') ? 'active' : ''}" href="${ctx}/app/my-work">My Work</a>
                    </c:otherwise>
                </c:choose>
            </c:if>
            <%-- ENG-059: /app/ideas now serves a dedicated own-ideas-only "My Ideas" page for
                 EMPLOYEE-class users (same route as the CEO/MM Idea Queue, branched server-side) -
                 no longer the my-work.jsp#my-ideas anchor ENG-057 used as a stopgap. --%>
            <a class="${currentPath == ctx.concat('/app/ideas') ? 'active' : ''}" href="${ctx}/app/ideas">My Ideas</a>
            <a class="${currentPath == ctx.concat('/app/ideas/new') ? 'active' : ''}" href="${ctx}/app/ideas/new">Submit Idea</a>
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
