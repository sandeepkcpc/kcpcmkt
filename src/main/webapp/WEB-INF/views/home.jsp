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
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Welcome, ${user.fullName}</h1>
    <p>Business Role: ${user.businessRole.roleName} &middot; Access Class: ${accessClass}</p>
    <p class="muted">Further stage dashboards (Planning, Shooting, Editing, Publishing, Performance) are added in subsequent build phases.</p>
</main>
</body>
</html>
