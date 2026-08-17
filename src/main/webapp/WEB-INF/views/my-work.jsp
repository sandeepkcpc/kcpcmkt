<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — My Work</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>My Work</h1>
    <p class="muted">${user.fullName} &middot; ${accessClass}</p>

    <h2>Assigned Tasks</h2>
    <table class="data-table">
        <thead><tr><th>Content ID</th><th>Role</th><th>Status</th><th>Shoot Date</th><th>Edit Date</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="t" items="${shootTasks}">
            <tr>
                <td>${t.contentPlan.contentId}</td>
                <td>Cameraperson</td>
                <td><span class="status-badge">${t.contentPlan.workflowInstance.currentStatusCode.statusName}</span></td>
                <td>${t.contentPlan.plannedShootDate}</td>
                <td>${t.contentPlan.plannedEditDate}</td>
                <td><a href="${pageContext.request.contextPath}/app/deliverables/${t.contentPlan.id}">Open</a></td>
            </tr>
        </c:forEach>
        <c:forEach var="t" items="${editTasks}">
            <tr>
                <td>${t.contentPlan.contentId}</td>
                <td>Editor</td>
                <td><span class="status-badge">${t.contentPlan.workflowInstance.currentStatusCode.statusName}</span></td>
                <td>${t.contentPlan.plannedShootDate}</td>
                <td>${t.contentPlan.plannedEditDate}</td>
                <td><a href="${pageContext.request.contextPath}/app/deliverables/${t.contentPlan.id}">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty shootTasks and empty editTasks}">
            <tr><td colspan="6" class="muted">No assigned work.</td></tr>
        </c:if>
        </tbody>
    </table>

    <h2>My Ideas</h2>
    <table class="data-table">
        <thead><tr><th>Idea ID</th><th>Title</th><th>Status</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="idea" items="${myIdeas}">
            <tr>
                <td>${idea.businessIdeaCode}</td>
                <td>${idea.title}</td>
                <td><span class="status-badge">${idea.workflowInstance.currentStatusCode.statusName}</span></td>
                <td><a href="${pageContext.request.contextPath}/app/ideas/${idea.id}">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty myIdeas}"><tr><td colspan="4" class="muted">No submissions yet.</td></tr></c:if>
        </tbody>
    </table>

    <h2>My Review Feedback</h2>
    <table class="data-table">
        <thead><tr><th>Gate</th><th>Decision</th><th>Reason</th><th>Decided (IST)</th></tr></thead>
        <tbody>
        <c:forEach var="rc" items="${myReviewFeedback}">
            <tr>
                <td>${rc.gateType}</td>
                <td>${rc.decision}</td>
                <td>${rc.decisionReason}</td>
                <td>${kcpc:ist(rc.decidedAt)}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty myReviewFeedback}"><tr><td colspan="4" class="muted">No review feedback yet.</td></tr></c:if>
        </tbody>
    </table>

    <h2>My Marks</h2>
    <table class="data-table">
        <thead><tr><th>Content ID</th><th>Role</th><th>Mark</th><th>Attributed (IST)</th></tr></thead>
        <tbody>
        <c:forEach var="m" items="${myMarks}">
            <tr>
                <td>${m.contentPlan.contentId}</td>
                <td>${m.roleType}</td>
                <td>${m.attributedMarkValue}</td>
                <td>${kcpc:ist(m.attributedAt)}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty myMarks}"><tr><td colspan="4" class="muted">No marks attributed yet.</td></tr></c:if>
        </tbody>
    </table>
</main>
</body>
</html>
