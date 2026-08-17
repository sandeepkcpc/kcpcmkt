<%@ page contentType="text/html;charset=UTF-8" language="java" %>
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
    <a class="${currentPath == ctx.concat('/app/pipeline') ? 'active' : ''}" href="${ctx}/app/pipeline">Content Pipeline</a>
    <a class="${currentPath == ctx.concat('/app/my-work') ? 'active' : ''}" href="${ctx}/app/my-work">My Work</a>
    <a class="${currentPath == ctx.concat('/app/ideas') ? 'active' : ''}" href="${ctx}/app/ideas">Idea Queue</a>
    <a class="${currentPath == ctx.concat('/app/ideas/new') ? 'active' : ''}" href="${ctx}/app/ideas/new">Submit Idea</a>
    <a class="${currentPath == ctx.concat('/app/reports/workload') ? 'active' : ''}" href="${ctx}/app/reports/workload">Team Workload</a>
    <a class="${currentPath == ctx.concat('/app/reports/team-kpis') ? 'active' : ''}" href="${ctx}/app/reports/team-kpis">Team KPI</a>
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
</nav>
