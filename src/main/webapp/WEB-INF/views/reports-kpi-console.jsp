<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — KPI Dashboard</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide reports-page">
    <%@ include file="fragments/reports-tabs.jspf" %>
    <div id="reportsKpiDynamicRegion">
        <%@ include file="fragments/reports-kpi-console-content.jspf" %>
    </div>
</main>
<%-- Must load (and therefore register its delegated 'change'/'submit' listeners on
     #reportsKpiDynamicRegion) BEFORE reports-workspace.js: both listen for the same bubbled events
     on the same region, and DOM listeners for one event type fire in registration order - this
     one has to compute/validate the Date Range fields first so reports-workspace.js's own
     auto-submit-on-select-change (unchanged) always reads the freshly-calculated values, and so an
     invalid range can stopImmediatePropagation() before that handler ever runs. --%>
<script src="<c:url value='/js/reports-kpi-date-preset.js'/>" defer></script>
<script src="<c:url value='/js/reports-workspace.js'/>" defer></script>
<script src="<c:url value='/js/reports-kpi-ownership-drilldown.js'/>" defer></script>
<script src="<c:url value='/js/reports-kpi-calendar.js'/>" defer></script>
</body>
</html>
