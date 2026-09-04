<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Team ▸ Performance</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <link rel="icon" type="image/x-icon" href="${pageContext.request.contextPath}/images/favicon.ico">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <div class="page-header-row">
        <div>
            <h1>Team</h1>
        </div>
        <div class="team-workload-asof">
            <span class="asof-icon" aria-hidden="true">&#9432;</span>
            Data as of <c:out value="${kcpc:ist(data.generatedAt)}"/>
        </div>
    </div>

    <div class="team-workload-tabs team-kpi-tabs">
        <c:if test="${canViewTeamWorkload}">
            <a class="team-workload-tab" href="${pageContext.request.contextPath}/app/reports/workload">Workload</a>
        </c:if>
        <a class="team-workload-tab active" href="${pageContext.request.contextPath}/app/reports/team-kpis">Performance</a>
    </div>

    <div class="team-perf-kpi-cards">
        <div class="team-perf-kpi-card perf-completed">
            <div class="team-perf-kpi-title">Completed</div>
            <div class="team-perf-kpi-icon">&#10003;</div>
            <div class="team-perf-kpi-value">${empty data.completed ? '-' : data.completed}</div>
            <div class="team-perf-kpi-subtitle">Tasks completed</div>
        </div>
        <div class="team-perf-kpi-card perf-inprogress">
            <div class="team-perf-kpi-title">In Progress</div>
            <div class="team-perf-kpi-icon">&#8987;</div>
            <div class="team-perf-kpi-value">${empty data.inProgress ? '-' : data.inProgress}</div>
            <div class="team-perf-kpi-subtitle">Tasks in progress</div>
        </div>
        <div class="team-perf-kpi-card perf-rate">
            <div class="team-perf-kpi-title">Completion Rate</div>
            <div class="team-perf-kpi-icon">&#128200;</div>
            <div class="team-perf-kpi-value">${empty data.completionRate ? '-' : data.completionRate}</div>
            <div class="team-perf-kpi-subtitle">Completed / Total</div>
        </div>
        <div class="team-perf-kpi-card perf-delay">
            <div class="team-perf-kpi-title">Delay Total</div>
            <div class="team-perf-kpi-icon">&#9888;</div>
            <div class="team-perf-kpi-value">${empty data.delayTotal ? '-' : data.delayTotal}</div>
            <div class="team-perf-kpi-subtitle">Delayed tasks</div>
        </div>
        <div class="team-perf-kpi-card perf-ontime">
            <div class="team-perf-kpi-title">On-Time %</div>
            <div class="team-perf-kpi-icon">&#128337;</div>
            <div class="team-perf-kpi-value">${empty data.onTimeRate ? '-' : data.onTimeRate}</div>
            <div class="team-perf-kpi-subtitle">Tasks completed on time</div>
        </div>
    </div>

    <div class="note-box">
        These metrics represent department-level aggregates only.<br>
        Individual team member performance is not displayed here.
    </div>
</main>
</body>
</html>
