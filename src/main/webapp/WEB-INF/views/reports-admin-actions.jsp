<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administrative Actions</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide reports-page">
    <%@ include file="fragments/reports-tabs.jspf" %>
    <div id="reportsAdminActionsDynamicRegion">
        <%@ include file="fragments/reports-admin-actions-content.jspf" %>
    </div>
</main>
<%-- Loaded BEFORE reports-workspace.js so this file's 'submit' listener (Date Range validation)
     registers first on the same region/event - same script-order reasoning as
     reports-kpi-date-preset.js before reports-workspace.js on the KPI Dashboard page. --%>
<script src="<c:url value='/js/reports-admin-actions.js'/>" defer></script>
<script src="<c:url value='/js/reports-workspace.js'/>" defer></script>
</body>
</html>
