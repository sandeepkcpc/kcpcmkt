<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — My Work</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <div class="page-header-row">
        <div>
            <h1>My Work</h1>
            <%-- Permission-driven multi-function workflow: rendered from actual assignments/
                 permissions, never from Business Role - a single generic message covers every
                 employee, whether they work one stage or several. --%>
            <p class="muted">Manage your assigned work and delegated workflow responsibilities.</p>
        </div>
        <%-- ENG-057/058/066: a quick-glance summary, never a management control - Role from the
             employee's own Business Role (never Designation - Business Role remains organizational
             display only), Shoot/Edit Lead only shown when this employee is actually the Lead on at
             least one of their own currently active Shoot/Edit assignments. --%>
        <div class="employee-summary-card">
            <div>
                <div class="summary-field-label">Role</div>
                <div class="summary-field-value"><c:out value="${empty businessRoleName ? '—' : businessRoleName}"/></div>
            </div>
            <c:if test="${isShootLead}">
                <div>
                    <div class="summary-field-label">Shoot Lead</div>
                    <div class="summary-field-value"><c:out value="${shootLeadDisplayName}"/> <span class="lead-badge">Lead</span></div>
                </div>
            </c:if>
            <c:if test="${isEditLead}">
                <div>
                    <div class="summary-field-label">Edit Lead</div>
                    <div class="summary-field-value"><c:out value="${editLeadDisplayName}"/> <span class="lead-badge">Lead</span></div>
                </div>
            </c:if>
        </div>
    </div>

    <%-- Execution | Assignment Management: only rendered as a tier at all when the employee holds
         some assignment-management authority (PERM_04/06/11) - distinct from execution
         (PERM_18/19/08). Assignment Management is a delegated, actionable queue (see
         AssignmentManagementQueueService), never a historical/broader Pipeline-style view. --%>
    <c:if test="${showAssignmentManagementTier}">
    <div class="my-work-mode-tabs">
        <button type="button" class="my-work-mode-tab active" data-tab="execution">Execution</button>
        <button type="button" class="my-work-mode-tab" data-tab="assignment-mgmt">Assignment Management</button>
    </div>
    <div class="my-work-mode-panel" data-tab-panel="execution">
    </c:if>

    <%-- Stage tabs: no "All" tab - each employee sees only the stage tabs they are actually
         authorized for. Dashboard/Shoot/Edit/Publishing each render only when this employee holds
         the matching live execution permission (PERM_18/19/08) OR has real current/history
         assignment data for that stage (${showShootTab}/${showEditTab}/${showPublishTab},
         computed once in LandingMvcController#myWork - the same source the backend execution
         checks use, so a tab is never shown for a stage the employee cannot actually act in, and
         never hidden for one they can). Normally no button carries a hardcoded "active" class -
         my-work-tabs.js falls back to the first tab present in the DOM when none is pre-marked
         active, and since every button here is itself conditionally rendered, "first in the DOM"
         is always this employee's own first authorized tab (Dashboard > Shoot > Edit > Publishing
         priority, matching this fixed button order) - never a removed "All" tab. Task-specific
         stage detail screens (Shoot/Edit/Publishing Task Detail at /app/deliverables/{id}) are
         unchanged by this.

         The one exception is ${activeStageTab}, set only when the request carried a "tab"
         parameter - which the Publishing filter panels round-trip so that applying or clearing a
         filter returns the user to the tab they were filtering from, instead of bouncing them
         back to Dashboard. my-work-tabs.js already prefers a server-rendered "active" button, so
         this needs no JS change; an absent or unrecognised value leaves the fallback untouched. --%>
    <div class="my-work-stage-tabs">
        <%-- ENG-098: Publisher-only upcoming-work tab - same gate as the Publishing tab itself
             (${showPublishTab}) since it's built entirely from that same Publisher data.

             The visible LABEL is "Planning"; the identifier stays "dashboard" throughout -
             data-tab/data-tab-panel, the ?tab= parameter, the dash* filter parameters, the
             my-work-dashboard.js panel lookup and the DOM ids. That is deliberate: renaming the
             identifier would change request parameters and break any bookmarked/shared filter URL
             for no user-visible gain. Label and identifier are allowed to differ here. --%>
        <c:if test="${showPublishTab}"><button type="button" class="my-work-stage-tab ${activeStageTab == 'dashboard' ? 'active' : ''}" data-tab="dashboard">Planning</button></c:if>
        <c:if test="${showShootTab}"><button type="button" class="my-work-stage-tab ${activeStageTab == 'shoot' ? 'active' : ''}" data-tab="shoot">Shoot</button></c:if>
        <c:if test="${showEditTab}"><button type="button" class="my-work-stage-tab ${activeStageTab == 'edit' ? 'active' : ''}" data-tab="edit">Edit</button></c:if>
        <c:if test="${showPublishTab}"><button type="button" class="my-work-stage-tab ${activeStageTab == 'publish' ? 'active' : ''}" data-tab="publish">Publishing</button></c:if>
    </div>

    <%-- Zero-authorized-tabs edge case: an employee with no execution permission and no
         current/history data in any stage would otherwise see an empty tab bar and nothing else -
         a plain informational message instead, reusing the existing note-box convention (see the
         same class used at the foot of each stage panel below). --%>
    <c:if test="${!showShootTab && !showEditTab && !showPublishTab}">
        <p class="note-box">You have no assigned work yet. Once you are assigned to a Shoot, Edit,
            or Publishing task, it will appear here.</p>
    </c:if>

    <%-- ================================================================ DASHBOARD (Publisher upcoming-work) === --%>
    <c:if test="${showPublishTab}">
    <div class="my-work-stage-panel hidden" data-tab-panel="dashboard">
        <div class="kpi-cards">
            <div class="kpi-card kpi-underreview">
                <div class="kpi-card-icon">&#128197;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Upcoming</span><span class="kpi-card-count">${upcomingPublishingCount}</span></div>
                    <div class="kpi-card-subtitle">Assigned, not yet in Publishing</div>
                </div>
            </div>
            <div class="kpi-card kpi-active">
                <div class="kpi-card-icon">&#128228;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Active Publishing</span><span class="kpi-card-count">${activePublishingCount}</span></div>
                    <div class="kpi-card-subtitle">Tasks assigned to you</div>
                </div>
            </div>
            <div class="kpi-card kpi-delayed">
                <div class="kpi-card-icon">&#9201;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Delayed</span><span class="kpi-card-count">${delayedPublishingCount}</span></div>
                    <div class="kpi-card-subtitle">Past planned live date</div>
                </div>
            </div>
            <div class="kpi-card kpi-completed">
                <div class="kpi-card-icon">&#9989;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Completed</span><span class="kpi-card-count">${publishCompletedCount}</span></div>
                    <div class="kpi-card-subtitle">Publishing completed</div>
                </div>
            </div>
        </div>
        <%-- Filter panel above Upcoming Tasks. Shared with the Publishing tab's own copy below
             (fragments/publish-filters.jspf) - both drive the same liveDate/channel/platform
             parameters, which the controller applies to both Publishing lists in one pass. --%>
        <c:set var="filterPanelId" value="publishDashboardFilter"/>
        <c:set var="filterTab" value="dashboard"/>
        <%-- This copy's own scope: the dash* parameters, computed from Upcoming Tasks only. --%>
        <c:set var="fPrefix" value="dash"/>
        <c:set var="fDateParam" value="${dashDateParam}"/>
        <c:set var="fChannelParam" value="${dashChannelParam}"/>
        <c:set var="fPlatformParams" value="${dashPlatformParams}"/>
        <c:set var="fChannelOptions" value="${dashChannelOptions}"/>
        <c:set var="fPlatformOptions" value="${dashPlatformOptions}"/>
        <c:set var="fTodayCount" value="${dashTodayCount}"/>
        <c:set var="fTomorrowCount" value="${dashTomorrowCount}"/>
        <c:set var="fTodayQs" value="${dashTodayQs}"/>
        <c:set var="fTomorrowQs" value="${dashTomorrowQs}"/>
        <c:set var="fTodaySelected" value="${dashTodaySelected}"/>
        <c:set var="fTomorrowSelected" value="${dashTomorrowSelected}"/>
        <c:set var="fCustomDateSelected" value="${dashCustomDateSelected}"/>
        <c:set var="fClearQs" value="${dashClearQs}"/>
        <%-- The Publishing tab's filter, re-submitted untouched so filtering here never clears it. --%>
        <c:set var="fOtherPrefix" value="pub"/>
        <c:set var="fOtherDate" value="${pubDateParam}"/>
        <c:set var="fOtherChannel" value="${pubChannelParam}"/>
        <c:set var="fOtherPlatforms" value="${pubPlatformParams}"/>
        <%@ include file="fragments/publish-filters.jspf" %>
        <div class="panel my-work-table-wrapper">
            <%-- Badge shows the number of rows actually rendered (the filtered list), never the
                 unfiltered KPI-card total - otherwise a filtered table would contradict its own
                 header. The KPI cards above intentionally keep showing whole-workload totals. --%>
            <h2>Upcoming Tasks <span class="count-badge">${fn:length(upcomingPublishWork)}</span></h2>
            <p class="muted">Tasks assigned to you but not yet in Publishing stage.<c:if test="${dashFilterActive}"> Showing ${fn:length(upcomingPublishWork)} of ${upcomingPublishingCount} matching this tab's filters.</c:if></p>
            <table class="data-table">
                <thead>
                <tr>
                    <th>Stage</th><th>Content ID</th><th>Content / Task</th><th>Priority</th>
                    <th>Planned Date</th><th>Platforms</th><th>Action</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="item" items="${upcomingPublishWork}">
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${item.currentStage == 'Shoot'}"><span class="stage-badge stage-shoot">SHOOT</span></c:when>
                                <c:when test="${item.currentStage == 'Edit'}"><span class="stage-badge stage-edit">EDIT</span></c:when>
                                <c:otherwise><span class="stage-badge stage-publish"><c:out value="${item.currentStage}"/></span></c:otherwise>
                            </c:choose>
                        </td>
                        <td><a class="content-id-link" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}">${item.contentId}</a></td>
                        <td><c:out value="${item.title}"/></td>
                        <td>
                            <c:if test="${not empty item.priority}">
                                <span class="priority-pill ${item.priorityCssClass}"><c:out value="${item.priority}"/></span>
                            </c:if>
                        </td>
                        <td class="${item.delayed ? 'planned-date-delayed' : ''}">${item.plannedDate}</td>
                        <%-- Platforms: same icon+count chip UI as Content Pipeline/Content Detail
                             (fragments/pipeline-platform-chip.jspf), same ${item.platformSummaries}
                             data shape (PipelinePlatformSummary) built by the shared
                             PipelineDashboardService#buildPlatformSummariesForPlan - never a second
                             rendering/data implementation. --%>
                        <td class="pipeline-col-wrap">
                            <div class="pipeline-platform-chips">
                                <c:forEach var="summary" items="${item.platformSummaries}" varStatus="ps">
                                    <c:set var="popoverId" value="upcoming-platform-popover-${item.contentPlanId}-${ps.index}"/>
                                    <%@ include file="fragments/pipeline-platform-chip.jspf" %>
                                </c:forEach>
                                <c:if test="${empty item.platformSummaries}"><span class="pipeline-team-empty">—</span></c:if>
                            </div>
                        </td>
                        <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}">View Details</a></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty upcomingPublishWork}">
                    <%-- Distinguishes "you genuinely have none" from "your filters excluded them
                         all", so a zero-row table is never mistaken for lost work. --%>
                    <tr><td colspan="7" class="muted">
                        <c:choose>
                            <c:when test="${dashFilterActive and upcomingPublishingCount > 0}">
                                No upcoming publishing tasks match this tab's filters (${upcomingPublishingCount} total).
                            </c:when>
                            <c:otherwise>No upcoming publishing tasks.</c:otherwise>
                        </c:choose>
                    </td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
    </c:if>

    <%-- ================================================================ SHOOT ============== --%>
    <c:if test="${showShootTab}">
    <div class="my-work-stage-panel hidden" data-tab-panel="shoot">
        <div class="kpi-cards">
            <div class="kpi-card kpi-active">
                <div class="kpi-card-icon">&#128247;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Active Shoots</span><span class="kpi-card-count">${activeShootsCount}</span></div>
                    <div class="kpi-card-subtitle">Tasks assigned to you</div>
                </div>
            </div>
            <div class="kpi-card kpi-rework">
                <div class="kpi-card-icon">&#128260;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Rework Required</span><span class="kpi-card-count">${reworkRequiredCount}</span></div>
                    <div class="kpi-card-subtitle">Needs your updates</div>
                </div>
            </div>
            <div class="kpi-card kpi-delayed">
                <div class="kpi-card-icon">&#9201;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Delayed</span><span class="kpi-card-count">${delayedShootsCount}</span></div>
                    <div class="kpi-card-subtitle">Past planned shoot date</div>
                </div>
            </div>
            <div class="kpi-card kpi-completed">
                <div class="kpi-card-icon">&#9989;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Completed</span><span class="kpi-card-count">${completedShootsCount}</span></div>
                    <div class="kpi-card-subtitle">Shoots completed</div>
                </div>
            </div>
        </div>
        <c:if test="${!hasShootExecutionPermission}">
            <p class="execution-blocked-note">You do not currently hold Shoot execution permission - any task below is historical or awaiting reassignment.</p>
        </c:if>

            <div class="panel my-work-table-wrapper">
                <h2><span class="stage-badge stage-shoot">SHOOT</span>Active Shoot Tasks</h2>
                <table class="data-table">
                    <thead>
                    <tr>
                        <th>Content ID</th><th>Idea / Content</th><th>Priority</th><th>Shoot Date</th>
                        <th>Model(s)</th><th>Shoot Lead</th><th>Status</th><th>Drive</th><th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="item" items="${shootActiveWork}">
                        <tr>
                            <td><a class="content-id-link" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}">${item.contentId}</a></td>
                            <td><c:out value="${item.title}"/></td>
                            <td>
                                <c:if test="${not empty item.priority}">
                                    <span class="priority-pill ${item.priorityCssClass}"><c:out value="${item.priority}"/></span>
                                </c:if>
                            </td>
                            <td class="${item.delayed ? 'planned-date-delayed' : ''}">${item.plannedDate}</td>
                            <td><c:out value="${empty item.models ? '—' : item.models}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty item.leadName}">
                                        <c:out value="${item.leadName}"/><c:if test="${item.shootLead}"> <span class="lead-badge">Lead</span></c:if>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${item.delayed}"><span class="status-pill status-delayed">Delayed &middot; ${item.delayDays} day<c:if test="${item.delayDays != 1}">s</c:if></span></c:when>
                                    <c:otherwise><span class="status-pill ${item.statusCssClass}"><c:out value="${item.statusLabel}"/></span></c:otherwise>
                                </c:choose>
                                <c:if test="${item.onHold}"><span class="status-pill status-onhold">On Hold</span></c:if>
                            </td>
                            <td>
                                <c:if test="${not empty item.driveLink}">
                                    <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer" title="Open Drive">&#128193;</a>
                                </c:if>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${item.executionBlocked}">
                                        <span class="execution-blocked-note">Execution permission removed. This task requires reassignment or permission restoration.</span>
                                    </c:when>
                                    <c:when test="${not empty item.actionLabel}">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}"><c:out value="${item.actionLabel}"/></a>
                                    </c:when>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty shootActiveWork}">
                        <tr><td colspan="9" class="muted">No active shoot tasks.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>

        <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.
            Looking for your completed work? See <a href="${pageContext.request.contextPath}/app/my-performance">My Performance</a>.</p>
    </div>
    </c:if>

    <%-- ================================================================ EDIT =============== --%>
    <c:if test="${showEditTab}">
    <div class="my-work-stage-panel hidden" data-tab-panel="edit">
        <div class="kpi-cards">
            <div class="kpi-card kpi-active">
                <div class="kpi-card-icon">&#127916;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Active Edits</span><span class="kpi-card-count">${activeEditsCount}</span></div>
                    <div class="kpi-card-subtitle">Tasks assigned to you</div>
                </div>
            </div>
            <div class="kpi-card kpi-rework">
                <div class="kpi-card-icon">&#128260;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Rework Required</span><span class="kpi-card-count">${editReworkRequiredCount}</span></div>
                    <div class="kpi-card-subtitle">Needs your updates</div>
                </div>
            </div>
            <div class="kpi-card kpi-delayed">
                <div class="kpi-card-icon">&#9201;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Delayed</span><span class="kpi-card-count">${editDelayedCount}</span></div>
                    <div class="kpi-card-subtitle">Past planned edit date</div>
                </div>
            </div>
            <div class="kpi-card kpi-completed">
                <div class="kpi-card-icon">&#9989;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Completed</span><span class="kpi-card-count">${editCompletedCount}</span></div>
                    <div class="kpi-card-subtitle">Edits completed</div>
                </div>
            </div>
        </div>
        <c:if test="${!hasEditExecutionPermission}">
            <p class="execution-blocked-note">You do not currently hold Edit execution permission - any task below is historical or awaiting reassignment.</p>
        </c:if>

            <div class="panel my-work-table-wrapper">
                <h2><span class="stage-badge stage-edit">EDIT</span>Active Edit Tasks</h2>
                <table class="data-table">
                    <thead>
                    <tr>
                        <th>Content ID</th><th>Idea / Content</th><th>Priority</th><th>Edit Date</th>
                        <th>Editor(s)</th><th>Edit Lead</th><th>Status</th><th>Drive</th><th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="item" items="${editActiveWork}">
                        <tr>
                            <td><a class="content-id-link" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}">${item.contentId}</a></td>
                            <td><c:out value="${item.title}"/></td>
                            <td>
                                <c:if test="${not empty item.priority}">
                                    <span class="priority-pill ${item.priorityCssClass}"><c:out value="${item.priority}"/></span>
                                </c:if>
                            </td>
                            <td class="${item.delayed ? 'planned-date-delayed' : ''}">${item.plannedDate}</td>
                            <td><c:out value="${empty item.models ? '—' : item.models}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty item.leadName}">
                                        <c:out value="${item.leadName}"/><c:if test="${item.shootLead}"> <span class="lead-badge">Lead</span></c:if>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${item.delayed}"><span class="status-pill status-delayed">Delayed &middot; ${item.delayDays} day<c:if test="${item.delayDays != 1}">s</c:if></span></c:when>
                                    <c:otherwise><span class="status-pill ${item.statusCssClass}"><c:out value="${item.statusLabel}"/></span></c:otherwise>
                                </c:choose>
                                <c:if test="${item.onHold}"><span class="status-pill status-onhold">On Hold</span></c:if>
                            </td>
                            <td>
                                <c:if test="${not empty item.driveLink}">
                                    <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer" title="Open Drive">&#128193;</a>
                                </c:if>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${item.executionBlocked}">
                                        <span class="execution-blocked-note">Execution permission removed. This task requires reassignment or permission restoration.</span>
                                    </c:when>
                                    <c:when test="${not empty item.actionLabel}">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}"><c:out value="${item.actionLabel}"/></a>
                                    </c:when>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty editActiveWork}">
                        <tr><td colspan="9" class="muted">No active edit tasks.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>

        <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.
            Looking for your completed work? See <a href="${pageContext.request.contextPath}/app/my-performance">My Performance</a>.</p>
    </div>
    </c:if>

    <%-- ================================================================ PUBLISHING ========= --%>
    <c:if test="${showPublishTab}">
    <div class="my-work-stage-panel hidden" data-tab-panel="publish">
        <%-- ENG-098: Upcoming Publishing moved to its own top-level "Dashboard" tab (single source
             of truth for Upcoming, never duplicated here too) - this panel goes back to its
             original Active Work/History shape, unchanged from before ENG-097. --%>
        <div class="kpi-cards">
            <div class="kpi-card kpi-active">
                <div class="kpi-card-icon">&#128228;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Active Publishing</span><span class="kpi-card-count">${activePublishingCount}</span></div>
                    <div class="kpi-card-subtitle">Tasks assigned to you</div>
                </div>
            </div>
            <div class="kpi-card kpi-rework">
                <div class="kpi-card-icon">&#127919;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Pending Targets</span><span class="kpi-card-count">${pendingTargetsCount}</span></div>
                    <div class="kpi-card-subtitle">Targets still to publish</div>
                </div>
            </div>
            <div class="kpi-card kpi-delayed">
                <div class="kpi-card-icon">&#9201;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Delayed</span><span class="kpi-card-count">${delayedPublishingCount}</span></div>
                    <div class="kpi-card-subtitle">Past planned live date</div>
                </div>
            </div>
            <div class="kpi-card kpi-completed">
                <div class="kpi-card-icon">&#9989;</div>
                <div class="kpi-card-body">
                    <div class="kpi-card-title-row"><span class="kpi-card-title">Completed</span><span class="kpi-card-count">${publishCompletedCount}</span></div>
                    <div class="kpi-card-subtitle">Publishing completed</div>
                </div>
            </div>
        </div>
        <c:if test="${!hasPublishingExecutionPermission}">
            <p class="execution-blocked-note">You do not currently hold Publishing execution permission - any task below is historical or awaiting reassignment.</p>
        </c:if>

            <%-- Same filter panel as the Dashboard tab, rendered above this tab's own table so a
                 Publisher working here does not have to switch tabs to filter. Both copies drive
                 the identical liveDate/channel/platform parameters, applied server-side to both
                 Publishing lists in one pass - so whichever panel is used, both tables agree.
                 filterPanelId differs from the Dashboard copy's to keep DOM ids unique. --%>
            <c:set var="filterPanelId" value="publishTabFilter"/>
            <c:set var="filterTab" value="publish"/>
            <%-- This copy's own scope: the pub* parameters, computed from Active Publishing Tasks
                 only - never the Dashboard's Upcoming numbers. --%>
            <c:set var="fPrefix" value="pub"/>
            <c:set var="fDateParam" value="${pubDateParam}"/>
            <c:set var="fChannelParam" value="${pubChannelParam}"/>
            <c:set var="fPlatformParams" value="${pubPlatformParams}"/>
            <c:set var="fChannelOptions" value="${pubChannelOptions}"/>
            <c:set var="fPlatformOptions" value="${pubPlatformOptions}"/>
            <c:set var="fTodayCount" value="${pubTodayCount}"/>
            <c:set var="fTomorrowCount" value="${pubTomorrowCount}"/>
            <c:set var="fTodayQs" value="${pubTodayQs}"/>
            <c:set var="fTomorrowQs" value="${pubTomorrowQs}"/>
            <c:set var="fTodaySelected" value="${pubTodaySelected}"/>
            <c:set var="fTomorrowSelected" value="${pubTomorrowSelected}"/>
            <c:set var="fCustomDateSelected" value="${pubCustomDateSelected}"/>
            <c:set var="fClearQs" value="${pubClearQs}"/>
            <%-- The Dashboard tab's filter, re-submitted untouched. --%>
            <c:set var="fOtherPrefix" value="dash"/>
            <c:set var="fOtherDate" value="${dashDateParam}"/>
            <c:set var="fOtherChannel" value="${dashChannelParam}"/>
            <c:set var="fOtherPlatforms" value="${dashPlatformParams}"/>
            <%@ include file="fragments/publish-filters.jspf" %>
            <div class="panel my-work-table-wrapper">
                <h2><span class="stage-badge stage-publish">PUBLISHING</span>Active Publishing Tasks</h2>
                <%-- This tab's OWN filter (the pub* parameters, independent of the Dashboard's),
                     spelled out so rows missing from this table are never mistaken for lost work. --%>
                <c:if test="${pubFilterActive}">
                    <p class="muted">Showing ${fn:length(publishActiveWork)} of ${activePublishingCount} &mdash; this tab's filters applied.
                        <a href="<c:url value='/app/my-work'/><c:out value='${pubClearQs}'/>tab=publish">Clear filters</a></p>
                </c:if>
                <table class="data-table">
                    <thead>
                    <tr>
                        <th>Content ID</th><th>Content</th><th>Priority</th><th>Planned Live Date</th>
                        <th>Platforms</th><th>Status</th><th>Drive</th><th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="item" items="${publishActiveWork}">
                        <tr>
                            <td><a class="content-id-link" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}">${item.contentId}</a></td>
                            <td><c:out value="${item.title}"/></td>
                            <td>
                                <c:if test="${not empty item.priority}">
                                    <span class="priority-pill ${item.priorityCssClass}"><c:out value="${item.priority}"/></span>
                                </c:if>
                            </td>
                            <td class="${item.delayed ? 'planned-date-delayed' : ''}">${item.plannedDate}</td>
                            <%-- Platforms: the SAME icon+count chip UI and the SAME
                                 PipelinePlatformSummary data as the Dashboard tab's Upcoming Tasks
                                 column - one shared fragment (fragments/pipeline-platform-chip.jspf)
                                 and one shared builder
                                 (PipelineDashboardService#buildPlatformSummariesForPlan), never a
                                 second rendering or data implementation. Upcoming rows carry the
                                 summaries on the DTO itself; Active rows look them up by Content
                                 Plan id from publishPlatformsByPlan, because ActiveWorkItem is
                                 shared with the Shoot/Edit rows, which have no platform concept.
                                 Chip -> popover behaviour comes from the shared
                                 platform-chip-popover.js. That module's chip-click listener is
                                 delegated PER CONTAINER (only its outside-click/Escape handlers
                                 are page-global), so this panel must be passed to wireClicks()
                                 explicitly - my-work-dashboard.js wires both this panel and the
                                 Dashboard one. Without that call these chips render correctly and
                                 do nothing when clicked. --%>
                            <td class="pipeline-col-wrap">
                                <c:set var="activePlatformSummaries" value="${publishPlatformsByPlan[item.contentPlanId]}"/>
                                <div class="pipeline-platform-chips">
                                    <c:forEach var="summary" items="${activePlatformSummaries}" varStatus="ps">
                                        <%-- Distinct id prefix from the Upcoming table's chips: both
                                             tables render on the same page, so a shared prefix would
                                             produce duplicate popover ids. --%>
                                        <c:set var="popoverId" value="active-platform-popover-${item.contentPlanId}-${ps.index}"/>
                                        <%@ include file="fragments/pipeline-platform-chip.jspf" %>
                                    </c:forEach>
                                    <c:if test="${empty activePlatformSummaries}"><span class="pipeline-team-empty">—</span></c:if>
                                </div>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${item.repost}"><span class="stage-badge stage-repost">REPOST</span></c:when>
                                </c:choose>
                                <c:choose>
                                    <c:when test="${item.delayed}"><span class="status-pill status-delayed">Delayed &middot; ${item.delayDays} day<c:if test="${item.delayDays != 1}">s</c:if></span></c:when>
                                    <c:otherwise><span class="status-pill ${item.statusCssClass}"><c:out value="${item.statusLabel}"/></span></c:otherwise>
                                </c:choose>
                                <c:if test="${item.onHold}"><span class="status-pill status-onhold">On Hold</span></c:if>
                            </td>
                            <td>
                                <c:if test="${not empty item.driveLink}">
                                    <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer" title="Open Drive">&#128193;</a>
                                </c:if>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${item.executionBlocked}">
                                        <span class="execution-blocked-note">Execution permission removed. This task requires reassignment or permission restoration.</span>
                                    </c:when>
                                    <c:when test="${not empty item.actionLabel}">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}"><c:out value="${item.actionLabel}"/></a>
                                    </c:when>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty publishActiveWork}">
                        <%-- colspan 8: Publisher(s) and Targets were replaced by a single
                             Platforms column, so this table is one column narrower than the
                             Shoot/Edit tables above. --%>
                        <tr><td colspan="8" class="muted">
                            <c:choose>
                                <c:when test="${pubFilterActive and activePublishingCount > 0}">
                                    No active publishing tasks match this tab's filters (${activePublishingCount} total).
                                </c:when>
                                <c:otherwise>No active publishing tasks.</c:otherwise>
                            </c:choose>
                        </td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>

        <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.
            Looking for your completed work? See <a href="${pageContext.request.contextPath}/app/my-performance">My Performance</a>.</p>
    </div>
    </c:if>

    <c:if test="${showAssignmentManagementTier}">
    </div> <%-- /execution mode-panel --%>

    <div class="my-work-mode-panel hidden" data-tab-panel="assignment-mgmt">
        <div class="my-work-stage-tabs">
            <button type="button" class="assignment-mgmt-tab active" data-tab="assign-shoot">Shoot</button>
            <button type="button" class="assignment-mgmt-tab" data-tab="assign-edit">Edit</button>
        </div>

        <div class="assignment-mgmt-panel" data-tab-panel="assign-shoot">
            <div class="panel my-work-table-wrapper">
                <h2>Shoot Assignment Management <span class="count-badge">${shootAssignmentQueue.size()}</span></h2>
                <p class="muted">Content IDs where you can currently set up or change the Shoot team. Historical or
                    no-longer-actionable items are not shown here.</p>
                <table class="data-table">
                    <thead><tr><th>Content ID</th><th>Content</th><th>Current Status</th><th>Shoot Date</th>
                        <th>Current Assignees</th><th>Lead</th><th>Action</th></tr></thead>
                    <tbody>
                    <c:forEach var="q" items="${shootAssignmentQueue}">
                        <tr>
                            <td><c:out value="${q.contentId}"/></td>
                            <td><c:out value="${q.title}"/></td>
                            <td>${q.status.statusName}</td>
                            <td>${empty q.relevantDate ? '—' : q.relevantDate}</td>
                            <td><c:forEach var="n" items="${q.currentAssigneeNames}" varStatus="s"><c:out value="${n}"/><c:if test="${!s.last}">, </c:if></c:forEach>
                                <c:if test="${empty q.currentAssigneeNames}">&mdash;</c:if></td>
                            <td>${empty q.leadName ? '—' : q.leadName}</td>
                            <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${q.planId}?tab=shoot"><c:out value="${q.actionLabel}"/></a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty shootAssignmentQueue}">
                        <tr><td colspan="7" class="muted">Nothing needs Shoot assignment action from you right now.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </div>

        <div class="assignment-mgmt-panel hidden" data-tab-panel="assign-edit">
            <div class="panel my-work-table-wrapper">
                <h2>Edit Assignment Management <span class="count-badge">${editAssignmentQueue.size()}</span></h2>
                <p class="muted">Content IDs where you can currently set up or change the Edit team. Historical or
                    no-longer-actionable items are not shown here.</p>
                <table class="data-table">
                    <thead><tr><th>Content ID</th><th>Content</th><th>Current Status</th><th>Edit Date</th>
                        <th>Current Assignees</th><th>Lead</th><th>Action</th></tr></thead>
                    <tbody>
                    <c:forEach var="q" items="${editAssignmentQueue}">
                        <tr>
                            <td><c:out value="${q.contentId}"/></td>
                            <td><c:out value="${q.title}"/></td>
                            <td>${q.status.statusName}</td>
                            <td>${empty q.relevantDate ? '—' : q.relevantDate}</td>
                            <td><c:forEach var="n" items="${q.currentAssigneeNames}" varStatus="s"><c:out value="${n}"/><c:if test="${!s.last}">, </c:if></c:forEach>
                                <c:if test="${empty q.currentAssigneeNames}">&mdash;</c:if></td>
                            <td>${empty q.leadName ? '—' : q.leadName}</td>
                            <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${q.planId}?tab=edit"><c:out value="${q.actionLabel}"/></a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty editAssignmentQueue}">
                        <tr><td colspan="7" class="muted">Nothing needs Edit assignment action from you right now.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </div>
    </div> <%-- /assignment-mgmt mode-panel --%>
    </c:if>
</main>
<script src="<c:url value='/js/my-work-tabs.js'/>" defer></script>
<script src="<c:url value='/js/platform-chip-popover.js'/>" defer></script>
<script src="<c:url value='/js/my-work-dashboard.js'/>" defer></script>
</body>
</html>
