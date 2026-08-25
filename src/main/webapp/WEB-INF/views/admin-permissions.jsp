<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Permissions</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="page-header-row">
        <div>
            <h1>Permissions</h1>
        </div>
        <span class="muted" style="font-size:0.82rem;white-space:nowrap;">&#8505; All dates &amp; times shown in IST</span>
    </div>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <h2>All Active Operational Permissions <span class="muted" style="font-weight:400;font-size:0.8rem;">(Read-only)</span></h2>
        <table class="data-table admin-table">
            <thead>
                <tr><th>User</th><th>Email</th><th>Permission</th><th>What it means</th><th>Scope</th><th>Granted By</th>
                    <th>Effective From (IST)</th><th>Expires (IST)</th></tr>
            </thead>
            <tbody>
            <c:forEach var="g" items="${grants}">
                <tr>
                    <td><a href="${pageContext.request.contextPath}/app/admin/users/${g.granteeUserId}">${g.granteeName}</a></td>
                    <td class="admin-email-cell muted">${g.granteeEmail}</td>
                    <td>${g.permission}</td>
                    <td class="muted" style="font-size:0.8rem;max-width:22rem;"><c:out value="${permissionDescriptions[g.permission]}"/></td>
                    <td title="${g.scopeDetail}">${g.scopeType}</td>
                    <td>${g.grantorName}</td>
                    <td>${kcpc:ist(g.effectiveFrom)}</td>
                    <td>${empty g.effectiveUntil ? '-' : kcpc:ist(g.effectiveUntil)}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty grants}"><tr><td colspan="8" class="muted">No active permission grants.</td></tr></c:if>
            </tbody>
        </table>
    </div>
</main>
</body>
</html>
