<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/ideas">Idea Queue</a>
    <a class="header-link" href="${pageContext.request.contextPath}/app/ideas/new">Submit Idea</a>
    <form method="post" action="${pageContext.request.contextPath}/logout" class="logout-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <button type="submit" class="link-button">Sign out</button>
    </form>
</header>
<main class="app-main">
    <h1>Welcome, ${user.fullName}</h1>
    <p>Business Role: ${user.businessRole.roleName} &middot; Access Class: ${accessClass}</p>
    <p class="muted">Further stage dashboards (Planning, Shooting, Editing, Publishing, Performance) are added in subsequent build phases.</p>
</main>
</body>
</html>
