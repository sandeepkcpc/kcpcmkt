<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Permissions</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/pipeline">Pipeline</a>
</header>
<nav class="app-nav">
    <a href="${pageContext.request.contextPath}/app/admin/users">Users</a>
    <a href="${pageContext.request.contextPath}/app/admin/business-roles">Business Roles</a>
    <a href="${pageContext.request.contextPath}/app/admin/permissions" class="active">Permissions</a>
    <a href="${pageContext.request.contextPath}/app/admin/catalogue">Publishing Catalogue</a>
</nav>
<main class="app-main">
    <h1>Administration &raquo; Permissions</h1>
    <p class="muted">Every currently active Operational-Permission grant, across all users. To
        grant, modify, or revoke a permission, open that user's detail page under
        <a href="${pageContext.request.contextPath}/app/admin/users">Users</a> — this screen is a
        read-only consolidated view.</p>

    <table class="data-table">
        <thead>
            <tr><th>User</th><th>Email</th><th>#</th><th>Permission</th><th>Scope</th><th>Detail</th>
                <th>Granted By</th><th>Effective From</th><th>Expires</th></tr>
        </thead>
        <tbody>
        <c:forEach var="g" items="${grants}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/app/admin/users/${g.granteeUserId}">${g.granteeName}</a></td>
                <td>${g.granteeEmail}</td>
                <td>${g.permission.number()}</td>
                <td>${g.permission}</td>
                <td>${g.scopeType}</td>
                <td>${g.scopeDetail}</td>
                <td>${g.grantorName}</td>
                <td>${g.effectiveFrom}</td>
                <td><c:out value="${g.effectiveUntil}" default="(none)"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty grants}"><tr><td colspan="9" class="muted">No active permission grants.</td></tr></c:if>
        </tbody>
    </table>
</main>
</body>
</html>
