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
    <%-- ENG: the page header lives INSIDE the AJAX-swapped region too (same reasoning as the tab
         bar below it) - the Ideas tab's header text ("Idea Review & Planning") needs to correctly
         re-appear on an AJAX tab switch back to Ideas, not just on a full page load. --%>
    <div id="reviewsDynamicRegion">
        <%@ include file="fragments/reviews-content.jspf" %>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/stage-discussion.js" defer></script>
<script src="${pageContext.request.contextPath}/js/model-picker.js" defer></script>
<script src="${pageContext.request.contextPath}/js/publication-scope.js" defer></script>
<script src="${pageContext.request.contextPath}/js/script-description-modal.js" defer></script>
<script src="${pageContext.request.contextPath}/js/reviews-workspace.js" defer></script>
</body>
</html>
