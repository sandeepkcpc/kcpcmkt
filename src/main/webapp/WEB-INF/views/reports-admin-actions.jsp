<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administrative Actions</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/pipeline">Pipeline</a>
</header>
<main class="app-main">
    <h1>Administrative Actions Report (Perm #16)</h1>
    <p class="muted">Read-only management summary derived from immutable audit data.</p>
    <table class="data-table">
        <thead><tr><th>Action Type</th><th>Count</th><th>Reasons (sample)</th></tr></thead>
        <tbody>
        <c:forEach var="e" items="${report}">
            <tr>
                <td>${e.key}</td>
                <td>${e.value.count}</td>
                <td>
                    <c:forEach var="r" items="${e.value.reasons}" varStatus="s">
                        ${r}<c:if test="${!s.last}">; </c:if>
                    </c:forEach>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</main>
</body>
</html>
