<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Content Pipeline</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <form method="post" action="${pageContext.request.contextPath}/logout" class="logout-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <button type="submit" class="link-button">Sign out</button>
    </form>
</header>
<nav class="app-nav">
    <a class="active" href="${pageContext.request.contextPath}/app/pipeline">Content Pipeline</a>
    <a href="${pageContext.request.contextPath}/app/ideas">Idea Queue</a>
    <a href="${pageContext.request.contextPath}/app/ideas/new">Submit Idea</a>
    <a href="${pageContext.request.contextPath}/app/reports/workload">Team Workload</a>
    <a href="${pageContext.request.contextPath}/app/reports/team-kpis">Team KPI</a>
    <a href="${pageContext.request.contextPath}/app/reports/kpis">30-KPI Console</a>
    <a href="${pageContext.request.contextPath}/app/reports/delayed">Delayed Deliverables</a>
    <a href="${pageContext.request.contextPath}/app/reports/admin-actions">Admin Actions</a>
    <a href="${pageContext.request.contextPath}/app/audit">Audit History</a>
    <a href="${pageContext.request.contextPath}/app/export">Export</a>
    <c:if test="${accessClass == 'CEO_OWNER'}">
        <a href="${pageContext.request.contextPath}/app/admin/users">Users</a>
        <a href="${pageContext.request.contextPath}/app/admin/business-roles">Business Roles</a>
        <a href="${pageContext.request.contextPath}/app/admin/permissions">Permissions</a>
        <a href="${pageContext.request.contextPath}/app/admin/catalogue">Publishing Catalogue</a>
    </c:if>
</nav>
<main class="app-main">
    <h1>Content Pipeline</h1>
    <p class="muted">${user.fullName} &middot; ${accessClass}</p>
    <table class="data-table">
        <thead>
        <tr><th>Content ID</th><th>Title</th><th>Status</th><th>Priority</th><th>Flags</th><th>Live Date</th><th></th></tr>
        </thead>
        <tbody>
        <c:forEach var="p" items="${plans}">
            <tr>
                <td>${p.contentId}</td>
                <td>${p.idea.title}</td>
                <td><span class="status-badge">${p.workflowInstance.currentStatusCode.statusName}</span></td>
                <td>${p.contentPriority}</td>
                <td>
                    <c:if test="${not empty p.plannedLiveDate and p.plannedLiveDate lt today
                                  and p.workflowInstance.currentStatusCode != 'COMP'
                                  and p.workflowInstance.currentStatusCode != 'CAN'}">
                        <span class="flag-chip flag-delayed">Delayed</span>
                    </c:if>
                    <c:if test="${onHoldWorkflowInstanceIds.contains(p.workflowInstance.id)}">
                        <span class="flag-chip flag-hold">Hold</span>
                    </c:if>
                </td>
                <td>${p.plannedLiveDate}</td>
                <td><a href="${pageContext.request.contextPath}/app/deliverables/${p.id}">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty plans}"><tr><td colspan="7" class="muted">No deliverables yet.</td></tr></c:if>
        </tbody>
    </table>
</main>
</body>
</html>
