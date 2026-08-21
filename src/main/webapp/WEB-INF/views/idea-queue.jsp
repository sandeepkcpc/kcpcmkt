<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Idea Queue</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <c:if test="${not empty errorMessage}">
        <div class="alert alert-error">${errorMessage}</div>
    </c:if>
    <%-- ENG-088: everything inside this div is what idea-queue-dashboard.js replaces on every AJAX
         filter/sort/pagination interaction - see fragments/idea-queue-content.jspf. --%>
    <div id="ideaQueueDynamicRegion">
        <%@ include file="fragments/idea-queue-content.jspf" %>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/idea-queue-dashboard.js" defer></script>
</body>
</html>
