<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Forgot Password</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body class="auth-page">
<main class="auth-card">
    <h1>KCPC Bandhani</h1>
    <p class="subtitle">Reset your password</p>
    <c:if test="${not empty errorMessage}">
        <div class="alert-error">${errorMessage}</div>
    </c:if>
    <c:choose>
        <c:when test="${not empty successMessage}">
            <%-- Same message shown whether or not the email actually matched an account - never
                 reveal account existence via a different success/error branch here. --%>
            <div class="alert-success">${successMessage}</div>
        </c:when>
        <c:otherwise>
            <p class="muted">Enter the email address associated with your account and we'll send you a link to reset your password.</p>
            <form method="post" action="${pageContext.request.contextPath}/forgot-password">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label for="email">Email</label>
                <input type="email" id="email" name="email" required autofocus placeholder="you@company.com">
                <button type="submit">Send Reset Link</button>
            </form>
        </c:otherwise>
    </c:choose>
    <a class="auth-back-link" href="${pageContext.request.contextPath}/login">&larr; Back to Sign In</a>
</main>
</body>
</html>
