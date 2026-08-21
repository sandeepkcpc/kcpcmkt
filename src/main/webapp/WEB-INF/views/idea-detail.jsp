<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — ${idea.businessIdeaCode}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<%-- Idea Detail / Review redesign - presentation only. Same model attributes idea-detail.jsp has
     always been given by IdeaMvcController#detail (idea, canDecide, ideaStatusLabel/CssClass,
     ideaStatusHistory/-Asc, contentPlanId) - no workflow/permission/decision logic lives here. The
     Idea's own status vocabulary (Under Review/Approved/Retained/Rejected/Reopened) is the ONLY
     status ever shown - never a downstream Planning/Shoot/Edit/Publishing WorkflowStatus name. --%>
<c:set var="idtLastEvent" value="${fn:length(ideaStatusHistory) > 0 ? ideaStatusHistory[0] : null}"/>
<c:set var="idtLastUpdated" value="${empty idtLastEvent ? idea.submittedAt : idtLastEvent.timestamp}"/>
<%-- The timeline's trailing "current status" node is only needed when the current Idea status
     ISN'T already the label of the most recent real lifecycle event (e.g. PA right after Submit -
     the last real event is "Submitted", not "Under Review"). Once the latest real event's own
     label already equals the current status (Approved/Rejected/Retained, or a Reopened idea whose
     last event actually is "Reopened"), that real event already IS the current state - no
     synthetic duplicate node. --%>
<c:set var="idtCurrentIsRealEvent" value="${not empty idtLastEvent and idtLastEvent.eventLabel == ideaStatusLabel}"/>

<main class="app-main app-main-wide idea-detail-page" id="ideaDetailPage">

    <div class="idea-detail-topbar">
        <a class="idea-detail-back-link" id="ideaDetailBackLink"
           href="${pageContext.request.contextPath}/app/ideas"
           data-default-href="${pageContext.request.contextPath}/app/ideas">&larr; Back to Idea Queue</a>
        <div class="idea-detail-title-row">
            <h1><c:out value="${idea.businessIdeaCode}"/> <span class="idea-detail-sep">&middot;</span> <c:out value="${idea.title}"/></h1>
            <span class="status-pill ${ideaStatusCssClass}"><c:out value="${ideaStatusLabel}"/></span>
        </div>
    </div>

    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="content-detail-body idea-detail-body">
        <div class="content-detail-main">

            <%-- ============================ IDEA DETAILS ============================ --%>
            <div class="panel idea-detail-card">
                <h2 class="idea-detail-card-title">
                    <span class="idea-detail-card-icon idea-detail-icon-circle">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><line x1="12" y1="11" x2="12" y2="16"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                    </span>
                    Idea Details
                </h2>
                <div class="idea-detail-fields-grid">
                    <div class="idea-detail-col">
                        <div class="idea-detail-row">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2"/><line x1="8" y1="9" x2="16" y2="9"/><line x1="8" y1="13" x2="13" y2="13"/></svg>
                                Idea ID
                            </span>
                            <span class="idea-detail-field-value"><c:out value="${idea.businessIdeaCode}"/></span>
                        </div>
                        <div class="idea-detail-row">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6h16M4 12h16M4 18h10"/></svg>
                                Title
                            </span>
                            <span class="idea-detail-field-value"><c:out value="${idea.title}"/></span>
                        </div>
                        <div class="idea-detail-row">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3v4a1 1 0 0 0 1 1h4"/><path d="M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2Z"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="12" y2="17"/></svg>
                                Idea Description / Details
                            </span>
                            <span class="idea-detail-field-value">
                                <c:choose>
                                    <c:when test="${not empty idea.notesRemarks}"><c:out value="${idea.notesRemarks}"/></c:when>
                                    <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                                </c:choose>
                            </span>
                        </div>
                        <div class="idea-detail-row">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 20H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h5"/><path d="M14 3h7v7"/><path d="M21 3l-8 8"/></svg>
                                Additional Note
                            </span>
                            <span class="idea-detail-field-value">
                                <c:choose>
                                    <c:when test="${not empty idea.additionalNote}"><c:out value="${idea.additionalNote}"/></c:when>
                                    <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                                </c:choose>
                            </span>
                        </div>
                        <div class="idea-detail-row idea-detail-row-last">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.07 0l2.83-2.83a5 5 0 0 0-7.07-7.07L11.5 4.5"/><path d="M14 11a5 5 0 0 0-7.07 0L4.1 13.83a5 5 0 0 0 7.07 7.07l1.36-1.36"/></svg>
                                Reference Link
                            </span>
                            <span class="idea-detail-field-value">
                                <c:choose>
                                    <c:when test="${not empty idea.referenceLink}">
                                        <a class="idea-detail-ref-link" href="${fn:escapeXml(idea.referenceLink)}" target="_blank" rel="noopener noreferrer">
                                            <c:out value="${idea.referenceLink}"/>
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                                        </a>
                                    </c:when>
                                    <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                                </c:choose>
                            </span>
                        </div>
                    </div>
                    <div class="idea-detail-col">
                        <div class="idea-detail-row">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                                Submitted By
                            </span>
                            <span class="idea-detail-field-value"><c:out value="${idea.submittedBy.fullName}"/></span>
                        </div>
                        <div class="idea-detail-row">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                                Submitted On
                            </span>
                            <span class="idea-detail-field-value">${kcpc:ist(idea.submittedAt)}</span>
                        </div>
                        <div class="idea-detail-row">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/></svg>
                                Last Updated
                            </span>
                            <span class="idea-detail-field-value">${kcpc:ist(idtLastUpdated)}</span>
                        </div>
                        <div class="idea-detail-row idea-detail-row-last">
                            <span class="idea-detail-field-label">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41 11 4H4v7l9.59 9.59a2 2 0 0 0 2.82 0l4.18-4.18a2 2 0 0 0 0-2.82Z"/><circle cx="8" cy="8.5" r="1"/></svg>
                                Current Idea Status
                            </span>
                            <span class="idea-detail-field-value"><span class="status-pill ${ideaStatusCssClass}"><c:out value="${ideaStatusLabel}"/></span></span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <%-- ============================ RIGHT SIDEBAR ============================ --%>
        <aside class="content-detail-sidebar idea-detail-sidebar">
            <div class="panel idea-detail-status-card">
                <div class="idea-detail-status-card-head">
                    <span class="idea-detail-status-card-icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3v4a1 1 0 0 0 1 1h4"/><path d="M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2Z"/></svg>
                    </span>
                    <span class="idea-detail-status-card-title">Current Idea Status</span>
                    <span class="status-pill ${ideaStatusCssClass} idea-detail-status-card-badge"><c:out value="${ideaStatusLabel}"/></span>
                </div>
                <p class="idea-detail-status-card-updated">Last updated: ${kcpc:ist(idtLastUpdated)}</p>
            </div>

            <div class="panel idea-detail-timeline-card">
                <h3 class="idea-detail-card-title idea-detail-card-title-plain">Status Timeline</h3>
                <ul class="idea-timeline">
                    <c:forEach var="event" items="${ideaStatusHistoryAsc}">
                        <li class="idea-timeline-item">
                            <span class="idea-timeline-dot idea-timeline-dot-${fn:toLowerCase(event.eventLabel)}">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                            </span>
                            <div class="idea-timeline-body">
                                <div class="idea-timeline-label"><c:out value="${event.eventLabel}"/></div>
                                <div class="idea-timeline-meta">${kcpc:ist(event.timestamp)}</div>
                                <div class="idea-timeline-meta">by <c:out value="${event.triggeredByName}"/></div>
                            </div>
                        </li>
                    </c:forEach>
                    <c:if test="${!idtCurrentIsRealEvent}">
                        <li class="idea-timeline-item idea-timeline-item-current">
                            <span class="idea-timeline-dot idea-timeline-dot-current">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/></svg>
                            </span>
                            <div class="idea-timeline-body">
                                <div class="idea-timeline-label"><c:out value="${ideaStatusLabel}"/></div>
                                <div class="idea-timeline-meta idea-timeline-current-label">Current Status</div>
                            </div>
                        </li>
                    </c:if>
                    <c:if test="${empty ideaStatusHistoryAsc and empty idtCurrentIsRealEvent}">
                        <li class="muted">No history yet.</li>
                    </c:if>
                </ul>

                <%-- ENG: contextual message, worded strictly from the real ideaStatusLabel/canDecide
                     the controller already computed - never claims an action is available unless
                     canDecide is actually true, and never mentions a downstream Content status. --%>
                <div class="idea-detail-timeline-note">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><line x1="12" y1="8" x2="12" y2="8.01"/><line x1="12" y1="11" x2="12" y2="16"/></svg>
                    <p>
                        <c:choose>
                            <c:when test="${idea.workflowInstance.currentStatusCode == 'PA' and ideaStatusLabel == 'Reopened'}">
                                This idea has been reopened and is under review again.
                                <c:if test="${canDecide}"> You can approve, reject or retain this idea.</c:if>
                            </c:when>
                            <c:when test="${idea.workflowInstance.currentStatusCode == 'PA'}">
                                This idea is currently under review.
                                <c:choose>
                                    <c:when test="${canDecide}"> You can approve, reject or retain this idea.</c:when>
                                    <c:otherwise> You cannot make a review decision on this idea &mdash; it is routed to another authorized reviewer.</c:otherwise>
                                </c:choose>
                            </c:when>
                            <c:when test="${idea.workflowInstance.currentStatusCode == 'RET'}">
                                This idea has been retained for future consideration.
                                <c:if test="${canDecide}"> You can reopen it to bring it back for review.</c:if>
                            </c:when>
                            <c:when test="${idea.workflowInstance.currentStatusCode == 'RJ'}">
                                This idea has been rejected. No further action is available.
                            </c:when>
                            <c:otherwise>
                                This idea has been approved and has moved into the production workflow.
                            </c:otherwise>
                        </c:choose>
                    </p>
                </div>
            </div>
        </aside>
    </div>

    <%-- ============================ REVIEW DECISION ============================ --%>
    <c:if test="${idea.workflowInstance.currentStatusCode == 'PA'}">
        <div class="panel idea-detail-review-card">
            <h2 class="idea-detail-card-title">
                <span class="idea-detail-card-icon idea-detail-icon-circle">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v18"/><path d="m4 7 3-3 3 3"/><path d="M4 7a4 4 0 0 0 6 0"/><path d="m14 7 3-3 3 3"/><path d="M14 7a4 4 0 0 0 6 0"/></svg>
                </span>
                Review Decision
            </h2>
            <c:choose>
                <c:when test="${canDecide}">
                    <div class="idea-detail-review-grid">
                        <form method="post" id="idea-review-form" action="${pageContext.request.contextPath}/app/ideas/${idea.id}/review">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Decision *
                                <select name="decision" id="idea-review-decision" required>
                                    <option value="" selected disabled>Select decision</option>
                                    <option value="APPROVE">Approve</option>
                                    <option value="REJECT">Reject</option>
                                    <option value="RETAIN">Retain</option>
                                </select>
                            </label>
                            <div class="field-row idea-detail-marks-row">
                                <div>
                                    <label>Cameraperson Mark (Approve only)
                                        <select name="cameramanMark">
                                            <option value="0.0">0</option>
                                            <option value="0.5">0.5</option>
                                            <option value="1.0">1.0</option>
                                            <option value="2.0">2.0</option>
                                            <option value="3.0">3.0</option>
                                        </select>
                                    </label>
                                </div>
                                <div>
                                    <label>Editor Mark (Approve only)
                                        <select name="editorMark">
                                            <option value="0.0">0</option>
                                            <option value="0.5">0.5</option>
                                            <option value="1.0">1.0</option>
                                            <option value="2.0">2.0</option>
                                            <option value="3.0">3.0</option>
                                        </select>
                                    </label>
                                </div>
                            </div>
                            <label id="idea-review-reason-label" for="idea-review-reason">Reason (mandatory for Reject; optional for Retain)</label>
                            <textarea name="reason" id="idea-review-reason" rows="3" maxlength="500" placeholder="Enter reason here..."></textarea>
                            <div class="idea-detail-reason-counter-row">
                                <span class="char-counter" data-counter-for="idea-review-reason">0 / 500</span>
                            </div>
                            <div class="idea-submit-actions">
                                <button type="reset" class="btn-outline" id="idea-review-clear">Clear</button>
                                <button type="submit" id="idea-review-submit">&#9992; Submit Decision</button>
                            </div>
                        </form>
                        <div class="idea-detail-guidelines">
                            <h3>Decision Guidelines</h3>
                            <div class="idea-guideline idea-guideline-approve">
                                <span class="idea-guideline-dot"></span>
                                <div>
                                    <strong>Approve</strong>
                                    <p>The idea will be approved and can proceed according to the existing workflow.</p>
                                </div>
                            </div>
                            <div class="idea-guideline idea-guideline-reject">
                                <span class="idea-guideline-dot"></span>
                                <div>
                                    <strong>Reject</strong>
                                    <p>The idea will be rejected. Reason is mandatory if required by existing backend rules.</p>
                                </div>
                            </div>
                            <div class="idea-guideline idea-guideline-retain">
                                <span class="idea-guideline-dot"></span>
                                <div>
                                    <strong>Retain</strong>
                                    <p>The idea will be retained for future consideration.</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </c:when>
                <c:otherwise>
                    <p class="note-box">You cannot make a review decision on work you submitted, prepared, or
                        participated in. This idea is routed to another authorized reviewer.</p>
                </c:otherwise>
            </c:choose>
        </div>
    </c:if>

    <c:if test="${idea.workflowInstance.currentStatusCode == 'RET'}">
        <div class="panel idea-detail-review-card">
            <h2 class="idea-detail-card-title">Retained</h2>
            <c:choose>
                <c:when test="${canDecide}">
                    <p class="muted">This idea is retained. You can reopen it to bring it back for review.</p>
                    <form method="post" action="${pageContext.request.contextPath}/app/ideas/${idea.id}/reopen">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <button type="submit">Reopen to Pending Approval</button>
                    </form>
                </c:when>
                <c:otherwise>
                    <p class="note-box">You do not hold Idea Review authority to reopen this idea.</p>
                </c:otherwise>
            </c:choose>
        </div>
    </c:if>

    <%-- ENG-061: informational only - the Idea Status above stays "Approved" regardless; this never
         replaces it with a Planning status, and the operational deliverable link itself is CEO/MM-only. --%>
    <c:if test="${not empty contentPlanId}">
        <div class="panel">
            <h2>Deliverable</h2>
            <p>This approved idea has moved to Planning.
                <c:if test="${accessClass != 'EMPLOYEE'}">
                    <a href="${pageContext.request.contextPath}/app/deliverables/${contentPlanId}">Open the deliverable &raquo;</a>
                </c:if>
            </p>
        </div>
    </c:if>

    <%-- ============================ IDEA DECISION HISTORY ============================ --%>
    <div class="panel idea-detail-card">
        <h2 class="idea-detail-card-title">
            <span class="idea-detail-card-icon idea-detail-icon-circle">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7"/><polyline points="3 4 3 9 8 9"/><polyline points="12 7 12 12 15 14"/></svg>
            </span>
            Idea Decision History
        </h2>
        <div class="idea-history-table-wrap">
            <table class="idea-history-table">
                <thead>
                <tr><th>Date &amp; Time</th><th>Action</th><th>By</th><th>Remarks</th></tr>
                </thead>
                <tbody>
                <c:forEach var="event" items="${ideaStatusHistory}">
                    <tr>
                        <td class="idea-history-time">${kcpc:ist(event.timestamp)}</td>
                        <td><span class="idea-action-badge idea-action-badge-${fn:toLowerCase(event.eventLabel)}"><c:out value="${event.eventLabel}"/></span></td>
                        <td><c:out value="${event.triggeredByName}"/></td>
                        <td class="idea-history-remarks">
                            <c:choose>
                                <c:when test="${not empty event.reason}"><c:out value="${event.reason}"/></c:when>
                                <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty ideaStatusHistory}">
                    <tr><td colspan="4" class="muted">No history yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/idea-detail.js" defer></script>
</body>
</html>
