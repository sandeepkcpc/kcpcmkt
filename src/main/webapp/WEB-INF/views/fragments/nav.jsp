<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
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
            <%-- ENG-059: /app/ideas now serves a dedicated own-ideas-only "My Ideas" page for
                 EMPLOYEE-class users (same route as the CEO/MM Idea Queue, branched server-side) -
                 no longer the my-work.jsp#my-ideas anchor ENG-057 used as a stopgap. --%>
            <a class="${currentPath == ctx.concat('/app/ideas') ? 'active' : ''}" href="${ctx}/app/ideas">My Ideas</a>
            <a class="${currentPath == ctx.concat('/app/ideas/new') ? 'active' : ''}" href="${ctx}/app/ideas/new">Submit Idea</a>
        </c:when>
        <c:otherwise>
            <a class="${currentPath == ctx.concat('/app/pipeline') ? 'active' : ''}" href="${ctx}/app/pipeline">Content Pipeline</a>
            <a class="${currentPath == ctx.concat('/app/my-work') ? 'active' : ''}" href="${ctx}/app/my-work">My Work</a>
            <a class="${currentPath == ctx.concat('/app/ideas') ? 'active' : ''}" href="${ctx}/app/ideas">Idea Queue</a>
            <a class="${currentPath == ctx.concat('/app/ideas/new') ? 'active' : ''}" href="${ctx}/app/ideas/new">Submit Idea</a>
            <%-- ENG-087: "Team Workload"/"Team KPI" consolidated into one "Team" nav entry with
                 Workload/Performance tabs inside the page itself (reports-workload.jsp/
                 reports-team-kpis.jsp both render the same tab header) - both pages/routes/
                 permission gates (PERM_14/PERM_15) are unchanged, this is nav presentation only. --%>
            <a class="${currentPath == ctx.concat('/app/reports/workload') or currentPath == ctx.concat('/app/reports/team-kpis') ? 'active' : ''}"
               href="${ctx}/app/reports/workload">Team</a>
            <a class="${currentPath == ctx.concat('/app/reports/kpis') ? 'active' : ''}" href="${ctx}/app/reports/kpis">KPI Console</a>
            <a class="${currentPath == ctx.concat('/app/reports/delayed') ? 'active' : ''}" href="${ctx}/app/reports/delayed">Delayed Deliverables</a>
            <a class="${currentPath == ctx.concat('/app/reports/admin-actions') ? 'active' : ''}" href="${ctx}/app/reports/admin-actions">Admin Actions</a>
            <a class="${currentPath == ctx.concat('/app/audit') ? 'active' : ''}" href="${ctx}/app/audit">Logs</a>
            <a class="${currentPath == ctx.concat('/app/export') ? 'active' : ''}" href="${ctx}/app/export">Export</a>
            <c:if test="${accessClass == 'CEO_OWNER'}">
                <a class="${currentPath == ctx.concat('/app/admin/users') ? 'active' : ''}" href="${ctx}/app/admin/users">Users</a>
                <a class="${currentPath == ctx.concat('/app/admin/business-roles') ? 'active' : ''}" href="${ctx}/app/admin/business-roles">Business Roles</a>
                <a class="${currentPath == ctx.concat('/app/admin/permissions') ? 'active' : ''}" href="${ctx}/app/admin/permissions">Permissions</a>
                <a class="${currentPath == ctx.concat('/app/admin/catalogue') ? 'active' : ''}" href="${ctx}/app/admin/catalogue">Publishing Catalogue</a>
            </c:if>
        </c:otherwise>
    </c:choose>
</nav>
