<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — My Performance</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <div class="page-header-row">
        <div>
            <h1>My Performance</h1>
            <%-- Employee-specific self-service dashboard: every value on this page is scoped to the
                 authenticated employee only (LandingMvcController#myPerformance resolves the
                 employee from the session/JWT principal, never a request parameter). --%>
            <p class="muted">Your own marks, completed work and delay performance across every stage you have worked on.</p>
        </div>
    </div>

    <%-- KPI cards - scoped to the selected date range only, independent of the Stage/Role/Status/
         Delay/Search filters applied to the table below. Total Marks only renders for a Shoot/Edit
         mark-eligible employee (${markVisibilityEligible}, computed in
         LandingMvcController#myPerformance from the same PERM_18/PERM_19 execution permissions
         used everywhere else in this app - never Business Role, never Publisher/Model alone) - the
         card count switches to the existing 2-card responsive layout instead of leaving a blank
         slot where it used to sit. --%>
    <div class="kpi-cards ${markVisibilityEligible ? 'kpi-cards-3' : 'kpi-cards-2'}">
        <c:if test="${markVisibilityEligible}">
        <div class="kpi-card kpi-completed">
            <div class="kpi-card-icon">&#127942;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Total Marks</span>
                    <span class="kpi-card-count">${totalMarks}</span></div>
                <div class="kpi-card-subtitle">Selected range</div>
            </div>
        </div>
        </c:if>
        <div class="kpi-card kpi-underreview">
            <div class="kpi-card-icon">&#9989;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Tasks Completed</span><span class="kpi-card-count">${tasksCompletedCount}</span></div>
                <div class="kpi-card-subtitle">Selected range</div>
            </div>
        </div>
        <div class="kpi-card kpi-rejected">
            <div class="kpi-card-icon">&#9201;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Delayed Tasks</span><span class="kpi-card-count">${delayedTasksCount}</span></div>
                <div class="kpi-card-subtitle">Completed after planned date</div>
            </div>
        </div>
    </div>

    <div class="panel my-work-table-wrapper">
        <form method="get" action="${pageContext.request.contextPath}/app/my-performance" class="my-ideas-filters" id="myPerformanceFilterForm">
            <div class="filter-field filter-field-search">
                <label for="mp-search">Search</label>
                <input type="text" id="mp-search" name="q" class="my-ideas-search-input"
                       placeholder="Search by Content ID or title..." value="${fn:escapeXml(q)}">
            </div>
            <div class="filter-field">
                <label for="mp-stage">Work / Stage</label>
                <select id="mp-stage" name="stage">
                    <option value="ALL" ${empty stage or stage == 'ALL' ? 'selected' : ''}>All</option>
                    <option value="SHOOT" ${stage == 'SHOOT' ? 'selected' : ''}>Shoot</option>
                    <option value="EDIT" ${stage == 'EDIT' ? 'selected' : ''}>Edit</option>
                    <option value="PUBLISH" ${stage == 'PUBLISH' ? 'selected' : ''}>Publishing</option>
                </select>
            </div>
            <div class="filter-field">
                <label for="mp-role">Role</label>
                <select id="mp-role" name="role">
                    <option value="ALL" ${empty role or role == 'ALL' ? 'selected' : ''}>All</option>
                    <option value="Cameraperson" ${role == 'Cameraperson' ? 'selected' : ''}>Cameraperson</option>
                    <option value="Editor" ${role == 'Editor' ? 'selected' : ''}>Editor</option>
                    <option value="Publisher" ${role == 'Publisher' ? 'selected' : ''}>Publisher</option>
                    <option value="Model" ${role == 'Model' ? 'selected' : ''}>Model</option>
                </select>
            </div>
            <div class="filter-field">
                <label for="mp-status">Status</label>
                <select id="mp-status" name="status">
                    <option value="ALL" ${empty status or status == 'ALL' ? 'selected' : ''}>All Status</option>
                    <option value="COMPLETED" ${status == 'COMPLETED' ? 'selected' : ''}>Completed</option>
                    <option value="DELAYED" ${status == 'DELAYED' ? 'selected' : ''}>Delayed</option>
                    <option value="ON_TIME" ${status == 'ON_TIME' ? 'selected' : ''}>On Time</option>
                    <option value="EARLY" ${status == 'EARLY' ? 'selected' : ''}>Early</option>
                </select>
            </div>
            <div class="filter-field">
                <label for="mp-delay">Delay</label>
                <select id="mp-delay" name="delay">
                    <option value="ALL" ${empty delay or delay == 'ALL' ? 'selected' : ''}>All</option>
                    <option value="DELAYED" ${delay == 'DELAYED' ? 'selected' : ''}>Delayed</option>
                    <option value="ON_TIME" ${delay == 'ON_TIME' ? 'selected' : ''}>On Time</option>
                    <option value="EARLY" ${delay == 'EARLY' ? 'selected' : ''}>Early</option>
                </select>
            </div>
            <div class="filter-field">
                <label for="mp-from">From</label>
                <input type="date" id="mp-from" name="fromDate" value="${fromDate}">
            </div>
            <div class="filter-field">
                <label for="mp-to">To</label>
                <input type="date" id="mp-to" name="toDate" value="${toDate}">
            </div>
            <div class="filter-field">
                <label for="mp-page-size">Per Page</label>
                <select id="mp-page-size" name="pageSize" onchange="this.form.submit()">
                    <option value="10" ${pageSize == 10 ? 'selected' : ''}>10 per page</option>
                    <option value="25" ${pageSize == 25 ? 'selected' : ''}>25 per page</option>
                    <option value="50" ${pageSize == 50 ? 'selected' : ''}>50 per page</option>
                </select>
            </div>
            <div class="filter-field">
                <button type="submit" class="btn-outline">Search</button>
            </div>
            <div class="filter-field">
                <a class="btn-outline" href="${pageContext.request.contextPath}/app/my-performance">&#8635; Clear Filters</a>
            </div>
        </form>

        <h2>Task Performance <span class="count-badge">${performanceRowsTotalCount}</span></h2>
        <table class="data-table">
            <thead>
            <tr>
                <th>Content ID</th>
                <th>Stage</th>
                <th>Role</th>
                <th>Completed On</th>
                <th>Delay</th>
                <c:if test="${markVisibilityEligible}"><th>Mark</th></c:if>
                <th>View</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="row" items="${performanceRows}">
                <tr>
                    <td>${row.contentId}</td>
                    <td>
                        <c:choose>
                            <c:when test="${row.stage == 'SHOOT'}"><span class="stage-badge stage-shoot">SHOOT</span></c:when>
                            <c:when test="${row.stage == 'EDIT'}"><span class="stage-badge stage-edit">EDIT</span></c:when>
                            <c:otherwise><span class="stage-badge stage-publish">PUBLISHING</span></c:otherwise>
                        </c:choose>
                    </td>
                    <td><c:out value="${row.roleLabel}"/></td>
                    <td><c:if test="${not empty row.completedOn}">${kcpc:ist(row.completedOn)}</c:if></td>
                    <td>
                        <c:choose>
                            <c:when test="${row.delayStatus == 'DELAYED'}">
                                <span class="status-pill status-delayed">Delayed &middot; ${row.delayDays} day<c:if test="${row.delayDays != 1}">s</c:if></span>
                            </c:when>
                            <c:when test="${row.delayStatus == 'ON_TIME'}"><span class="status-pill status-completed">On Time</span></c:when>
                            <c:when test="${row.delayStatus == 'EARLY'}"><span class="status-pill status-completed">Early &middot; ${-1 * row.delayDays} day<c:if test="${row.delayDays != -1}">s</c:if></span></c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </td>
                    <c:if test="${markVisibilityEligible}">
                    <td>
                        <c:choose>
                            <c:when test="${not empty row.mark}"><c:out value="${row.mark}"/></c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </td>
                    </c:if>
                    <%-- Reuses the SAME task-specific detail screen My Work's own completed-work
                         links already open (DeliverableMvcController#view, ?tab=shoot/edit/
                         publishing) - no separate performance-detail view. --%>
                    <td>
                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}?tab=<c:choose><c:when test="${row.stage == 'SHOOT'}">shoot</c:when><c:when test="${row.stage == 'EDIT'}">edit</c:when><c:otherwise>publishing</c:otherwise></c:choose>">View</a>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty performanceRows}">
                <tr><td colspan="${markVisibilityEligible ? 7 : 6}" class="muted">No performance records match these filters.</td></tr>
            </c:if>
            </tbody>
        </table>

        <c:if test="${performanceRowsTotalCount > 0}">
            <div class="pagination">
                <span>Page ${currentPage} of ${totalPages} &middot; ${performanceRowsTotalCount} records</span>
                <div class="pagination-controls">
                    <c:choose>
                        <c:when test="${currentPage > 1}">
                            <c:url var="prevPageUrl" value="/app/my-performance">
                                <c:param name="q" value="${q}"/><c:param name="stage" value="${stage}"/>
                                <c:param name="role" value="${role}"/><c:param name="status" value="${status}"/>
                                <c:param name="delay" value="${delay}"/><c:param name="fromDate" value="${fromDate}"/>
                                <c:param name="toDate" value="${toDate}"/><c:param name="pageSize" value="${pageSize}"/>
                                <c:param name="page" value="${currentPage - 1}"/>
                            </c:url>
                            <a href="${prevPageUrl}">&#8249;</a>
                        </c:when>
                        <c:otherwise><span class="page-disabled">&#8249;</span></c:otherwise>
                    </c:choose>
                    <c:forEach begin="1" end="${totalPages}" var="p">
                        <c:choose>
                            <c:when test="${p == currentPage}"><span class="page-current">${p}</span></c:when>
                            <c:otherwise>
                                <c:url var="pageUrl" value="/app/my-performance">
                                    <c:param name="q" value="${q}"/><c:param name="stage" value="${stage}"/>
                                    <c:param name="role" value="${role}"/><c:param name="status" value="${status}"/>
                                    <c:param name="delay" value="${delay}"/><c:param name="fromDate" value="${fromDate}"/>
                                    <c:param name="toDate" value="${toDate}"/><c:param name="pageSize" value="${pageSize}"/>
                                    <c:param name="page" value="${p}"/>
                                </c:url>
                                <a href="${pageUrl}">${p}</a>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>
                    <c:choose>
                        <c:when test="${currentPage < totalPages}">
                            <c:url var="nextPageUrl" value="/app/my-performance">
                                <c:param name="q" value="${q}"/><c:param name="stage" value="${stage}"/>
                                <c:param name="role" value="${role}"/><c:param name="status" value="${status}"/>
                                <c:param name="delay" value="${delay}"/><c:param name="fromDate" value="${fromDate}"/>
                                <c:param name="toDate" value="${toDate}"/><c:param name="pageSize" value="${pageSize}"/>
                                <c:param name="page" value="${currentPage + 1}"/>
                            </c:url>
                            <a href="${nextPageUrl}">&#8250;</a>
                        </c:when>
                        <c:otherwise><span class="page-disabled">&#8250;</span></c:otherwise>
                    </c:choose>
                </div>
            </div>
        </c:if>
    </div>
</main>
</body>
</html>
