<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Delayed Deliverables</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Delayed Deliverables</h1>
    <p class="note-box">DLY is a supplementary flag over the primary workflow status — it is not a distinct status.</p>
    <table class="data-table">
        <thead><tr><th>Content ID</th><th>Stage</th><th>Days Delayed</th><th>Priority</th><th>Planned Date</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="r" items="${rows}">
            <tr>
                <td>${r.contentId}</td>
                <td>${r.stage}</td>
                <td>${r.delayDays}</td>
                <td>${r.priority}</td>
                <td>${r.plannedDate}</td>
                <td><a href="${pageContext.request.contextPath}/app/deliverables/${r.contentPlanId}">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty rows}"><tr><td colspan="6" class="muted">No delayed deliverables in this period.</td></tr></c:if>
        </tbody>
    </table>
</main>
</body>
</html>
