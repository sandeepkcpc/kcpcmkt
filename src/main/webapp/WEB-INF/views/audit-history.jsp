<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Audit History</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/pipeline">Pipeline</a>
</header>
<main class="app-main">
    <h1>Audit History (Perm #16)</h1>
    <p class="note-box">Read-only. Audit records are immutable — no edit/delete affordance exists anywhere in this system.</p>
    <form class="filter-bar" method="get" action="${pageContext.request.contextPath}/app/audit">
        Action Type <input type="text" name="actionType" value="${param.actionType}">
        From <input type="date" name="startDate" value="${param.startDate}">
        To <input type="date" name="endDate" value="${param.endDate}">
        <button type="submit">Filter</button>
    </form>
    <table class="data-table">
        <thead><tr><th>Timestamp (UTC)</th><th>Actor</th><th>Event Type</th><th>Object</th><th>Reason</th></tr></thead>
        <tbody>
        <c:forEach var="log" items="${logs}">
            <tr>
                <td>${log.eventTimestamp}</td>
                <td>${log.actorFullName}</td>
                <td>${log.eventType}</td>
                <td>${log.targetEntityName}</td>
                <td>${log.actionReason}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty logs}"><tr><td colspan="5" class="muted">No audit entries match this filter.</td></tr></c:if>
        </tbody>
    </table>
</main>
</body>
</html>
