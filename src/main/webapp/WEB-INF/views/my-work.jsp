<%@ page contentType="text/html;charset=UTF-8" language="java" %>
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
            <p class="muted">
                <c:choose>
                    <c:when test="${businessRoleName == 'Camera Person'}">View and complete your assigned shooting tasks.</c:when>
                    <c:when test="${businessRoleName == 'Video Editor'}">View and complete your assigned editing tasks.</c:when>
                    <c:when test="${businessRoleName == 'Publisher'}">View and complete your assigned publishing tasks.</c:when>
                    <c:otherwise>All your assigned tasks and progress.</c:otherwise>
                </c:choose>
            </p>
        </div>
        <%-- ENG-057/058/066: a quick-glance summary, never a management control - Role from the
             employee's own Business Role (never Designation), Shoot/Edit Lead only shown when this
             employee is actually the Lead on at least one of their own currently active Shoot/Edit
             assignments. --%>
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

    <c:choose>
        <%-- ENG-058: the Cameraperson-focused KPI-cards + tabbed dashboard. Every other Business
             Role keeps the ENG-057 generic stacked Active/Completed Work layout, unchanged, in the
             c:otherwise branch below. --%>
        <c:when test="${businessRoleName == 'Camera Person'}">
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

            <div class="my-work-tabs">
                <button type="button" class="my-work-tab active" data-tab="active">Active Work</button>
                <button type="button" class="my-work-tab" data-tab="history">History</button>
                <button type="button" class="my-work-tab" data-tab="marks">Marks</button>
            </div>

            <div class="my-work-tab-panel" data-tab-panel="active">
                <div class="panel my-work-table-wrapper">
                    <h2>Active Shoot Tasks</h2>
                    <table class="data-table">
                        <thead>
                        <tr>
                            <th>Content ID</th>
                            <th>Idea / Content</th>
                            <th>Priority</th>
                            <th>Shoot Date</th>
                            <th>Model(s)</th>
                            <th>Shoot Lead</th>
                            <th>Status</th>
                            <th>Drive</th>
                            <th>Action</th>
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
                                </td>
                                <td>
                                    <c:if test="${not empty item.driveLink}">
                                        <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer" title="Open Drive">&#128193;</a>
                                    </c:if>
                                </td>
                                <td>
                                    <c:if test="${not empty item.actionLabel}">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}"><c:out value="${item.actionLabel}"/></a>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty shootActiveWork}">
                            <tr><td colspan="9" class="muted">No active shoot tasks.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>

                <%-- ENG-063: My Review Feedback lives INSIDE the Active Work tab panel only - it
                     hides/shows together with Active Shoot Tasks on tab switch, and never appears
                     under History or Marks. --%>
                <div class="panel my-work-table-wrapper feedback-panel">
                    <div class="page-header-row">
                        <div>
                            <h2>My Review Feedback</h2>
                            <p class="muted">Feedback and decisions on your shoot submissions.</p>
                        </div>
                    </div>

                    <form method="get" action="${pageContext.request.contextPath}/app/my-work" class="feedback-filters">
                        <div class="filter-field filter-field-search">
                            <input type="text" name="feedbackQ" class="feedback-search-input"
                                   placeholder="Search by Content ID..." value="${fn:escapeXml(feedbackQuery)}">
                        </div>
                        <div class="filter-pills">
                            <c:url var="pillAllUrl" value="/app/my-work"><c:param name="feedbackQ" value="${feedbackQuery}"/><c:param name="feedbackFilter" value="ALL"/></c:url>
                            <c:url var="pillReworkUrl" value="/app/my-work"><c:param name="feedbackQ" value="${feedbackQuery}"/><c:param name="feedbackFilter" value="REWORK"/></c:url>
                            <c:url var="pillApprovedUrl" value="/app/my-work"><c:param name="feedbackQ" value="${feedbackQuery}"/><c:param name="feedbackFilter" value="APPROVED"/></c:url>
                            <a class="filter-pill ${feedbackFilter == 'ALL' ? 'active' : ''}" href="${pillAllUrl}">All</a>
                            <a class="filter-pill ${feedbackFilter == 'REWORK' ? 'active' : ''}" href="${pillReworkUrl}">Rework Required</a>
                            <a class="filter-pill ${feedbackFilter == 'APPROVED' ? 'active' : ''}" href="${pillApprovedUrl}">Approved</a>
                        </div>
                        <button type="submit" class="btn-outline">Search</button>
                    </form>

                    <div class="feedback-card-list">
                        <c:forEach var="group" items="${shootFeedbackGroups}">
                            <div class="feedback-card">
                                <div class="feedback-card-main">
                                    <div class="feedback-content-col">
                                        <div class="feedback-field-label">Content ID</div>
                                        <a class="content-id-link" href="${pageContext.request.contextPath}/app/deliverables/${group.contentPlanId}"><c:out value="${group.contentId}"/></a>
                                        <div class="feedback-field-label feedback-stage-label">Stage</div>
                                        <div class="feedback-stage-value">SHOOT REVIEW</div>
                                    </div>
                                    <div class="feedback-decision-col">
                                        <span class="status-pill ${group.latest.decisionCssClass}"><c:out value="${fn:toUpperCase(group.latest.decisionLabel)}"/></span>
                                    </div>
                                    <div class="feedback-reason-col">
                                        <div class="feedback-field-label">Reason / Feedback</div>
                                        <div><c:out value="${empty group.latest.reason ? '—' : group.latest.reason}"/></div>
                                        <c:if test="${not empty group.latest.reviewerName}">
                                            <div class="feedback-reviewer">Decided by <c:out value="${group.latest.reviewerName}"/><c:if test="${group.latest.reviewerIsLead}"> (Lead)</c:if></div>
                                        </c:if>
                                    </div>
                                    <div class="feedback-date-col">
                                        <div>${kcpc:istDate(group.latest.decidedAt)}</div>
                                        <div class="muted">${kcpc:istTime(group.latest.decidedAt)} IST</div>
                                    </div>
                                    <div class="feedback-action-col">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${group.contentPlanId}">View Task &rarr;</a>
                                    </div>
                                </div>
                                <c:if test="${group.hasHistory}">
                                    <details class="feedback-history">
                                        <summary>View Feedback History</summary>
                                        <ul class="feedback-history-list">
                                            <c:forEach var="entry" items="${group.priorHistory}">
                                                <li>
                                                    <span class="ts">${kcpc:istDate(entry.decidedAt)} &middot; ${kcpc:istTime(entry.decidedAt)}</span>
                                                    <span class="status-pill ${entry.decisionCssClass}"><c:out value="${fn:toUpperCase(entry.decisionLabel)}"/></span>
                                                    <c:out value="${empty entry.reason ? '—' : entry.reason}"/>
                                                </li>
                                            </c:forEach>
                                        </ul>
                                    </details>
                                </c:if>
                            </div>
                        </c:forEach>
                        <c:if test="${empty shootFeedbackGroups}"><p class="muted">No review feedback yet.</p></c:if>
                    </div>

                    <c:if test="${feedbackTotalCount > 0}">
                        <div class="pagination">
                            <span>Showing ${feedbackFromIndex} to ${feedbackToIndex} of ${feedbackTotalCount} Content IDs</span>
                            <div class="pagination-controls">
                                <c:choose>
                                    <c:when test="${feedbackCurrentPage > 1}">
                                        <c:url var="feedbackPrevUrl" value="/app/my-work">
                                            <c:param name="feedbackQ" value="${feedbackQuery}"/>
                                            <c:param name="feedbackFilter" value="${feedbackFilter}"/>
                                            <c:param name="feedbackPage" value="${feedbackCurrentPage - 1}"/>
                                        </c:url>
                                        <a href="${feedbackPrevUrl}">&#8249;</a>
                                    </c:when>
                                    <c:otherwise><span class="page-disabled">&#8249;</span></c:otherwise>
                                </c:choose>
                                <c:forEach begin="1" end="${feedbackTotalPages}" var="p">
                                    <c:choose>
                                        <c:when test="${p == feedbackCurrentPage}"><span class="page-current">${p}</span></c:when>
                                        <c:otherwise>
                                            <c:url var="feedbackPageUrl" value="/app/my-work">
                                                <c:param name="feedbackQ" value="${feedbackQuery}"/>
                                                <c:param name="feedbackFilter" value="${feedbackFilter}"/>
                                                <c:param name="feedbackPage" value="${p}"/>
                                            </c:url>
                                            <a href="${feedbackPageUrl}">${p}</a>
                                        </c:otherwise>
                                    </c:choose>
                                </c:forEach>
                                <c:choose>
                                    <c:when test="${feedbackCurrentPage < feedbackTotalPages}">
                                        <c:url var="feedbackNextUrl" value="/app/my-work">
                                            <c:param name="feedbackQ" value="${feedbackQuery}"/>
                                            <c:param name="feedbackFilter" value="${feedbackFilter}"/>
                                            <c:param name="feedbackPage" value="${feedbackCurrentPage + 1}"/>
                                        </c:url>
                                        <a href="${feedbackNextUrl}">&#8250;</a>
                                    </c:when>
                                    <c:otherwise><span class="page-disabled">&#8250;</span></c:otherwise>
                                </c:choose>
                            </div>
                        </div>
                    </c:if>
                </div>
            </div>

            <div class="my-work-tab-panel hidden" data-tab-panel="history">
                <div class="panel my-work-table-wrapper">
                    <h2>Completed Shoot Work</h2>
                    <table class="data-table">
                        <thead>
                        <tr>
                            <th>Content ID</th>
                            <th>Idea / Content</th>
                            <th>Shoot Date</th>
                            <th>Model(s)</th>
                            <th>Completed On</th>
                            <th>Result</th>
                            <th>Remarks</th>
                            <th>View</th>
                        </tr>
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
                                <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${w.contentPlanId}">View Details</a></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty shootCompletedWork}">
                            <tr><td colspan="8" class="muted">No completed shoot work yet.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>

            <div class="my-work-tab-panel hidden" data-tab-panel="marks">
                <div class="panel my-work-table-wrapper">
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

            <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.</p>
        </c:when>

        <%-- ENG-066: the Video Editor-focused KPI-cards + tabbed dashboard - exact same visual
             pattern as the Cameraperson dashboard above (My Work / Shoot ENG-058), Edit-flavored
             data only, so the employee portal stays visually consistent across stages. --%>
        <c:when test="${businessRoleName == 'Video Editor'}">
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

            <div class="my-work-tabs">
                <button type="button" class="my-work-tab active" data-tab="active">Active Work</button>
                <button type="button" class="my-work-tab" data-tab="history">History</button>
                <button type="button" class="my-work-tab" data-tab="marks">Marks</button>
            </div>

            <div class="my-work-tab-panel" data-tab-panel="active">
                <div class="panel my-work-table-wrapper">
                    <h2>Active Edit Tasks</h2>
                    <table class="data-table">
                        <thead>
                        <tr>
                            <th>Content ID</th>
                            <th>Idea / Content</th>
                            <th>Priority</th>
                            <th>Edit Date</th>
                            <th>Editor(s)</th>
                            <th>Edit Lead</th>
                            <th>Status</th>
                            <th>Drive</th>
                            <th>Action</th>
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
                                </td>
                                <td>
                                    <c:if test="${not empty item.driveLink}">
                                        <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer" title="Open Drive">&#128193;</a>
                                    </c:if>
                                </td>
                                <td>
                                    <c:if test="${not empty item.actionLabel}">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}"><c:out value="${item.actionLabel}"/></a>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty editActiveWork}">
                            <tr><td colspan="9" class="muted">No active edit tasks.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>

                <%-- ENG-066: My Review Feedback lives INSIDE the Active Work tab panel only, exactly
                     like the Cameraperson version (ENG-063) - EDIT_REVIEW decisions only. --%>
                <div class="panel my-work-table-wrapper feedback-panel">
                    <div class="page-header-row">
                        <div>
                            <h2>My Review Feedback</h2>
                            <p class="muted">Feedback and decisions on your edit submissions.</p>
                        </div>
                    </div>

                    <form method="get" action="${pageContext.request.contextPath}/app/my-work" class="feedback-filters">
                        <div class="filter-field filter-field-search">
                            <input type="text" name="editFeedbackQ" class="feedback-search-input"
                                   placeholder="Search by Content ID..." value="${fn:escapeXml(editFeedbackQuery)}">
                        </div>
                        <div class="filter-pills">
                            <c:url var="editPillAllUrl" value="/app/my-work"><c:param name="editFeedbackQ" value="${editFeedbackQuery}"/><c:param name="editFeedbackFilter" value="ALL"/></c:url>
                            <c:url var="editPillReworkUrl" value="/app/my-work"><c:param name="editFeedbackQ" value="${editFeedbackQuery}"/><c:param name="editFeedbackFilter" value="REWORK"/></c:url>
                            <c:url var="editPillApprovedUrl" value="/app/my-work"><c:param name="editFeedbackQ" value="${editFeedbackQuery}"/><c:param name="editFeedbackFilter" value="APPROVED"/></c:url>
                            <a class="filter-pill ${editFeedbackFilter == 'ALL' ? 'active' : ''}" href="${editPillAllUrl}">All</a>
                            <a class="filter-pill ${editFeedbackFilter == 'REWORK' ? 'active' : ''}" href="${editPillReworkUrl}">Rework Required</a>
                            <a class="filter-pill ${editFeedbackFilter == 'APPROVED' ? 'active' : ''}" href="${editPillApprovedUrl}">Approved</a>
                        </div>
                        <button type="submit" class="btn-outline">Search</button>
                    </form>

                    <div class="feedback-card-list">
                        <c:forEach var="group" items="${editFeedbackGroups}">
                            <div class="feedback-card">
                                <div class="feedback-card-main">
                                    <div class="feedback-content-col">
                                        <div class="feedback-field-label">Content ID</div>
                                        <a class="content-id-link" href="${pageContext.request.contextPath}/app/deliverables/${group.contentPlanId}"><c:out value="${group.contentId}"/></a>
                                        <div class="feedback-field-label feedback-stage-label">Stage</div>
                                        <div class="feedback-stage-value">EDIT REVIEW</div>
                                    </div>
                                    <div class="feedback-decision-col">
                                        <span class="status-pill ${group.latest.decisionCssClass}"><c:out value="${fn:toUpperCase(group.latest.decisionLabel)}"/></span>
                                    </div>
                                    <div class="feedback-reason-col">
                                        <div class="feedback-field-label">Reason / Feedback</div>
                                        <div><c:out value="${empty group.latest.reason ? '—' : group.latest.reason}"/></div>
                                        <c:if test="${not empty group.latest.reviewerName}">
                                            <div class="feedback-reviewer">Decided by <c:out value="${group.latest.reviewerName}"/><c:if test="${group.latest.reviewerIsLead}"> (Lead)</c:if></div>
                                        </c:if>
                                    </div>
                                    <div class="feedback-date-col">
                                        <div>${kcpc:istDate(group.latest.decidedAt)}</div>
                                        <div class="muted">${kcpc:istTime(group.latest.decidedAt)} IST</div>
                                    </div>
                                    <div class="feedback-action-col">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${group.contentPlanId}">View Task &rarr;</a>
                                    </div>
                                </div>
                                <c:if test="${group.hasHistory}">
                                    <details class="feedback-history">
                                        <summary>View Feedback History</summary>
                                        <ul class="feedback-history-list">
                                            <c:forEach var="entry" items="${group.priorHistory}">
                                                <li>
                                                    <span class="ts">${kcpc:istDate(entry.decidedAt)} &middot; ${kcpc:istTime(entry.decidedAt)}</span>
                                                    <span class="status-pill ${entry.decisionCssClass}"><c:out value="${fn:toUpperCase(entry.decisionLabel)}"/></span>
                                                    <c:out value="${empty entry.reason ? '—' : entry.reason}"/>
                                                </li>
                                            </c:forEach>
                                        </ul>
                                    </details>
                                </c:if>
                            </div>
                        </c:forEach>
                        <c:if test="${empty editFeedbackGroups}"><p class="muted">No review feedback yet.</p></c:if>
                    </div>

                    <c:if test="${editFeedbackTotalCount > 0}">
                        <div class="pagination">
                            <span>Showing ${editFeedbackFromIndex} to ${editFeedbackToIndex} of ${editFeedbackTotalCount} Content IDs</span>
                            <div class="pagination-controls">
                                <c:choose>
                                    <c:when test="${editFeedbackCurrentPage > 1}">
                                        <c:url var="editFeedbackPrevUrl" value="/app/my-work">
                                            <c:param name="editFeedbackQ" value="${editFeedbackQuery}"/>
                                            <c:param name="editFeedbackFilter" value="${editFeedbackFilter}"/>
                                            <c:param name="editFeedbackPage" value="${editFeedbackCurrentPage - 1}"/>
                                        </c:url>
                                        <a href="${editFeedbackPrevUrl}">&#8249;</a>
                                    </c:when>
                                    <c:otherwise><span class="page-disabled">&#8249;</span></c:otherwise>
                                </c:choose>
                                <c:forEach begin="1" end="${editFeedbackTotalPages}" var="p">
                                    <c:choose>
                                        <c:when test="${p == editFeedbackCurrentPage}"><span class="page-current">${p}</span></c:when>
                                        <c:otherwise>
                                            <c:url var="editFeedbackPageUrl" value="/app/my-work">
                                                <c:param name="editFeedbackQ" value="${editFeedbackQuery}"/>
                                                <c:param name="editFeedbackFilter" value="${editFeedbackFilter}"/>
                                                <c:param name="editFeedbackPage" value="${p}"/>
                                            </c:url>
                                            <a href="${editFeedbackPageUrl}">${p}</a>
                                        </c:otherwise>
                                    </c:choose>
                                </c:forEach>
                                <c:choose>
                                    <c:when test="${editFeedbackCurrentPage < editFeedbackTotalPages}">
                                        <c:url var="editFeedbackNextUrl" value="/app/my-work">
                                            <c:param name="editFeedbackQ" value="${editFeedbackQuery}"/>
                                            <c:param name="editFeedbackFilter" value="${editFeedbackFilter}"/>
                                            <c:param name="editFeedbackPage" value="${editFeedbackCurrentPage + 1}"/>
                                        </c:url>
                                        <a href="${editFeedbackNextUrl}">&#8250;</a>
                                    </c:when>
                                    <c:otherwise><span class="page-disabled">&#8250;</span></c:otherwise>
                                </c:choose>
                            </div>
                        </div>
                    </c:if>
                </div>
            </div>

            <div class="my-work-tab-panel hidden" data-tab-panel="history">
                <div class="panel my-work-table-wrapper">
                    <h2>Completed Edit Work</h2>
                    <table class="data-table">
                        <thead>
                        <tr>
                            <th>Content ID</th>
                            <th>Content</th>
                            <th>Edit Date</th>
                            <th>Completed On</th>
                            <th>Result</th>
                            <th>Remarks</th>
                            <th>View</th>
                        </tr>
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
                                <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${w.contentPlanId}">View Details</a></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty editCompletedWork}">
                            <tr><td colspan="7" class="muted">No completed edit work yet.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>

            <div class="my-work-tab-panel hidden" data-tab-panel="marks">
                <div class="panel my-work-table-wrapper">
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

            <p class="note-box">Need help or have questions? Use the Comments section in task details to discuss with your lead.</p>
        </c:when>

        <%-- ENG-068: the Publisher-focused KPI-cards + tabbed dashboard - same visual shell as the
             Cameraperson/Editor dashboards above, but Publishing-flavored: only 3 KPI cards (no
             Rework - Publishing has no review gate), no Marks tab (no marks model defined for
             Publisher), and a Targets column instead of a Lead column (Publishing has no Lead
             concept). --%>
        <c:when test="${businessRoleName == 'Publisher'}">
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

            <div class="my-work-tabs">
                <button type="button" class="my-work-tab active" data-tab="active">Active Work</button>
                <button type="button" class="my-work-tab" data-tab="history">History</button>
            </div>

            <div class="my-work-tab-panel" data-tab-panel="active">
                <div class="panel my-work-table-wrapper">
                    <h2>Active Publishing Tasks</h2>
                    <table class="data-table">
                        <thead>
                        <tr>
                            <th>Content ID</th>
                            <th>Content</th>
                            <th>Priority</th>
                            <th>Planned Live Date</th>
                            <th>Publisher(s)</th>
                            <th>Targets</th>
                            <th>Status</th>
                            <th>Drive</th>
                            <th>Action</th>
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
                                <td><span class="status-pill ${item.statusCssClass}"><c:out value="${item.statusLabel}"/></span></td>
                                <td>
                                    <c:if test="${not empty item.driveLink}">
                                        <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer" title="Open Drive">&#128193;</a>
                                    </c:if>
                                </td>
                                <td>
                                    <c:if test="${not empty item.actionLabel}">
                                        <a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}"><c:out value="${item.actionLabel}"/></a>
                                    </c:if>
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

            <div class="my-work-tab-panel hidden" data-tab-panel="history">
                <div class="panel my-work-table-wrapper">
                    <h2>Completed Publishing Work</h2>
                    <table class="data-table">
                        <thead>
                        <tr>
                            <th>Content ID</th>
                            <th>Content</th>
                            <th>Planned Live Date</th>
                            <th>Completed On</th>
                            <th>View</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="w" items="${publishCompletedWork}">
                            <tr>
                                <td>${w.contentId}</td>
                                <td><c:out value="${w.title}"/></td>
                                <td>${w.stageDate}</td>
                                <td><c:if test="${not empty w.completedOn}">${kcpc:ist(w.completedOn)}</c:if></td>
                                <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${w.contentPlanId}">View Details</a></td>
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
        </c:when>

        <c:otherwise>
            <div class="panel my-work-table-wrapper">
                <h2>Active Work <span class="count-badge">${activeWork.size()}</span></h2>
                <table class="data-table">
                    <thead>
                    <tr>
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
                            <td><span class="status-pill ${item.statusCssClass}"><c:out value="${item.statusLabel}"/></span></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty item.driveLink}">
                                        <a class="drive-link" href="${item.driveLink}" target="_blank" rel="noopener noreferrer">Open Drive &#8599;</a>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </td>
                            <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${item.contentPlanId}">View Details</a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty activeWork}">
                        <tr><td colspan="8" class="muted">No active work.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>

            <div class="panel my-work-table-wrapper">
                <h2>Completed Work <span class="count-badge">${completedWork.size()}</span></h2>
                <table class="data-table">
                    <thead>
                    <tr>
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
                            <td><a class="btn-outline" href="${pageContext.request.contextPath}/app/deliverables/${w.contentPlanId}">View Details</a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty completedWork}">
                        <tr><td colspan="6" class="muted">No completed work yet.</td></tr>
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

            <%-- ENG-059/062: My Ideas moved to its own /app/ideas page (kept out of My Work). This
                 generic, all-gate-types feedback table stays exactly as before for every non-Camera
                 Person Business Role; the Camera Person branch above has its own redesigned,
                 SHOOT_REVIEW-scoped "My Review Feedback" section instead. --%>
            <div class="panel">
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
            </div>
        </c:otherwise>
    </c:choose>
</main>
<script src="${pageContext.request.contextPath}/js/my-work-tabs.js" defer></script>
</body>
</html>
