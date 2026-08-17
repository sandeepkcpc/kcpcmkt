<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Content Pipeline</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Content Pipeline</h1>
    <p class="muted">${user.fullName} &middot; ${accessClass} &middot; one row per Content ID</p>
    <div class="pipeline-scroll">
        <table class="pipeline-table">
            <thead>
            <tr>
                <th class="pipeline-col-id">Content ID</th>
                <th>SKU</th>
                <th>Idea</th>
                <th>Reference Link / Note</th>
                <th>Category</th>
                <th>Channels</th>
                <th>Actor</th>
                <th>Camera Person</th>
                <th>Models</th>
                <th>Video Editor</th>
                <th>Drive Link</th>
                <th>Planned Live Date</th>
                <th>Shoot Date</th>
                <th>Edit Date</th>
                <th>Live Date</th>
                <th>Platforms</th>
                <th>Performance</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="row" items="${pipelineRows}">
                <tr>
                    <td class="pipeline-col-id">
                        <a href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}">${row.contentId}</a>
                    </td>
                    <td>${row.sku}</td>
                    <td class="pipeline-col-wrap" title="${row.ideaTitle}">${row.ideaTitle}</td>
                    <td class="pipeline-col-wrap">
                        <c:choose>
                            <c:when test="${row.referenceLinkIsUrl}">
                                <a href="${row.referenceLink}" target="_blank" rel="noopener noreferrer" title="${row.referenceLink}">${row.referenceLink}</a>
                            </c:when>
                            <c:when test="${not empty row.referenceLink}">
                                <span title="${row.referenceLink}">${row.referenceLink}</span>
                            </c:when>
                            <c:otherwise><span class="muted">—</span></c:otherwise>
                        </c:choose>
                    </td>
                    <td title="${row.category}">${row.category}</td>
                    <td class="pipeline-col-wrap" title="${row.channels}">${row.channels}</td>
                    <td title="${row.actor}">${row.actor}</td>
                    <td class="pipeline-col-wrap" title="${row.cameraPersons}">${row.cameraPersons}</td>
                    <td class="pipeline-col-wrap" title="${row.models}">${row.models}</td>
                    <td class="pipeline-col-wrap" title="${row.videoEditors}">${row.videoEditors}</td>
                    <td>
                        <c:choose>
                            <c:when test="${not empty row.driveLink}">
                                <a class="pipeline-link-icon" href="${row.driveLink}" target="_blank" rel="noopener noreferrer"
                                   title="Open Drive Link" aria-label="Open Drive Link">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                                         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                                        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
                                    </svg>
                                </a>
                            </c:when>
                            <c:otherwise><span class="pipeline-link-muted" title="No Drive Link set">—</span></c:otherwise>
                        </c:choose>
                    </td>
                    <td>${row.plannedLiveDate}</td>
                    <td>${row.shootDate}</td>
                    <td>${row.editDate}</td>
                    <td>${row.liveDate}</td>
                    <td class="pipeline-col-wrap" title="${row.platforms}">${row.platforms}</td>
                    <td>
                        <c:choose>
                            <c:when test="${row.performanceLinkEligible}">
                                <a class="performance-cell clickable"
                                   href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}#performance">${row.performanceState}</a>
                            </c:when>
                            <c:otherwise><span class="muted">${row.performanceState}</span></c:otherwise>
                        </c:choose>
                    </td>
                    <td><span class="status-badge">${row.status}</span></td>
                </tr>
            </c:forEach>
            <c:if test="${empty pipelineRows}"><tr><td colspan="18" class="muted">No deliverables yet.</td></tr></c:if>
            </tbody>
        </table>
    </div>
</main>
</body>
</html>
