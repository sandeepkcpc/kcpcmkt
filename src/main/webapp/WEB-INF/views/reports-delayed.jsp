<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Delayed Deliverables</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide reports-page">
    <%@ include file="fragments/reports-tabs.jspf" %>
    <div id="reportsDelayedDynamicRegion">
        <%@ include file="fragments/reports-delayed-content.jspf" %>
    </div>
</main>
<script src="<c:url value='/js/reports-workspace.js'/>" defer></script>
</body>
</html>
