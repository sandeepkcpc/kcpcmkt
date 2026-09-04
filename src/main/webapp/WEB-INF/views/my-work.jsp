<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — My Work</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <link rel="icon" type="image/x-icon" href="${pageContext.request.contextPath}/images/favicon.ico">
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
         never hidden for one they can). None of these buttons carries a hardcoded "active" class -
         my-work-tabs.js falls back to the first tab present in the DOM when none is pre-marked
         active, and since every button here is itself conditionally rendered, "first in the DOM"
         is always this employee's own first authorized tab (Dashboard > Shoot > Edit > Publishing
         priority, matching this fixed button order) - never a removed "All" tab. Task-specific
         stage detail screens (Shoot/Edit/Publishing Task Detail at /app/deliverables/{id}) are
         unchanged by this. --%>
    <div class="my-work-stage-tabs">
        <%-- ENG-098: Publisher-only upcoming-work dashboard - same gate as the Publishing tab
             itself (${showPublishTab}) since it's built entirely from that same Publisher data. --%>
        <c:if test="${showPublishTab}"><button type="button" class="my-work-stage-tab" data-tab="dashboard">Dashboard</button></c:if>
        <c:if test="${showShootTab}"><button type="button" class="my-work-stage-tab" data-tab="shoot">Shoot</button></c:if>
        <c:if test="${showEditTab}"><button type="button" class="my-work-stage-tab" data-tab="edit">Edit</button></c:if>
        <c:if test="${showPublishTab}"><button type="button" class="my-work-stage-tab" data-tab="publish">Publishing</button></c:if>
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
        <div class="panel my-work-table-wrapper">
            <h2>Upcoming Tasks <span class="count-badge">${upcomingPublishingCount}</span></h2>
            <p class="muted">Tasks assigned to you but not yet in Publishing stage.</p>
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
                    <tr><td colspan="7" class="muted">No upcoming publishing tasks.</td></tr>
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

            <div class="panel my-work-table-wrapper">
                <h2><span class="stage-badge stage-publish">PUBLISHING</span>Active Publishing Tasks</h2>
                <table class="data-table">
                    <thead>
                    <tr>
                        <th>Content ID</th><th>Content</th><th>Priority</th><th>Planned Live Date</th>
                        <th>Publisher(s)</th><th>Targets</th><th>Status</th><th>Drive</th><th>Action</th>
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
                            <td><c:out value="${empty item.models ? '—' : item.models}"/></td>
                            <td><c:out value="${empty item.targetsSummary ? '—' : item.targetsSummary}"/></td>
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
                        <tr><td colspan="9" class="muted">No active publishing tasks.</td></tr>
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
<script src="${pageContext.request.contextPath}/js/my-work-tabs.js" defer></script>
<script src="${pageContext.request.contextPath}/js/platform-chip-popover.js" defer></script>
<script src="${pageContext.request.contextPath}/js/my-work-dashboard.js" defer></script>
</body>
</html>
