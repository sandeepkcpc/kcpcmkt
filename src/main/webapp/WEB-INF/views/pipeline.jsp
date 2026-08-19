<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Content Pipeline</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%-- ENG-071: ONE shared GET form spans the search bar through the end of the table - every
         per-column filter popup's Apply button submits THIS same form, so clicking Apply in any
         one popup carries every other column's current filter value along with it, with zero
         hidden-field duplication. Sort links/pagination links stay plain <a href> navigations
         (unaffected by whatever is currently typed/selected but not yet Applied in an open popup),
         using the shared ${filterQueryString} built once in LandingMvcController#pipeline. --%>
    <form method="get" action="${pageContext.request.contextPath}/app/pipeline" id="pipelineFilterForm">
        <input type="hidden" name="page" value="1">
        <input type="hidden" name="size" value="${pipelineSize}">
        <input type="hidden" name="sortBy" value="${sortByParam}">
        <input type="hidden" name="sortDir" value="${sortDirParam}">

        <div class="pipeline-header-row">
            <div>
                <h1>Content Pipeline</h1>
                <p class="muted">One row per Content ID &middot; All dates in YYYY-MM-DD</p>
            </div>
            <div class="pipeline-filters-bar">
                <div class="pipeline-search-field">
                    <input type="text" name="q" value="${fn:escapeXml(qParam)}" placeholder="Search Content ID, Idea, SKU...">
                    <span class="pipeline-search-icon">&#128269;</span>
                </div>
                <button type="submit" class="btn-outline">Search</button>
                <a class="btn-outline" href="${pageContext.request.contextPath}/app/pipeline">Reset Filters</a>
            </div>
        </div>

        <%-- ENG-069: pure display summaries over the same rows the table renders below - never a
             separate query, never a new backend status (see PipelineDashboardService/LandingMvcController). --%>
        <div class="pipeline-kpi-cards">
            <div class="pipeline-kpi-card kpi-planning">
                <div class="pipeline-kpi-icon">&#128203;</div>
                <div>
                    <div class="pipeline-kpi-title">Planning</div>
                    <div class="pipeline-kpi-count">${planningCount}</div>
                    <div class="pipeline-kpi-subtitle">Content IDs</div>
                </div>
            </div>
            <div class="pipeline-kpi-card kpi-shoot">
                <div class="pipeline-kpi-icon">&#128247;</div>
                <div>
                    <div class="pipeline-kpi-title">Shoot</div>
                    <div class="pipeline-kpi-count">${shootCount}</div>
                    <div class="pipeline-kpi-subtitle">Content IDs</div>
                </div>
            </div>
            <div class="pipeline-kpi-card kpi-edit">
                <div class="pipeline-kpi-icon">&#9999;&#65039;</div>
                <div>
                    <div class="pipeline-kpi-title">Edit</div>
                    <div class="pipeline-kpi-count">${editCount}</div>
                    <div class="pipeline-kpi-subtitle">Content IDs</div>
                </div>
            </div>
            <div class="pipeline-kpi-card kpi-publishing">
                <div class="pipeline-kpi-icon">&#128228;</div>
                <div>
                    <div class="pipeline-kpi-title">Publishing</div>
                    <div class="pipeline-kpi-count">${publishingCount}</div>
                    <div class="pipeline-kpi-subtitle">Content IDs</div>
                </div>
            </div>
            <div class="pipeline-kpi-card kpi-attention">
                <div class="pipeline-kpi-icon">&#9201;</div>
                <div>
                    <div class="pipeline-kpi-title">Attention / Delayed</div>
                    <div class="pipeline-kpi-count">${attentionDelayedCount}</div>
                    <div class="pipeline-kpi-subtitle">Content IDs</div>
                </div>
            </div>
        </div>

        <div class="pipeline-scroll">
            <table class="pipeline-table" id="pipelineTable">
                <thead>
                <tr class="pipeline-group-row">
                    <th class="pipeline-group-content" colspan="4">Content</th>
                    <th class="pipeline-group-people" colspan="3">People</th>
                    <th class="pipeline-group-planned" colspan="3">Planned Dates</th>
                    <th class="pipeline-group-actual" colspan="3">Actual Dates</th>
                    <th class="pipeline-group-publication" colspan="2">Publication</th>
                    <th class="pipeline-group-current" colspan="2">Current</th>
                </tr>
                <tr class="pipeline-col-row">
                    <c:set var="sortField" value="contentId"/><c:set var="sortLabel" value="Content ID"/>
                    <th class="pipeline-col-id"><%@ include file="fragments/pipeline-sort-header.jspf" %></th>

                    <c:set var="filterField" value="sku"/><c:set var="filterLabel" value="SKU"/>
                    <c:set var="filterValue" value="${skuParam}"/><c:set var="filterActive" value="${skuFilterActive}"/>
                    <c:set var="filterSortField" value="sku"/>
                    <th><%@ include file="fragments/pipeline-text-filter.jspf" %></th>

                    <c:set var="filterField" value="idea"/><c:set var="filterLabel" value="Idea"/>
                    <c:set var="filterValue" value="${ideaParam}"/><c:set var="filterActive" value="${ideaFilterActive}"/>
                    <c:set var="filterSortField" value="idea"/>
                    <th><%@ include file="fragments/pipeline-text-filter.jspf" %></th>

                    <th>
                        <div class="pipeline-th-flex">
                            <c:set var="sortField" value="priority"/><c:set var="sortLabel" value="Priority"/>
                            <%@ include file="fragments/pipeline-sort-header.jspf" %>
                            <button type="button" class="pipeline-filter-trigger ${priorityFilterActive ? 'active' : ''}" data-popup-target="popup-priority" aria-label="Filter Priority">&#9660;</button>
                        </div>
                        <div class="pipeline-filter-popup hidden" id="popup-priority">
                            <label class="pipeline-filter-radio"><input type="radio" name="priority" value="" ${empty priorityParam ? 'checked' : ''}> All</label>
                            <label class="pipeline-filter-radio"><input type="radio" name="priority" value="HIGH" ${priorityParam == 'HIGH' ? 'checked' : ''}> High</label>
                            <label class="pipeline-filter-radio"><input type="radio" name="priority" value="MEDIUM" ${priorityParam == 'MEDIUM' ? 'checked' : ''}> Medium</label>
                            <label class="pipeline-filter-radio"><input type="radio" name="priority" value="LOW" ${priorityParam == 'LOW' ? 'checked' : ''}> Low</label>
                            <div class="pipeline-filter-popup-actions">
                                <button type="button" class="btn-outline pipeline-filter-clear">Clear</button>
                                <button type="submit" class="btn-outline">Apply</button>
                            </div>
                        </div>
                    </th>

                    <c:set var="filterField" value="cameraperson"/><c:set var="filterLabel" value="Cameraperson(s)"/>
                    <c:set var="filterValue" value="${camerapersonParam}"/><c:set var="filterActive" value="${camerapersonFilterActive}"/>
                    <c:set var="filterSortField" value=""/>
                    <th><%@ include file="fragments/pipeline-text-filter.jspf" %></th>

                    <c:set var="filterField" value="model"/><c:set var="filterLabel" value="Model(s)"/>
                    <c:set var="filterValue" value="${modelParam}"/><c:set var="filterActive" value="${modelFilterActive}"/>
                    <c:set var="filterSortField" value=""/>
                    <th><%@ include file="fragments/pipeline-text-filter.jspf" %></th>

                    <c:set var="filterField" value="videoEditor"/><c:set var="filterLabel" value="Video Editor(s)"/>
                    <c:set var="filterValue" value="${videoEditorParam}"/><c:set var="filterActive" value="${videoEditorFilterActive}"/>
                    <c:set var="filterSortField" value=""/>
                    <th><%@ include file="fragments/pipeline-text-filter.jspf" %></th>

                    <c:set var="filterField" value="plannedShoot"/><c:set var="filterLabel" value="Planned Shoot Date"/>
                    <c:set var="filterFrom" value="${plannedShootFromParam}"/><c:set var="filterTo" value="${plannedShootToParam}"/>
                    <c:set var="filterActive" value="${plannedShootFilterActive}"/><c:set var="filterSortField" value="plannedShootDate"/>
                    <th><%@ include file="fragments/pipeline-date-range-filter.jspf" %></th>

                    <c:set var="filterField" value="plannedEdit"/><c:set var="filterLabel" value="Planned Edit Date"/>
                    <c:set var="filterFrom" value="${plannedEditFromParam}"/><c:set var="filterTo" value="${plannedEditToParam}"/>
                    <c:set var="filterActive" value="${plannedEditFilterActive}"/><c:set var="filterSortField" value="plannedEditDate"/>
                    <th><%@ include file="fragments/pipeline-date-range-filter.jspf" %></th>

                    <c:set var="filterField" value="plannedLive"/><c:set var="filterLabel" value="Planned Live Date"/>
                    <c:set var="filterFrom" value="${plannedLiveFromParam}"/><c:set var="filterTo" value="${plannedLiveToParam}"/>
                    <c:set var="filterActive" value="${plannedLiveFilterActive}"/><c:set var="filterSortField" value="plannedLiveDate"/>
                    <th><%@ include file="fragments/pipeline-date-range-filter.jspf" %></th>

                    <c:set var="filterField" value="actualShoot"/><c:set var="filterLabel" value="Actual Shoot Date"/>
                    <c:set var="filterFrom" value="${actualShootFromParam}"/><c:set var="filterTo" value="${actualShootToParam}"/>
                    <c:set var="filterActive" value="${actualShootFilterActive}"/><c:set var="filterSortField" value="actualShootDate"/>
                    <th><%@ include file="fragments/pipeline-date-range-filter.jspf" %></th>

                    <c:set var="filterField" value="actualEdit"/><c:set var="filterLabel" value="Actual Edit Date"/>
                    <c:set var="filterFrom" value="${actualEditFromParam}"/><c:set var="filterTo" value="${actualEditToParam}"/>
                    <c:set var="filterActive" value="${actualEditFilterActive}"/><c:set var="filterSortField" value="actualEditDate"/>
                    <th><%@ include file="fragments/pipeline-date-range-filter.jspf" %></th>

                    <c:set var="filterField" value="actualLive"/><c:set var="filterLabel" value="Actual Live Date"/>
                    <c:set var="filterFrom" value="${actualLiveFromParam}"/><c:set var="filterTo" value="${actualLiveToParam}"/>
                    <c:set var="filterActive" value="${actualLiveFilterActive}"/><c:set var="filterSortField" value="actualLiveDate"/>
                    <th><%@ include file="fragments/pipeline-date-range-filter.jspf" %></th>

                    <th>
                        <div class="pipeline-th-flex">
                            <span class="pipeline-th-label">Channels / Platforms</span>
                            <button type="button" class="pipeline-filter-trigger ${platformChannelFilterActive ? 'active' : ''}" data-popup-target="popup-platform" aria-label="Filter Channels / Platforms">&#9660;</button>
                        </div>
                        <div class="pipeline-filter-popup hidden" id="popup-platform">
                            <label>Platform
                                <select name="platform">
                                    <option value="">All Platforms</option>
                                    <c:forEach var="p" items="${platformOptions}">
                                        <option value="${p}" ${platformParam == p ? 'selected' : ''}><c:out value="${p}"/></option>
                                    </c:forEach>
                                </select>
                            </label>
                            <label>Channel
                                <select name="channel">
                                    <option value="">All Channels</option>
                                    <c:forEach var="ch" items="${channelOptions}">
                                        <option value="${ch}" ${channelParam == ch ? 'selected' : ''}><c:out value="${ch}"/></option>
                                    </c:forEach>
                                </select>
                            </label>
                            <div class="pipeline-filter-popup-actions">
                                <button type="button" class="btn-outline pipeline-filter-clear">Clear</button>
                                <button type="submit" class="btn-outline">Apply</button>
                            </div>
                        </div>
                    </th>

                    <th>
                        <div class="pipeline-th-flex">
                            <span class="pipeline-th-label">Performance</span>
                            <button type="button" class="pipeline-filter-trigger ${performanceFilterActive ? 'active' : ''}" data-popup-target="popup-performance" aria-label="Filter Performance">&#9660;</button>
                        </div>
                        <div class="pipeline-filter-popup hidden" id="popup-performance">
                            <label class="pipeline-filter-radio"><input type="radio" name="performanceState" value="" ${empty performanceStateParam ? 'checked' : ''}> All</label>
                            <label class="pipeline-filter-radio"><input type="radio" name="performanceState" value="Not Yet Applicable" ${performanceStateParam == 'Not Yet Applicable' ? 'checked' : ''}> Not Due</label>
                            <label class="pipeline-filter-radio"><input type="radio" name="performanceState" value="Pending" ${performanceStateParam == 'Pending' ? 'checked' : ''}> Pending</label>
                            <label class="pipeline-filter-radio"><input type="radio" name="performanceState" value="Updated" ${performanceStateParam == 'Updated' ? 'checked' : ''}> Updated</label>
                            <label class="pipeline-filter-radio"><input type="radio" name="performanceState" value="Completed" ${performanceStateParam == 'Completed' ? 'checked' : ''}> Completed</label>
                            <div class="pipeline-filter-popup-actions">
                                <button type="button" class="btn-outline pipeline-filter-clear">Clear</button>
                                <button type="submit" class="btn-outline">Apply</button>
                            </div>
                        </div>
                    </th>

                    <th class="pipeline-col-status">
                        <div class="pipeline-th-flex">
                            <c:set var="sortField" value="status"/><c:set var="sortLabel" value="Status"/>
                            <%@ include file="fragments/pipeline-sort-header.jspf" %>
                            <button type="button" class="pipeline-filter-trigger ${statusFilterActive ? 'active' : ''}" data-popup-target="popup-status" aria-label="Filter Status">&#9660;</button>
                        </div>
                        <div class="pipeline-filter-popup hidden" id="popup-status">
                            <select name="status">
                                <option value="">All Status</option>
                                <c:forEach var="s" items="${statusOptions}">
                                    <option value="${s}" ${statusParam == s ? 'selected' : ''}><c:out value="${s}"/></option>
                                </c:forEach>
                            </select>
                            <label class="pipeline-filter-checkbox">
                                <input type="checkbox" name="delayed" value="true" ${delayedParam ? 'checked' : ''}>
                                Delayed / Attention only
                            </label>
                            <div class="pipeline-filter-popup-actions">
                                <button type="button" class="btn-outline pipeline-filter-clear">Clear</button>
                                <button type="submit" class="btn-outline">Apply</button>
                            </div>
                        </div>
                    </th>
                    <th class="pipeline-col-action">Action</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="row" items="${pipelineRows}">
                    <tr>
                        <td class="pipeline-col-id">
                            <a href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}">${row.contentId}</a>
                            <c:if test="${not empty row.driveLink}">
                                <a class="pipeline-link-icon" href="${row.driveLink}" target="_blank" rel="noopener noreferrer"
                                   title="Open Drive Link" aria-label="Open Drive Link">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                                         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                                        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
                                    </svg>
                                </a>
                            </c:if>
                        </td>
                        <td>${row.sku}</td>
                        <td class="pipeline-col-wrap" title="${row.ideaTitle}">${row.ideaTitle}</td>
                        <td>
                            <c:if test="${not empty row.priority}">
                                <span class="priority-pill priority-${row.priority == 'HIGH' ? 'high' : (row.priority == 'MEDIUM' ? 'medium' : 'low')}"><c:out value="${row.priority}"/></span>
                            </c:if>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${row.cameraPersons == '—'}"><span class="pipeline-team-empty">—</span></c:when>
                                <c:otherwise>
                                    <c:set var="camNames" value="${fn:split(row.cameraPersons, ',')}"/>
                                    <span class="pipeline-team-chip" title="${row.cameraPersons}">
                                        <c:out value="${fn:trim(camNames[0])}"/>
                                        <c:if test="${fn:length(camNames) gt 1}"><span class="pipeline-team-more">+${fn:length(camNames) - 1}</span></c:if>
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${row.models == '—'}"><span class="pipeline-team-empty">—</span></c:when>
                                <c:otherwise>
                                    <c:set var="modelNames" value="${fn:split(row.models, ',')}"/>
                                    <span class="pipeline-team-chip" title="${row.models}">
                                        <c:out value="${fn:trim(modelNames[0])}"/>
                                        <c:if test="${fn:length(modelNames) gt 1}"><span class="pipeline-team-more">+${fn:length(modelNames) - 1}</span></c:if>
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${row.videoEditors == '—'}"><span class="pipeline-team-empty">—</span></c:when>
                                <c:otherwise>
                                    <c:set var="editorNames" value="${fn:split(row.videoEditors, ',')}"/>
                                    <span class="pipeline-team-chip" title="${row.videoEditors}">
                                        <c:out value="${fn:trim(editorNames[0])}"/>
                                        <c:if test="${fn:length(editorNames) gt 1}"><span class="pipeline-team-more">+${fn:length(editorNames) - 1}</span></c:if>
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>${empty row.plannedShootDate ? '—' : row.plannedShootDate}</td>
                        <td>${empty row.plannedEditDate ? '—' : row.plannedEditDate}</td>
                        <td>${empty row.plannedLiveDate ? '—' : row.plannedLiveDate}</td>
                        <td>${row.actualShootDate}</td>
                        <td>${row.actualEditDate}</td>
                        <td>${row.actualLiveDate}</td>
                        <td>
                            <c:choose>
                                <c:when test="${row.platforms == '—'}"><span class="pipeline-team-empty">—</span></c:when>
                                <c:otherwise>
                                    <c:set var="platformNames" value="${fn:split(row.platforms, ',')}"/>
                                    <span class="pipeline-team-chip" title="Platforms: ${row.platforms} &middot; Channels: ${row.channels}">
                                        <c:out value="${fn:trim(platformNames[0])}"/>
                                        <c:if test="${fn:length(platformNames) gt 1}"><span class="pipeline-team-more">+${fn:length(platformNames) - 1}</span></c:if>
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${row.performanceLinkEligible}">
                                    <a class="performance-cell clickable"
                                       href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}#performance">${row.performanceState}</a>
                                </c:when>
                                <c:otherwise><span class="muted">Not Due</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="pipeline-col-status">
                            <div class="pipeline-status-cell">
                                <c:choose>
                                    <c:when test="${row.status == 'Planning' or row.status == 'Planning Review' or row.status == 'Planning Approved'}">
                                        <span class="pipeline-status-badge stage-planning"><c:out value="${row.status}"/></span>
                                    </c:when>
                                    <c:when test="${row.status == 'Shoot Assigned' or row.status == 'Shoot In Progress' or row.status == 'Shoot Review' or row.status == 'Shoot Approved'}">
                                        <span class="pipeline-status-badge stage-shoot"><c:out value="${row.status}"/></span>
                                    </c:when>
                                    <c:when test="${row.status == 'Edit Assigned' or row.status == 'Editing' or row.status == 'Edit Review' or row.status == 'Edit Approved'}">
                                        <span class="pipeline-status-badge stage-edit"><c:out value="${row.status}"/></span>
                                    </c:when>
                                    <c:when test="${row.status == 'Ready for Publishing' or row.status == 'Publishing'}">
                                        <span class="pipeline-status-badge stage-publishing"><c:out value="${row.status}"/></span>
                                    </c:when>
                                    <c:when test="${row.status == 'Performance Pending' or row.status == 'Performance Update'}">
                                        <span class="pipeline-status-badge stage-performance"><c:out value="${row.status}"/></span>
                                    </c:when>
                                    <c:when test="${row.status == 'Completed'}">
                                        <span class="pipeline-status-badge stage-completed"><c:out value="${row.status}"/></span>
                                    </c:when>
                                    <c:when test="${row.status == 'Cancelled'}">
                                        <span class="pipeline-status-badge stage-cancelled"><c:out value="${row.status}"/></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="pipeline-status-badge stage-planning"><c:out value="${row.status}"/></span>
                                    </c:otherwise>
                                </c:choose>
                                <c:if test="${row.delayed}">
                                    <span class="pipeline-delay-note">Delayed &middot; ${row.delayDays} day<c:if test="${row.delayDays != 1}">s</c:if></span>
                                </c:if>
                            </div>
                        </td>
                        <td class="pipeline-col-action">
                            <a class="pipeline-view-details" href="${pageContext.request.contextPath}/app/deliverables/${row.contentPlanId}">View Details &rarr;</a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty pipelineRows}"><tr><td colspan="17" class="muted">No deliverables yet.</td></tr></c:if>
                </tbody>
            </table>
        </div>
    </form>

    <div class="pagination">
        <div class="pipeline-per-page">
            <span>
                <c:choose>
                    <c:when test="${pipelineTotalCount == 0}">Showing 0 of 0 entries</c:when>
                    <c:otherwise>Showing ${pipelineFromIndex} to ${pipelineToIndex} of ${pipelineTotalCount} entries</c:otherwise>
                </c:choose>
            </span>
            <select id="pipelinePerPage" aria-label="Rows per page"
                    onchange="window.location.href='${pageContext.request.contextPath}/app/pipeline?${filterQueryString}&sortBy=${sortByParam}&sortDir=${sortDirParam}&page=1&size=' + this.value">
                <option value="10" ${pipelineSize == 10 ? 'selected' : ''}>10 per page</option>
                <option value="25" ${pipelineSize == 25 ? 'selected' : ''}>25 per page</option>
                <option value="50" ${pipelineSize == 50 ? 'selected' : ''}>50 per page</option>
                <option value="100" ${pipelineSize == 100 ? 'selected' : ''}>100 per page</option>
            </select>
        </div>
        <div class="pagination-controls">
            <c:set var="pipelinePageBase" value="${pageContext.request.contextPath}/app/pipeline?${filterQueryString}&sortBy=${sortByParam}&sortDir=${sortDirParam}&size=${pipelineSize}&page="/>
            <c:choose>
                <c:when test="${pipelineCurrentPage > 1}"><a href="${pipelinePageBase}${pipelineCurrentPage - 1}">&#8249;</a></c:when>
                <c:otherwise><span class="page-disabled">&#8249;</span></c:otherwise>
            </c:choose>
            <c:forEach begin="1" end="${pipelineTotalPages}" var="p">
                <c:choose>
                    <c:when test="${p == pipelineCurrentPage}"><span class="page-current">${p}</span></c:when>
                    <c:otherwise><a href="${pipelinePageBase}${p}">${p}</a></c:otherwise>
                </c:choose>
            </c:forEach>
            <c:choose>
                <c:when test="${pipelineCurrentPage < pipelineTotalPages}"><a href="${pipelinePageBase}${pipelineCurrentPage + 1}">&#8250;</a></c:when>
                <c:otherwise><span class="page-disabled">&#8250;</span></c:otherwise>
            </c:choose>
        </div>
    </div>

    <div class="pipeline-footer-note">
        <span>&#8505;</span>
        <span>One row represents one Content ID. Click <strong>View Details</strong> to open full workflow information and take actions.</span>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/pipeline-dashboard.js" defer></script>
</body>
</html>
