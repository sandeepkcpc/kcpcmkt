<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Completed Task Details</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <div class="breadcrumb"><a href="${pageContext.request.contextPath}/app/my-work">My Work</a> / Completed Task Details</div>

    <%-- Read-only snapshot of THIS employee's own completed assignment, stage-scoped only - never
         the shared /app/deliverables/{id} page, which has no ownership/stage gate and would expose
         next-stage data once the plan has moved on. See LandingMvcController#shootHistoryDetail/
         editHistoryDetail/publishHistoryDetail - every field here was assembled by one of those
         three, each of which only ever queries its own stage's data. --%>
    <div class="page-header-row">
        <div>
            <h1>Completed Task Details</h1>
            <p class="muted">
                <c:choose>
                    <c:when test="${stage == 'SHOOT'}">Your Shoot assignment on this Content ID - a read-only record.</c:when>
                    <c:when test="${stage == 'EDIT'}">Your Edit assignment on this Content ID - a read-only record.</c:when>
                    <c:otherwise>Your Publishing assignment on this Content ID - a read-only record.</c:otherwise>
                </c:choose>
            </p>
        </div>
    </div>

    <div class="panel">
        <h2><c:out value="${summary.contentId}"/></h2>
        <p class="muted"><c:out value="${summary.title}"/></p>

        <table class="data-table">
            <tbody>
            <tr>
                <th style="width:220px">
                    <c:choose>
                        <c:when test="${stage == 'SHOOT'}">Camera Person</c:when>
                        <c:when test="${stage == 'EDIT'}">Video Editor</c:when>
                        <c:otherwise>Publisher</c:otherwise>
                    </c:choose>
                </th>
                <td><c:out value="${assigneeName}"/> <c:if test="${isLead}"><span class="lead-badge">Lead</span></c:if></td>
            </tr>
            <tr>
                <th>Assigned On</th>
                <td>${empty assignedAt ? '—' : kcpc:ist(assignedAt)}</td>
            </tr>
            <tr>
                <th>
                    <c:choose>
                        <c:when test="${stage == 'SHOOT'}">Planned Shoot Date</c:when>
                        <c:when test="${stage == 'EDIT'}">Planned Edit Date</c:when>
                        <c:otherwise>Planned Live Date</c:otherwise>
                    </c:choose>
                </th>
                <td>${empty plannedDate ? '—' : plannedDate}</td>
            </tr>
            <tr>
                <th>
                    <c:choose>
                        <c:when test="${stage == 'SHOOT'}">Shoot Instructions</c:when>
                        <c:when test="${stage == 'EDIT'}">Edit Instructions</c:when>
                        <c:otherwise>Publishing Instructions</c:otherwise>
                    </c:choose>
                </th>
                <td class="${empty stageDescription ? 'muted' : ''}">${empty stageDescription ? 'No instructions were given.' : stageDescription}</td>
            </tr>
            <tr>
                <th>Drive Link</th>
                <td>
                    <c:choose>
                        <c:when test="${not empty folderLink}">
                            <a href="${folderLink}" target="_blank" rel="noopener noreferrer">Open &#8599;</a>
                        </c:when>
                        <c:otherwise><span class="muted">—</span></c:otherwise>
                    </c:choose>
                </td>
            </tr>
            </tbody>
        </table>
    </div>

    <div class="panel">
        <h2>Completion</h2>
        <table class="data-table">
            <tbody>
            <tr>
                <th style="width:220px">Completed On</th>
                <td>${empty summary.completedOn ? '—' : kcpc:ist(summary.completedOn)}</td>
            </tr>
            <c:if test="${stage != 'PUBLISH'}">
                <tr>
                    <th>Result</th>
                    <td>
                        <c:choose>
                            <c:when test="${not empty summary.finalResult}">
                                <span class="status-pill ${summary.finalResult == 'Approved' ? 'status-completed' : 'status-needschanges'}">
                                    <c:out value="${fn:toUpperCase(summary.finalResult)}"/>
                                </span>
                            </c:when>
                            <c:otherwise><span class="muted">—</span></c:otherwise>
                        </c:choose>
                    </td>
                </tr>
                <tr>
                    <th>Remarks</th>
                    <td class="${empty summary.remarks ? 'muted' : ''}">${empty summary.remarks ? '—' : summary.remarks}</td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>

    <p><a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work">&larr; Back to My Work</a></p>
</main>
</body>
</html>
