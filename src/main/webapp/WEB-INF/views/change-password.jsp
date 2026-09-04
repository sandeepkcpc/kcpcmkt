<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Change Password</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <link rel="icon" type="image/x-icon" href="${pageContext.request.contextPath}/images/favicon.ico">
</head>
<body class="auth-page">
<main class="auth-card">
    <h1>KCPC Bandhani</h1>
    <p class="subtitle">Change your password to continue</p>
    <div class="alert-info">A temporary password was used to sign in. Please set your own password before continuing.</div>
    <c:if test="${not empty errorMessage}">
        <div class="alert-error">${errorMessage}</div>
    </c:if>
    <form method="post" action="${pageContext.request.contextPath}/app/change-password">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <label for="newPassword">New Password</label>
        <input type="password" id="newPassword" name="newPassword" required autofocus>
        <label for="confirmPassword">Confirm Password</label>
        <input type="password" id="confirmPassword" name="confirmPassword" required>
        <button type="submit">Set New Password</button>
    </form>
</main>
</body>
</html>
