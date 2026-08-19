<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — My Ideas</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <div class="page-header-row">
        <div>
            <h1>My Ideas</h1>
            <p class="muted">Ideas you have submitted for review and their current status.</p>
        </div>
        <a class="btn-outline" href="${pageContext.request.contextPath}/app/ideas/new">+ Submit New Idea</a>
    </div>

    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="kpi-cards kpi-cards-5">
        <div class="kpi-card kpi-active">
            <div class="kpi-card-icon">&#128161;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Total Ideas</span><span class="kpi-card-count">${totalIdeasCount}</span></div>
                <div class="kpi-card-subtitle">All time submitted</div>
            </div>
        </div>
        <div class="kpi-card kpi-underreview">
            <div class="kpi-card-icon">&#8987;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Under Review</span><span class="kpi-card-count">${underReviewCount}</span></div>
                <div class="kpi-card-subtitle">Awaiting feedback</div>
            </div>
        </div>
        <div class="kpi-card kpi-completed">
            <div class="kpi-card-icon">&#9989;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Approved</span><span class="kpi-card-count">${approvedCount}</span></div>
                <div class="kpi-card-subtitle">Ideas approved</div>
            </div>
        </div>
        <div class="kpi-card kpi-retained">
            <div class="kpi-card-icon">&#128337;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Retained</span><span class="kpi-card-count">${retainedCount}</span></div>
                <div class="kpi-card-subtitle">Kept for future</div>
            </div>
        </div>
        <div class="kpi-card kpi-rejected">
            <div class="kpi-card-icon">&#10060;</div>
            <div class="kpi-card-body">
                <div class="kpi-card-title-row"><span class="kpi-card-title">Rejected</span><span class="kpi-card-count">${rejectedCount}</span></div>
                <div class="kpi-card-subtitle">Not approved</div>
            </div>
        </div>
    </div>

    <div class="panel my-work-table-wrapper">
        <form method="get" action="${pageContext.request.contextPath}/app/ideas" class="my-ideas-filters">
            <div class="filter-field filter-field-search">
                <label for="my-ideas-search">Search</label>
                <input type="text" id="my-ideas-search" name="q" class="my-ideas-search-input"
                       placeholder="Search ideas by title or ID..." value="${fn:escapeXml(myIdeasQuery)}">
            </div>
            <div class="filter-field">
                <label for="my-ideas-status">Idea Status</label>
                <select id="my-ideas-status" name="status">
                    <option value="ALL" ${myIdeasStatusFilter == 'ALL' ? 'selected' : ''}>All Status</option>
                    <option value="Under Review" ${myIdeasStatusFilter == 'Under Review' ? 'selected' : ''}>Under Review</option>
                    <option value="Approved" ${myIdeasStatusFilter == 'Approved' ? 'selected' : ''}>Approved</option>
                    <option value="Retained" ${myIdeasStatusFilter == 'Retained' ? 'selected' : ''}>Retained</option>
                    <option value="Rejected" ${myIdeasStatusFilter == 'Rejected' ? 'selected' : ''}>Rejected</option>
                    <option value="Reopened" ${myIdeasStatusFilter == 'Reopened' ? 'selected' : ''}>Reopened</option>
                </select>
            </div>
            <div class="filter-field">
                <label for="my-ideas-range">Submitted On</label>
                <select id="my-ideas-range" name="range">
                    <option value="ALL" ${myIdeasRangeFilter == 'ALL' ? 'selected' : ''}>All Time</option>
                    <option value="7" ${myIdeasRangeFilter == '7' ? 'selected' : ''}>Last 7 days</option>
                    <option value="30" ${myIdeasRangeFilter == '30' ? 'selected' : ''}>Last 30 days</option>
                    <option value="90" ${myIdeasRangeFilter == '90' ? 'selected' : ''}>Last 90 days</option>
                </select>
            </div>
            <div class="filter-field">
                <button type="submit" class="btn-outline">Search</button>
            </div>
            <div class="filter-field">
                <a class="btn-outline" href="${pageContext.request.contextPath}/app/ideas">&#8635; Reset</a>
            </div>
        </form>

        <table class="data-table">
            <thead>
            <tr>
                <th>Idea ID</th>
                <th>Idea Title</th>
                <th>Submitted On</th>
                <th>Idea Status</th>
                <th>Feedback</th>
                <th>Action</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="row" items="${myIdeaRows}">
                <tr>
                    <td>${row.businessIdeaCode}</td>
                    <td>
                        <c:out value="${row.title}"/>
                        <c:if test="${not empty row.description}">
                            <div class="idea-description-hint"><c:out value="${row.description}"/></div>
                        </c:if>
                    </td>
                    <td class="submitted-on-cell">
                        <div>${kcpc:istDate(row.submittedAt)}</div>
                        <div class="submitted-on-time muted">${kcpc:istTime(row.submittedAt)}</div>
                    </td>
                    <td><span class="status-pill ${row.statusCssClass}"><c:out value="${row.statusLabel}"/></span></td>
                    <td><c:out value="${empty row.feedback ? '—' : row.feedback}"/></td>
                    <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/ideas/${row.ideaId}">&#128065; View Details</a></td>
                </tr>
            </c:forEach>
            <c:if test="${empty myIdeaRows}">
                <tr><td colspan="6" class="muted">No ideas match these filters.</td></tr>
            </c:if>
            </tbody>
        </table>

        <c:if test="${myIdeasTotalCount > 0}">
            <div class="pagination">
                <span>Showing ${myIdeasFromIndex} to ${myIdeasToIndex} of ${myIdeasTotalCount} ideas</span>
                <div class="pagination-controls">
                    <c:choose>
                        <c:when test="${myIdeasCurrentPage > 1}">
                            <c:url var="prevPageUrl" value="/app/ideas">
                                <c:param name="q" value="${myIdeasQuery}"/>
                                <c:param name="status" value="${myIdeasStatusFilter}"/>
                                <c:param name="range" value="${myIdeasRangeFilter}"/>
                                <c:param name="page" value="${myIdeasCurrentPage - 1}"/>
                            </c:url>
                            <a href="${prevPageUrl}">&#8249;</a>
                        </c:when>
                        <c:otherwise><span class="page-disabled">&#8249;</span></c:otherwise>
                    </c:choose>
                    <c:forEach begin="1" end="${myIdeasTotalPages}" var="p">
                        <c:choose>
                            <c:when test="${p == myIdeasCurrentPage}"><span class="page-current">${p}</span></c:when>
                            <c:otherwise>
                                <c:url var="pageUrl" value="/app/ideas">
                                    <c:param name="q" value="${myIdeasQuery}"/>
                                    <c:param name="status" value="${myIdeasStatusFilter}"/>
                                    <c:param name="range" value="${myIdeasRangeFilter}"/>
                                    <c:param name="page" value="${p}"/>
                                </c:url>
                                <a href="${pageUrl}">${p}</a>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>
                    <c:choose>
                        <c:when test="${myIdeasCurrentPage < myIdeasTotalPages}">
                            <c:url var="nextPageUrl" value="/app/ideas">
                                <c:param name="q" value="${myIdeasQuery}"/>
                                <c:param name="status" value="${myIdeasStatusFilter}"/>
                                <c:param name="range" value="${myIdeasRangeFilter}"/>
                                <c:param name="page" value="${myIdeasCurrentPage + 1}"/>
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
