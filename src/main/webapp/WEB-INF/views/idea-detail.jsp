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
     ideaStatusHistory/-Asc, contentPlanId, idtLastUpdated) - no workflow/permission/decision logic
     lives here. The Idea's own status vocabulary (Under Review/Approved/Retained/Rejected/Reopened)
     is the ONLY status ever shown - never a downstream Planning/Shoot/Edit/Publishing WorkflowStatus
     name. idtLastUpdated is computed server-side (IdeaMvcController#computeLastUpdated) rather than
     here, so it can correctly factor in a Reference Link edit alongside the lifecycle history -
     an Instant comparison isn't reliable to do in plain JSTL EL. --%>
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
                <%-- Idea Description/Details (notes_remarks) may be unlimited-length script content -
                     it is never rendered inline in this card (see the removed field row below); only
                     this note icon, shown exclusively when a description exists, opens a modal with
                     the complete text (view-only for most users; CEO/Marketing Manager also get an
                     Edit control - see fragments/idea-description-modal*.jspf and
                     IdeaService#updateDescription). --%>
                <c:set var="ideaDescModalIdea" value="${idea}"/>
                <c:set var="ideaDescModalCanEdit" value="${canEditIdeaDescription}"/>
                <c:set var="ideaDescModalAjax" value="${false}"/>
                <c:set var="ideaDescModalTriggerClass" value="idea-detail-header-note-btn"/>
                <h2 class="idea-detail-card-title">
                    <span class="idea-detail-card-icon idea-detail-icon-circle">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><line x1="12" y1="11" x2="12" y2="16"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                    </span>
                    Idea Details
                    <%@ include file="fragments/idea-description-modal-trigger.jspf" %>
                </h2>
                <%@ include file="fragments/idea-description-modal.jspf" %>
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
                                <c:set var="refLinkEditIdea" value="${idea}"/>
                                <c:set var="refLinkEditCanEdit" value="${canEditReferenceLink}"/>
                                <c:set var="refLinkEditAjax" value="${false}"/>
                                <%@ include file="fragments/idea-reference-link-edit.jspf" %>
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
                            <%-- ENG-092: options driven by the admin-managed Mark Catalogue
                                 (Administration -> Mark Catalogue), not a hardcoded list.
                                 ENG-096: each mark only applies to a role whose stage is actually
                                 part of the selected pipeline - toggled live by updateStagesFields()
                                 in idea-detail.js, same stage-truth already used for the Shoot/
                                 Editor/Publisher assignment sections below. --%>
                            <div class="field-row idea-detail-marks-row" id="idea-review-team-marks-row">
                                <div id="idea-review-cameraman-mark-field">
                                    <label>Cameraperson Mark (Approve only)
                                        <select name="cameramanMark">
                                            <c:forEach var="v" items="${cameramanMarkOptions}">
                                                <option value="${v.markValue}">${v.markValue}</option>
                                            </c:forEach>
                                        </select>
                                    </label>
                                </div>
                                <div id="idea-review-editor-mark-field">
                                    <label>Editor Mark (Approve only)
                                        <select name="editorMark">
                                            <c:forEach var="v" items="${editorMarkOptions}">
                                                <option value="${v.markValue}">${v.markValue}</option>
                                            </c:forEach>
                                        </select>
                                    </label>
                                </div>
                                <div id="idea-review-model-mark-field">
                                    <label>Model Mark (Approve only)
                                        <select name="modelMark">
                                            <c:forEach var="v" items="${modelMarkOptions}">
                                                <option value="${v.markValue}">${v.markValue}</option>
                                            </c:forEach>
                                        </select>
                                    </label>
                                </div>
                            </div>

                            <%-- Workflow redesign: Planning is no longer a separate stage - every
                                 field it used to collect is entered right here, only relevant (and
                                 only shown, via idea-detail.js) when Approve is the chosen decision.
                                 Approval validates all of this and creates the Content Plan already
                                 fully populated, moving straight to Shoot Assigned. --%>
                            <div id="idea-review-planning-fields" class="idea-detail-planning-fields">
                                <h3>Planning Details</h3>
                                <div class="form-grid">
                                    <label>Category
                                        <select name="categoryText">
                                            <c:forEach var="cat" items="${categoryOptions}">
                                                <option value="${cat.name}">${cat.name}</option>
                                            </c:forEach>
                                        </select>
                                    </label>
                                    <label>Priority
                                        <select name="contentPriority">
                                            <c:forEach var="p" items="${priorities}">
                                                <option value="${p}" ${p == 'LOW' ? 'selected' : ''}>${p}</option>
                                            </c:forEach>
                                        </select>
                                    </label>
                                    <label>SKU Reference <input type="text" name="skuReference"></label>

                                    <label>Drive Folder Link
                                        <input type="text" name="folderLink" placeholder="https://drive.google.com/...">
                                    </label>
                                    <label>Planning Mode
                                        <select name="planningMode" id="idea-review-planning-mode">
                                            <option value="STANDARD" selected>Standard</option>
                                            <option value="URGENT">Urgent</option>
                                        </select>
                                    </label>
                                    <%-- ENG-091: picks where the pipeline starts - Standard/Direct Edit/Direct
                                         Publishing. Own small component (stages-picker.js), not .kcpc-model-picker -
                                         see that file's header comment. Real form field: name="stages" checkboxes
                                         submit natively, no JS collection step needed on this page. --%>
                                    <label>Stages *
                                        <details class="kcpc-stages-picker" id="idea-review-stages-picker">
                                            <summary class="kcpc-stages-picker-toggle">
                                                <span class="kcpc-stages-chips"></span>
                                            </summary>
                                            <div class="kcpc-stages-checklist">
                                                <label class="stage-check-item"><input type="checkbox" name="stages" value="SHOOT" checked> Shoot</label>
                                                <label class="stage-check-item"><input type="checkbox" name="stages" value="EDIT" checked> Edit</label>
                                                <label class="stage-check-item"><input type="checkbox" name="stages" value="PUBLISHING" checked> Publishing</label>
                                            </div>
                                        </details>
                                    </label>

                                    <p class="note-box grid-span-all">Standard: Shoot/Edit Date default to Live Date minus 5/2
                                        days unless overridden below, and Planned Live Date must be at least 5 days away. Urgent:
                                        required when the Planned Live Date is fewer than 5 days away — Shoot Date, Edit Date and
                                        Urgency Reason become mandatory.</p>

                                    <label>Planned Live Date * <input type="date" name="plannedLiveDate" min="${today}"></label>
                                    <label id="idea-review-shoot-date-label">Shoot Date <input type="date" name="shootDate" min="${today}"></label>
                                    <label id="idea-review-edit-date-label">Edit Date <input type="date" name="editDate" min="${today}"></label>
                                    <label class="grid-span-all" id="idea-review-urgency-reason-label">Urgency Reason (required for Urgent)
                                        <input type="text" name="urgencyReason"></label>
                                </div>

                                <h4 class="idea-detail-outputs-heading">Planned Outputs</h4>
                                <p class="reviews-field-hint">Select the type(s) of content you are planning to create and where they will be published.</p>
                                <div class="reviews-outputs-grid-wrap">
                                    <table class="data-table reviews-outputs-grid" id="ideaOutputsGrid">
                                        <thead>
                                            <tr>
                                                <th>Output Type</th>
                                                <th>Platform / Channel</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <c:forEach var="t" items="${outputTypes}">
                                                <tr class="reviews-output-row reviews-output-row-disabled" data-output-type="${t}">
                                                    <td class="reviews-output-type-cell">
                                                        <label class="reviews-output-type-toggle">
                                                            <input type="checkbox" class="reviews-output-row-enable">
                                                            <c:choose>
                                                                <c:when test="${t == 'STORY'}">
                                                                    <span class="reviews-output-type-icon reviews-output-type-icon-STORY">
                                                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/></svg>
                                                                    </span>
                                                                    <span class="reviews-output-type-copy">
                                                                        <strong>Story</strong>
                                                                        <span class="muted">Short vertical stories for engagement</span>
                                                                    </span>
                                                                </c:when>
                                                                <c:when test="${t == 'POST'}">
                                                                    <span class="reviews-output-type-icon reviews-output-type-icon-POST">
                                                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                                                                    </span>
                                                                    <span class="reviews-output-type-copy">
                                                                        <strong>Post</strong>
                                                                        <span class="muted">Images / graphics / carousels for feeds</span>
                                                                    </span>
                                                                </c:when>
                                                                <c:when test="${t == 'REEL'}">
                                                                    <span class="reviews-output-type-icon reviews-output-type-icon-REEL">
                                                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/><line x1="2" y1="7" x2="7" y2="7"/><line x1="2" y1="17" x2="7" y2="17"/><line x1="17" y1="17" x2="22" y2="17"/><line x1="17" y1="7" x2="22" y2="7"/></svg>
                                                                    </span>
                                                                    <span class="reviews-output-type-copy">
                                                                        <strong>Reel</strong>
                                                                        <span class="muted">Short vertical videos for social platforms</span>
                                                                    </span>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <span class="reviews-output-type-icon reviews-output-type-icon-LONG_VIDEO">
                                                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>
                                                                    </span>
                                                                    <span class="reviews-output-type-copy">
                                                                        <strong>Long Video</strong>
                                                                        <span class="muted">Long-form or horizontal videos</span>
                                                                    </span>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </label>
                                                    </td>
                                                    <td class="reviews-output-platform-cell">
                                                        <details class="reviews-platform-picker">
                                                            <summary class="reviews-platform-picker-toggle">
                                                                <span class="reviews-platform-chips"><span class="muted">Select platforms</span></span>
                                                                <span class="reviews-platform-picker-count muted">0 selected</span>
                                                            </summary>
                                                            <div class="kcpc-channel-checklist reviews-output-target-checklist">
                                                                <c:forEach var="pt" items="${activePublicationTargets}">
                                                                    <label class="channel-check-item" data-platform="${pt.platform.platformName}">
                                                                        <input type="checkbox" class="reviews-output-target-checkbox" value="${pt.id}"
                                                                               data-platform="${pt.platform.platformName}"
                                                                               data-channel="${pt.channel.channelHandle}"> ${pt.platform.platformName} / ${pt.channel.channelHandle}
                                                                    </label>
                                                                </c:forEach>
                                                            </div>
                                                        </details>
                                                        <div class="reviews-output-platform-popovers"></div>
                                                    </td>
                                                </tr>
                                            </c:forEach>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="reviews-decision-error hidden" id="ideaOutputError"></div>
                                <input type="hidden" name="outputsJson" id="ideaOutputsJsonField" value="">

                                <%-- 3-column row: Cameraperson(s) / Shoot Lead / Model(s)/Talent, one CSS grid row,
                                     all three columns using the SAME label+control wrapper structure
                                     (.reviews-shoot-assignment-col) so they align to the exact same top edge - no
                                     per-field row-spanning/positioning tricks that could drift out of sync. Shoot
                                     Lead's <select> must still stay a DOM descendant of the Cameraperson
                                     .kcpc-model-picker (model-picker.js's refreshLeadOptions finds it via
                                     picker.querySelector('.kcpc-lead-select'), scoped to that one picker instance) -
                                     .reviews-shoot-assignment-camera is display:contents so its two
                                     .reviews-shoot-assignment-col children (Cameraperson's own column, Shoot
                                     Lead's own column) become the actual grid items while the Lead select stays
                                     nested inside the Cameraperson picker. Model(s)/Talent is its own separate,
                                     independently-wired .kcpc-model-picker carrying the same
                                     .reviews-shoot-assignment-col class directly (a true sibling, not nested) -
                                     optional, no * in the label; empty selection is already accepted by
                                     IdeaService#approve, which only iterates planning.talentUserIds() when
                                     non-null - unchanged. --%>
                                <div class="reviews-shoot-assignment-grid" id="idea-review-shoot-assignment-section">
                                    <div class="kcpc-model-picker reviews-shoot-assignment-camera">
                                        <div class="reviews-shoot-assignment-col">
                                            <label>Initial Shoot Team (at least one Cameraperson required) *</label>
                                            <div class="kcpc-model-input">
                                                <div class="kcpc-model-chips"></div>
                                                <input type="text" class="kcpc-model-search" placeholder="Search cameraperson...">
                                            </div>
                                            <div class="kcpc-model-checklist">
                                                <c:forEach var="cu" items="${camerapersonUsers}">
                                                    <label class="model-check-item">
                                                        <input type="checkbox" name="camerapersonUserIds" value="${cu.id}" data-name="${cu.fullName}"> ${cu.fullName}
                                                        <span class="muted assignee-task-count">(<c:out value="${cu.activeTaskLabel}"/>)</span>
                                                    </label>
                                                </c:forEach>
                                            </div>
                                        </div>
                                        <div class="reviews-shoot-assignment-col">
                                            <label for="ideaLeadCameraperson">Shoot Lead (optional)</label>
                                            <select name="leadCamerapersonUserId" id="ideaLeadCameraperson" class="kcpc-lead-select" disabled>
                                                <option value="">N/A</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="kcpc-model-picker reviews-shoot-assignment-col">
                                        <label>Model(s) / Talent</label>
                                        <div class="kcpc-model-input">
                                            <div class="kcpc-model-chips"></div>
                                            <input type="text" class="kcpc-model-search" placeholder="Search model...">
                                        </div>
                                        <div class="kcpc-model-checklist">
                                            <c:forEach var="mu" items="${modelUsers}">
                                                <label class="model-check-item">
                                                    <input type="checkbox" name="modelUserIds" value="${mu.id}" data-name="${mu.fullName}"> ${mu.fullName}
                                                    <span class="muted assignee-task-count">(<c:out value="${mu.activeTaskLabel}"/>)</span>
                                                </label>
                                            </c:forEach>
                                        </div>
                                    </div>
                                </div>

                                <%-- ENG-091/ENG-095 Direct Edit only: no earlier Shoot Review Approve exists to
                                     fold Editor(s) into when Shoot itself was never selected - this is the ONLY
                                     checkpoint for this Content Plan's Edit team, so Editor Lead is mandatory
                                     here too, exactly like the normal Shoot Review Approve fold-in. Same
                                     .reviews-next-team-grid/.reviews-next-team-picker 2-column pattern as the
                                     Cameraperson(s)/Shoot Lead pairing above - the Lead <select> must stay a DOM
                                     descendant of the Editor(s) .kcpc-model-picker (model-picker.js's
                                     refreshLeadOptions scopes its lookup to that picker). --%>
                                <div class="reviews-next-team-grid hidden" id="idea-review-editor-assignment-section">
                                    <div class="kcpc-model-picker reviews-next-team-picker">
                                        <div class="reviews-shoot-assignment-col">
                                            <label>Editor(s) *</label>
                                            <div class="kcpc-model-input">
                                                <div class="kcpc-model-chips"></div>
                                                <input type="text" class="kcpc-model-search" placeholder="Search editor...">
                                            </div>
                                            <div class="kcpc-model-checklist">
                                                <c:forEach var="eu" items="${editorCandidateUsers}">
                                                    <label class="model-check-item">
                                                        <input type="checkbox" name="editorUserIds" value="${eu.id}" data-name="${eu.fullName}"> ${eu.fullName}
                                                        <span class="muted assignee-task-count">(<c:out value="${eu.activeTaskLabel}"/>)</span>
                                                    </label>
                                                </c:forEach>
                                            </div>
                                        </div>
                                        <div class="reviews-shoot-assignment-col">
                                            <label for="ideaLeadEditor">Editor Lead *</label>
                                            <select name="leadEditorUserId" id="ideaLeadEditor" class="kcpc-lead-select" disabled>
                                                <option value="">N/A</option>
                                            </select>
                                        </div>
                                    </div>
                                </div>

                                <%-- ENG-097/ENG-099: Publisher(s) is assigned during Planning for EVERY stage
                                     combo, always visible, ALWAYS mandatory now (not just Direct Publishing) -
                                     IdeaService#approve rejects the request unconditionally when empty. For
                                     Shoot/Edit-starting combos, Publisher(s) can still ALSO be reassigned later
                                     at Shoot/Edit Review Approve (or Edit Stage Skip) exactly as today -
                                     assigning here never activates Publishing itself (PublishingAssignment
                                     carries no status of its own) and never duplicates a later assignment
                                     (PublishingService#assignPublisher is already idempotent). --%>
                                <div class="kcpc-model-picker" id="idea-review-publisher-assignment-section">
                                    <label id="idea-review-publisher-label">Publisher(s) *</label>
                                    <div class="kcpc-model-input">
                                        <div class="kcpc-model-chips"></div>
                                        <input type="text" class="kcpc-model-search" placeholder="Search publisher...">
                                    </div>
                                    <div class="kcpc-model-checklist">
                                        <c:forEach var="pu" items="${publisherCandidateUsers}">
                                            <label class="model-check-item">
                                                <input type="checkbox" name="publisherUserIds" value="${pu.id}" data-name="${pu.fullName}"> ${pu.fullName}
                                                <span class="muted assignee-task-count">(<c:out value="${pu.activeTaskLabel}"/>)</span>
                                            </label>
                                        </c:forEach>
                                    </div>
                                    <p class="reviews-field-hint hidden" id="idea-review-publisher-hint">Optional here for Shoot/Edit-starting content - Publisher(s) can also be assigned later during Shoot/Edit Review approval.</p>
                                </div>
                            </div>

                            <label id="idea-review-reason-label" for="idea-review-reason">Reason (mandatory for Reject; optional for Retain)</label>
                            <textarea name="reason" id="idea-review-reason" rows="3" maxlength="500" placeholder="Enter reason here..."></textarea>
                            <div class="idea-detail-reason-counter-row">
                                <span class="char-counter" data-counter-for="idea-review-reason">0 / 500</span>
                            </div>
                            <div class="alert-error hidden" id="idea-review-stages-error"></div>
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
            <p>This approved idea now has an active production deliverable.
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
<script src="${pageContext.request.contextPath}/js/model-picker.js" defer></script>
<script src="${pageContext.request.contextPath}/js/stages-picker.js" defer></script>
<script src="${pageContext.request.contextPath}/js/idea-detail.js" defer></script>
<script src="${pageContext.request.contextPath}/js/script-description-modal.js" defer></script>
<script src="${pageContext.request.contextPath}/js/idea-reference-link-edit.js" defer></script>
</body>
</html>
