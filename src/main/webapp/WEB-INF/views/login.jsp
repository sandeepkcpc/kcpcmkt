<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Sign In</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body class="auth-page">
<main class="auth-card">
    <h1>KCPC Bandhani</h1>
    <p class="subtitle">Content Production Lifecycle</p>
    <c:if test="${not empty errorMessage}">
        <div class="alert alert-error">${errorMessage}</div>
    </c:if>
    <form method="post" action="${pageContext.request.contextPath}/login">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <label for="email">Email</label>
        <input type="email" id="email" name="email" required autofocus>
        <label for="password">Password</label>
        <input type="password" id="password" name="password" required>
        <div class="auth-forgot-row">
            <a class="auth-forgot-link" href="${pageContext.request.contextPath}/forgot-password">Forgot Password?</a>
        </div>
        <button type="submit">Sign In</button>
    </form>
</main>
</body>
</html>
