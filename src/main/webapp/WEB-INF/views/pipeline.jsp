<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Content Pipeline</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%-- ENG-081: everything inside this div is what pipeline-dashboard.js replaces on every
         AJAX filter/sort/stage/pagination interaction - see fragments/pipeline-content.jspf. --%>
    <div id="pipelineDynamicRegion">
        <%@ include file="fragments/pipeline-content.jspf" %>
    </div>
</main>
<script src="<c:url value='/js/platform-chip-popover.js'/>" defer></script>
<script src="<c:url value='/js/pipeline-dashboard.js'/>" defer></script>
</body>
</html>
