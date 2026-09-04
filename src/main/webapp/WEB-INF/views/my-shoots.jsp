<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — My Shoots</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <link rel="icon" type="image/x-icon" href="${pageContext.request.contextPath}/images/favicon.ico">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <div class="page-header-row">
        <div>
            <h1>My Shoots</h1>
            <p class="muted">
                <c:out value="${user.fullName}"/> &bull; <c:out value="${fn:toUpperCase(businessRoleName)}"/><br/>
                View the content shoots where you are participating as Model/Talent.
            </p>
        </div>
    </div>

    <%-- ENG-067: 2 KPI cards only - a Model's interest is the shoot day itself, not the content's
         full downstream lifecycle, so there's no Rework/Delayed/Completed breakdown here. --%>
    <div class="kpi-cards kpi-cards-2">
        <div class="kpi-card kpi-active">
            <div class="kpi-card-icon">&#127909;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Upcoming Shoots</span><span class="kpi-card-count">${upcomingShootsCount}</span></div>
                <div class="kpi-card-subtitle">Shoots you're part of</div>
            </div>
        </div>
        <div class="kpi-card kpi-completed">
            <div class="kpi-card-icon">&#128197;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Next Shoot</span><span class="kpi-card-count">${nextShootDateDisplay}</span></div>
                <div class="kpi-card-subtitle">Your next scheduled shoot date</div>
            </div>
        </div>
    </div>

    <div class="my-work-tabs">
        <button type="button" class="my-work-tab active" data-tab="upcoming">Upcoming</button>
        <button type="button" class="my-work-tab" data-tab="past">Past</button>
    </div>

    <div class="my-work-tab-panel" data-tab-panel="upcoming">
        <div class="panel my-work-table-wrapper">
            <h2>Upcoming Shoots</h2>
            <table class="data-table">
                <thead>
                <tr>
                    <th>Content ID</th>
                    <th>Idea / Content</th>
                    <th>Shoot Date</th>
                    <th>My Role</th>
                    <th>Other Talent</th>
                    <th>View</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="row" items="${upcomingShoots}">
                    <tr>
                        <td><a class="content-id-link" href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}">${row.contentId}</a></td>
                        <td><c:out value="${row.title}"/></td>
                        <td><c:out value="${empty row.plannedShootDate ? '—' : row.plannedShootDate}"/></td>
                        <td><c:out value="${row.myRole}"/></td>
                        <td><c:out value="${empty row.otherTalent ? '—' : row.otherTalent}"/></td>
                        <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}">View</a></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty upcomingShoots}">
                    <tr><td colspan="6" class="muted">No upcoming shoots.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>

    <div class="my-work-tab-panel hidden" data-tab-panel="past">
        <div class="panel my-work-table-wrapper">
            <h2>Past Shoots</h2>
            <%-- Every row here is, by construction, a completed personal task
                 (LandingMvcController#isModelShootTaskCompleted is exactly what routes a plan into
                 pastShoots instead of upcomingShoots) - historical record only, never openable:
                 no Content ID link, no View action. DeliverableMvcController#view independently
                 rejects a direct/typed URL for the same plan server-side, so this is presentation
                 of an access rule already enforced, not the rule itself. --%>
            <table class="data-table">
                <thead>
                <tr>
                    <th>Content ID</th>
                    <th>Idea / Content</th>
                    <th>Shoot Date</th>
                    <th>My Role</th>
                    <th>Other Talent</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="row" items="${pastShoots}">
                    <tr>
                        <td>${row.contentId}</td>
                        <td><c:out value="${row.title}"/></td>
                        <td><c:out value="${empty row.plannedShootDate ? '—' : row.plannedShootDate}"/></td>
                        <td><c:out value="${row.myRole}"/></td>
                        <td><c:out value="${empty row.otherTalent ? '—' : row.otherTalent}"/></td>
                        <td><span class="status-pill status-completed">Completed</span></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty pastShoots}">
                    <tr><td colspan="6" class="muted">No past shoots.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/my-work-tabs.js" defer></script>
</body>
</html>
