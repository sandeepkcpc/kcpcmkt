<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Team Workload</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <link rel="icon" type="image/x-icon" href="${pageContext.request.contextPath}/images/favicon.ico">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%-- ENG-087: everything inside this div is what team-workload-dashboard.js replaces on every
         AJAX filter interaction - see fragments/team-workload-content.jspf. --%>
    <div id="teamWorkloadDynamicRegion">
        <%@ include file="fragments/team-workload-content.jspf" %>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/team-workload-dashboard.js" defer></script>
</body>
</html>
