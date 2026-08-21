<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Team KPIs</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Team KPIs (Perm #15)</h1>
    <div class="kpi-grid">
        <div class="kpi-tile"><div class="kpi-value">${data.completed}</div><div class="kpi-label">Completed</div></div>
        <div class="kpi-tile"><div class="kpi-value">${data.inProgress}</div><div class="kpi-label">In Progress</div></div>
        <div class="kpi-tile"><div class="kpi-value">${data.completionRate}</div><div class="kpi-label">Completion Rate</div></div>
        <div class="kpi-tile"><div class="kpi-value">${data.delayTotal}</div><div class="kpi-label">Delay Total</div></div>
        <div class="kpi-tile"><div class="kpi-value">${data.onTimeRate}</div><div class="kpi-label">On-Time %</div></div>
    </div>
    <p class="note-box">Department-level aggregates only. No individual breakdowns.</p>
</main>
</body>
</html>
