<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Submit Idea</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/home">Home</a>
</header>
<main class="app-main">
    <h1>Submit an Idea</h1>
    <c:if test="${not empty errorMessage}">
        <div class="alert alert-error">${errorMessage}</div>
    </c:if>
    <form method="post" action="${pageContext.request.contextPath}/app/ideas" class="form-card">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <label for="title">Idea Title *</label>
        <input type="text" id="title" name="title" required maxlength="200">
        <label for="referenceLink">Reference Link / Note</label>
        <input type="text" id="referenceLink" name="referenceLink">
        <label for="notesRemarks">Remarks</label>
        <textarea id="notesRemarks" name="notesRemarks" rows="5"></textarea>
        <button type="submit">Submit Idea</button>
    </form>
</main>
</body>
</html>
