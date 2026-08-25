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

    <%-- Stage tabs: All is always present; Shoot/Edit/Publishing each render only when this
         employee holds the matching live execution permission (PERM_18/19/08) OR has real
         current/history assignment data for that stage (${showShootTab}/${showEditTab}/
         ${showPublishTab}, computed once in LandingMvcController#myWork - the same source the
         backend execution checks use, so a tab is never shown for a stage the employee cannot
         actually act in, and never hidden for one they can). Task-specific stage detail screens
         (Shoot/Edit/Publishing Task Detail at /app/deliverables/{id}) are unchanged by this. --%>
    <div class="my-work-stage-tabs">
        <button type="button" class="my-work-stage-tab active" data-tab="all">All</button>
        <c:if test="${showShootTab}"><button type="button" class="my-work-stage-tab" data-tab="shoot">Shoot</button></c:if>
        <c:if test="${showEditTab}"><button type="button" class="my-work-stage-tab" data-tab="edit">Edit</button></c:if>
        <c:if test="${showPublishTab}"><button type="button" class="my-work-stage-tab" data-tab="publish">Publishing</button></c:if>
    </div>

    <%-- ================================================================ ALL ================ --%>
    <div class="my-work-stage-panel" data-tab-panel="all">
        <div class="panel my-work-table-wrapper">
            <h2>Active Work <span class="count-badge">${activeWork.size()}</span></h2>
            <table class="data-table">
                <thead>
                <tr>
                    <th>Stage</th>
                    <th>Content ID</th>
                    <th>Content / Task</th>
                    <th>Priority</th>
                    <th>Planned Date</th>
                    <th>Lead</th>
                    <th>Status</th>
                    <th>Drive Link</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="item" items="${activeWork}">
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${item.stage == 'PUBLISH' && item.repost}"><span class="stage-badge stage-repost">REPOST</span></c:when>
                                <c:when test="${item.stage == 'SHOOT'}"><span class="stage-badge stage-shoot">SHOOT</span></c:when>
                                <c:when test="${item.stage == 'EDIT'}"><span class="stage-badge stage-edit">EDIT</span></c:when>
                                <c:otherwise><span class="stage-badge stage-publish">PUBLISHING</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>${item.contentId}</td>
                        <td><c:out value="${item.title}"/></td>
                        <td>
                            <c:if test="${not empty item.priority}">
                                <span class="priority-pill ${item.priorityCssClass}"><c:out value="${item.priority}"/></span>
                            </c:if>
                        </td>
                        <td>${item.plannedDate}</td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty item.leadName}">
                                    <c:out value="${item.leadName}"/><c:if test="${item.shootLead}"> <span class="lead-badge">Lead</span></c:if>
                                </c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </td>
                        <td><span class="status-pill ${item.statusCssClass}"><c:out value="${item.statusLabel}"/></span>
                            <c:if test="${item.onHold}"><span class="status-pill status-onhold">On Hold</span></c:if>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty item.driveLink}">
                                    <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer">Open Drive &#8599;</a>
                                </c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <%-- Permission revoked after assignment: task stays visible, execution
                                 suppressed, clear message instead - never silently hidden/deleted. --%>
                            <c:choose>
                                <c:when test="${item.executionBlocked}">
                                    <span class="execution-blocked-note">Execution permission removed. This task requires reassignment or permission restoration.</span>
                                </c:when>
                                <c:otherwise>
                                    <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}">View Details</a>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty activeWork}">
                    <tr><td colspan="9" class="muted">No active work.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>

        <div class="panel my-work-table-wrapper">
            <h2>Completed Work <span class="count-badge">${completedWork.size()}</span></h2>
            <table class="data-table">
                <thead>
                <tr>
                    <th>Stage</th>
                    <th>Content ID</th>
                    <th>Content / Task</th>
                    <th>Completed On</th>
                    <th>Result</th>
                    <th>Remarks</th>
                    <th>View</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="w" items="${completedWork}">
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${w.stageWorked == 'SHOOT'}"><span class="stage-badge stage-shoot">SHOOT</span></c:when>
                                <c:when test="${w.stageWorked == 'EDIT'}"><span class="stage-badge stage-edit">EDIT</span></c:when>
                                <c:otherwise><span class="stage-badge stage-publish">PUBLISHING</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>${w.contentId}</td>
                        <td><c:out value="${w.title}"/></td>
                        <td><c:if test="${not empty w.completedOn}">${kcpc:ist(w.completedOn)}</c:if></td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty w.finalResult}">
                                    <span class="status-pill ${w.finalResult == 'Approved' ? 'status-completed' : 'status-needschanges'}"><c:out value="${w.finalResult}"/></span>
                                </c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </td>
                        <td><c:out value="${empty w.remarks ? '—' : w.remarks}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${w.stageWorked == 'SHOOT'}">
                                    <a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work/history/shoot/${w.assignmentId}">View Details</a>
                                </c:when>
                                <c:when test="${w.stageWorked == 'EDIT'}">
                                    <a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work/history/edit/${w.assignmentId}">View Details</a>
                                </c:when>
                                <c:otherwise>
                                    <a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work/history/publish/${w.assignmentId}">View Details</a>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty completedWork}">
                    <tr><td colspan="7" class="muted">No completed work yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>

        <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.</p>

        <div class="panel">
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
        </div>
    </div>

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

        <div class="my-work-tabs">
            <button type="button" class="my-work-tab active" data-tab="shoot-active">Active Work</button>
            <button type="button" class="my-work-tab" data-tab="shoot-history">History</button>
            <button type="button" class="my-work-tab" data-tab="shoot-marks">Marks</button>
        </div>

        <div class="my-work-tab-panel" data-tab-panel="shoot-active">
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
                            <td>${item.plannedDate}</td>
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
        </div>

        <div class="my-work-tab-panel hidden" data-tab-panel="shoot-history">
            <div class="panel my-work-table-wrapper">
                <h2>Completed Shoot Work</h2>
                <table class="data-table">
                    <thead>
                    <tr><th>Content ID</th><th>Idea / Content</th><th>Shoot Date</th><th>Model(s)</th>
                        <th>Completed On</th><th>Result</th><th>Remarks</th><th>View</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="w" items="${shootCompletedWork}">
                        <tr>
                            <td>${w.contentId}</td>
                            <td><c:out value="${w.title}"/></td>
                            <td>${w.stageDate}</td>
                            <td><c:out value="${empty w.models ? '—' : w.models}"/></td>
                            <td><c:if test="${not empty w.completedOn}">${kcpc:ist(w.completedOn)}</c:if></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty w.finalResult}">
                                        <span class="status-pill ${w.finalResult == 'Approved' ? 'status-completed' : 'status-needschanges'}"><c:out value="${w.finalResult}"/></span>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${empty w.remarks ? '—' : w.remarks}"/></td>
                            <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work/history/shoot/${w.assignmentId}">View Details</a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty shootCompletedWork}">
                        <tr><td colspan="8" class="muted">No completed shoot work yet.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </div>

        <div class="my-work-tab-panel hidden" data-tab-panel="shoot-marks">
            <div class="panel my-work-table-wrapper">
                <h2>My Shoot Marks</h2>
                <table class="data-table">
                    <thead><tr><th>Content ID</th><th>Role</th><th>Mark</th><th>Attributed (IST)</th></tr></thead>
                    <tbody>
                    <c:forEach var="m" items="${shootMarks}">
                        <tr>
                            <td>${m.contentPlan.contentId}</td>
                            <td>${m.roleType}</td>
                            <td>${m.attributedMarkValue}</td>
                            <td>${kcpc:ist(m.attributedAt)}</td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty shootMarks}"><tr><td colspan="4" class="muted">No Shoot marks attributed yet.</td></tr></c:if>
                    </tbody>
                </table>
            </div>
        </div>

        <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.</p>
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

        <div class="my-work-tabs">
            <button type="button" class="my-work-tab active" data-tab="edit-active">Active Work</button>
            <button type="button" class="my-work-tab" data-tab="edit-history">History</button>
            <button type="button" class="my-work-tab" data-tab="edit-marks">Marks</button>
        </div>

        <div class="my-work-tab-panel" data-tab-panel="edit-active">
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
                            <td>${item.plannedDate}</td>
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
        </div>

        <div class="my-work-tab-panel hidden" data-tab-panel="edit-history">
            <div class="panel my-work-table-wrapper">
                <h2>Completed Edit Work</h2>
                <table class="data-table">
                    <thead>
                    <tr><th>Content ID</th><th>Content</th><th>Edit Date</th><th>Completed On</th>
                        <th>Result</th><th>Remarks</th><th>View</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="w" items="${editCompletedWork}">
                        <tr>
                            <td>${w.contentId}</td>
                            <td><c:out value="${w.title}"/></td>
                            <td>${w.stageDate}</td>
                            <td><c:if test="${not empty w.completedOn}">${kcpc:ist(w.completedOn)}</c:if></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty w.finalResult}">
                                        <span class="status-pill ${w.finalResult == 'Approved' ? 'status-completed' : 'status-needschanges'}"><c:out value="${w.finalResult}"/></span>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${empty w.remarks ? '—' : w.remarks}"/></td>
                            <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work/history/edit/${w.assignmentId}">View Details</a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty editCompletedWork}">
                        <tr><td colspan="7" class="muted">No completed edit work yet.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </div>

        <div class="my-work-tab-panel hidden" data-tab-panel="edit-marks">
            <div class="panel my-work-table-wrapper">
                <h2>My Edit Marks</h2>
                <table class="data-table">
                    <thead><tr><th>Content ID</th><th>Role</th><th>Mark</th><th>Attributed (IST)</th></tr></thead>
                    <tbody>
                    <c:forEach var="m" items="${editMarks}">
                        <tr>
                            <td>${m.contentPlan.contentId}</td>
                            <td>${m.roleType}</td>
                            <td>${m.attributedMarkValue}</td>
                            <td>${kcpc:ist(m.attributedAt)}</td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty editMarks}"><tr><td colspan="4" class="muted">No Edit marks attributed yet.</td></tr></c:if>
                    </tbody>
                </table>
            </div>
        </div>

        <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.</p>
    </div>
    </c:if>

    <%-- ================================================================ PUBLISHING ========= --%>
    <c:if test="${showPublishTab}">
    <div class="my-work-stage-panel hidden" data-tab-panel="publish">
        <div class="kpi-cards kpi-cards-3">
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

        <div class="my-work-tabs">
            <button type="button" class="my-work-tab active" data-tab="publish-active">Active Work</button>
            <button type="button" class="my-work-tab" data-tab="publish-history">History</button>
        </div>

        <div class="my-work-tab-panel" data-tab-panel="publish-active">
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
                            <td>${item.plannedDate}</td>
                            <td><c:out value="${empty item.models ? '—' : item.models}"/></td>
                            <td><c:out value="${empty item.targetsSummary ? '—' : item.targetsSummary}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${item.repost}"><span class="stage-badge stage-repost">REPOST</span></c:when>
                                </c:choose>
                                <span class="status-pill ${item.statusCssClass}"><c:out value="${item.statusLabel}"/></span>
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
        </div>

        <div class="my-work-tab-panel hidden" data-tab-panel="publish-history">
            <div class="panel my-work-table-wrapper">
                <h2>Completed Publishing Work</h2>
                <table class="data-table">
                    <thead>
                    <tr><th>Content ID</th><th>Content</th><th>Planned Live Date</th><th>Completed On</th><th>View</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="w" items="${publishCompletedWork}">
                        <tr>
                            <td>${w.contentId}</td>
                            <td><c:out value="${w.title}"/></td>
                            <td>${w.stageDate}</td>
                            <td><c:if test="${not empty w.completedOn}">${kcpc:ist(w.completedOn)}</c:if></td>
                            <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work/history/publish/${w.assignmentId}">View Details</a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty publishCompletedWork}">
                        <tr><td colspan="5" class="muted">No completed publishing work yet.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </div>

        <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.</p>
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
</body>
</html>
