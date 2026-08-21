<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administrative Actions</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide reports-page">
    <%@ include file="fragments/reports-tabs.jspf" %>
    <div id="reportsAdminActionsDynamicRegion">
        <%@ include file="fragments/reports-admin-actions-content.jspf" %>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/reports-workspace.js" defer></script>
</body>
</html>
