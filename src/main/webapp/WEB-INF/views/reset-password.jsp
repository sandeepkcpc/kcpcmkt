<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Reset Password</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body class="auth-page">
<main class="auth-card">
    <h1>KCPC Bandhani</h1>
    <p class="subtitle">Set a new password</p>
    <c:if test="${not empty errorMessage}">
        <div class="alert-error">${errorMessage}</div>
    </c:if>
    <c:choose>
        <c:when test="${resetComplete}">
            <div class="alert-success">${successMessage}</div>
            <a class="auth-back-link" href="${pageContext.request.contextPath}/login">Continue to Sign In &rarr;</a>
        </c:when>
        <c:otherwise>
            <c:choose>
                <c:when test="${empty token}">
                    <div class="alert-error">This reset link is missing its token. Please request a new one.</div>
                </c:when>
                <c:otherwise>
                    <form method="post" action="${pageContext.request.contextPath}/reset-password">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <input type="hidden" name="token" value="${fn:escapeXml(token)}"/>
                        <label for="newPassword">New Password</label>
                        <input type="password" id="newPassword" name="newPassword" required autofocus>
                        <label for="confirmPassword">Confirm Password</label>
                        <input type="password" id="confirmPassword" name="confirmPassword" required>
                        <button type="submit">Reset Password</button>
                    </form>
                </c:otherwise>
            </c:choose>
            <a class="auth-back-link" href="${pageContext.request.contextPath}/forgot-password">Request a new reset link</a>
        </c:otherwise>
    </c:choose>
</main>
</body>
</html>
