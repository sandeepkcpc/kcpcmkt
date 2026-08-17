<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Team Workload</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Team Workload (Perm #14)</h1>
    <div class="panel">
        <h2>Active Tasks by Stage</h2>
        <table class="data-table">
            <thead><tr><th>Stage</th><th>Active Tasks</th></tr></thead>
            <tbody>
            <c:forEach var="e" items="${data.activeTasksByStage}">
                <tr><td>${e.key}</td><td>${e.value}</td></tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
    <div class="panel">
        <h2>Assignee Load (active counts, no ratings)</h2>
        <table class="data-table">
            <thead><tr><th>Assignee</th><th>Active Tasks</th></tr></thead>
            <tbody>
            <c:forEach var="e" items="${data.assigneeActiveLoad}">
                <tr><td>${e.key}</td><td>${e.value}</td></tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
    <p class="note-box">Delayed deliverables in scope: ${data.delayedCount}. Counts only — no performance scores, Marks, or rankings.</p>
</main>
</body>
</html>
