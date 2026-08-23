<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Reviews</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide reviews-page" id="reviewsPage">
    <h1>Reviews</h1>
    <p class="muted">Attention queues for ideas and workflow reviews.</p>

    <%-- ENG: the tab bar lives INSIDE the AJAX-swapped region (like pipeline.jsp's stage tabs) so
         its count badges/active-state stay live across tab switches and after a decision changes
         a pending count, without a second AJAX call. --%>
    <div id="reviewsDynamicRegion">
        <%@ include file="fragments/reviews-content.jspf" %>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/stage-discussion.js" defer></script>
<script src="${pageContext.request.contextPath}/js/reviews-workspace.js" defer></script>
</body>
</html>
