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
        <thead><tr><th>Content ID</th><th>Role</th><th>Status</th><th>Description</th><th>Shoot Date</th><th>Edit Date</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="t" items="${shootTasks}">
            <tr>
                <td>${t.contentPlan.contentId}</td>
                <td>Cameraperson</td>
                <td><span class="status-badge">${t.contentPlan.workflowInstance.currentStatusCode.statusName}</span></td>
                <td class="my-work-description"><c:out value="${empty t.contentPlan.shootDescription ? '—' : t.contentPlan.shootDescription}"/></td>
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
                <td class="my-work-description"><c:out value="${empty t.contentPlan.editDescription ? '—' : t.contentPlan.editDescription}"/></td>
                <td>${t.contentPlan.plannedShootDate}</td>
                <td>${t.contentPlan.plannedEditDate}</td>
                <td><a href="${pageContext.request.contextPath}/app/deliverables/${t.contentPlan.id}">Open</a></td>
            </tr>
        </c:forEach>
        <c:forEach var="t" items="${publishTasks}">
            <tr>
                <td>${t.contentPlan.contentId}</td>
                <td>Publisher</td>
                <td><span class="status-badge">${t.contentPlan.workflowInstance.currentStatusCode.statusName}</span></td>
                <td class="my-work-description"><c:out value="${empty t.contentPlan.publishingDescription ? '—' : t.contentPlan.publishingDescription}"/></td>
                <td>${t.contentPlan.plannedShootDate}</td>
                <td>${t.contentPlan.plannedEditDate}</td>
                <td><a href="${pageContext.request.contextPath}/app/deliverables/${t.contentPlan.id}">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty shootTasks and empty editTasks and empty publishTasks}">
            <tr><td colspan="7" class="muted">No assigned work.</td></tr>
        </c:if>
        </tbody>
    </table>

    <h2>My Completed Work / History</h2>
    <p class="muted">Your own past involvement, once that stage has moved on - no next-stage operational detail.</p>
    <table class="data-table">
        <thead><tr><th>Content ID</th><th>Stage Worked</th><th>My Work Status</th><th>Completed On</th><th>Final Result</th></tr></thead>
        <tbody>
        <c:forEach var="w" items="${completedWork}">
            <tr>
                <td>${w.contentId}</td>
                <td>${w.stageWorked}</td>
                <td>Completed</td>
                <td><c:if test="${not empty w.completedOn}">${kcpc:ist(w.completedOn)}</c:if></td>
                <td>${w.finalResult}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty completedWork}">
            <tr><td colspan="5" class="muted">No completed work yet.</td></tr>
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
