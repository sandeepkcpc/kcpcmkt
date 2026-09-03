<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Submit Idea</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Submit an Idea</h1>
    <p class="muted">Suggest a new content idea for review.</p>

    <c:set var="ideaSubmitFormAjax" value="${false}"/>
    <%@ include file="fragments/idea-submit-form.jspf" %>

    <p class="idea-submit-footer-hint">&#9432; Your idea will be visible in My Ideas after submission.</p>
</main>
<script src="${pageContext.request.contextPath}/js/idea-submit.js"></script>
</body>
</html>
