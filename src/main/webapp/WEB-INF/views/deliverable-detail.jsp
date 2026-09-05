<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — ${plan.contentId}</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<%-- ENG-082: CEO/MM Content Detail redesign - management visibility/review/governed-actions shell
     over the SAME data this page has always built (see DeliverableMvcController#view); this file
     only changes presentation (tabs, stepper, summary bar, dynamic Action Center) and adds three
     new read-only pieces (platformSummaries, reviewFeedbackHistory, availableActions - all built
     server-side from existing repositories/permission flags, never re-deriving workflow rules
     here). The role/status branch to shoot-task-detail.jsp/edit-task-detail.jsp/publish-task-
     detail.jsp for an active Camera Person/Video Editor/Publisher's OWN task is unchanged and
     untouched by this file. --%>
<%-- Lifecycle stage index (1=Shoot..5=Completed, -1=no stage progress to show e.g. Cancelled/
     Rejected/Retained) - UI-representation only, derived purely from the real WorkflowStatus,
     never a new backend status. Index 0 ("Planning") is never assigned any more - Planning is
     folded into Idea Review, so a Content Plan is always already past it the moment it exists -
     the stepper's own node 0 always renders as its permanent "done" state instead. EAP
     (atomic/transient - see ENG-082 plan) is grouped with the stage it immediately advances TO,
     since it's never observably resting. --%>
<c:choose>
    <c:when test="${status == 'SA' or status == 'SIP' or status == 'SRV' or status == 'SAP'}"><c:set var="cdStageIndex" value="${1}"/></c:when>
    <c:when test="${status == 'EA' or status == 'ED' or status == 'ERV'}"><c:set var="cdStageIndex" value="${2}"/></c:when>
    <c:when test="${status == 'EAP' or status == 'RFP' or status == 'PUBG'}"><c:set var="cdStageIndex" value="${3}"/></c:when>
    <c:when test="${status == 'PP' or status == 'PFUP'}"><c:set var="cdStageIndex" value="${4}"/></c:when>
    <c:when test="${status == 'COMP'}"><c:set var="cdStageIndex" value="${5}"/></c:when>
    <c:otherwise><c:set var="cdStageIndex" value="${-1}"/></c:otherwise>
</c:choose>
<main class="app-main app-main-wide content-detail-page" id="contentDetailPage">
    <div class="content-detail-topbar">
        <div class="content-detail-topbar-row">
            <div class="content-detail-topbar-text">
                <%-- Caller-aware: only a viewer who can actually reach the Content Pipeline (native
                     authority) gets sent back there - a delegated employee (reaching this page from
                     My Work / Assignment Management) never gets a link to a module she can't open. --%>
                <c:choose>
                    <c:when test="${canSeePipeline}">
                        <a class="content-detail-back-link" id="contentDetailBackLink"
                           href="${pageContext.request.contextPath}/app/pipeline" data-default-href="${pageContext.request.contextPath}/app/pipeline">&larr; Back to Pipeline</a>
                    </c:when>
                    <c:otherwise>
                        <a class="content-detail-back-link" id="contentDetailBackLink"
                           href="${pageContext.request.contextPath}/app/my-work" data-default-href="${pageContext.request.contextPath}/app/my-work">&larr; Back to My Work</a>
                    </c:otherwise>
                </c:choose>
                <h1>Content Detail</h1>
                <div class="content-detail-title-row">
                    <span class="content-detail-id"><c:out value="${plan.contentId}"/></span>
                    <span class="content-detail-sep">&middot;</span>
                    <span class="content-detail-idea-title"><c:out value="${plan.idea.title}"/></span>
                </div>
            </div>

            <%-- ENG-082: lifecycle stepper - UI representation only, see cdStageIndex above. Six
                 nodes written out explicitly (not a loop over an EL list literal, which this
                 codebase has no existing precedent for and this app's exact JSP-EL/Jasper version
                 wasn't worth risking the whole page's render on) - each node's state is derived
                 purely from cdStageIndex. A "done" node always shows a checkmark regardless of
                 stage; a current/pending node shows its own small decorative icon (purely visual,
                 no meaning beyond labeling the stage - the text label underneath is the source of
                 truth) rendered with stroke="currentColor" so it inherits the dot's done/current/
                 pending color automatically. --%>
            <div class="content-detail-stepper">
                <div class="content-detail-step content-detail-step-${cdStageIndex > 0 ? 'done' : (cdStageIndex == 0 ? 'current' : 'pending')}">
                    <span class="content-detail-step-dot">
                        <c:choose>
                            <c:when test="${cdStageIndex > 0}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg></c:when>
                            <c:otherwise><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="4" width="12" height="17" rx="1.5"/><path d="M9 4V3a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v1"/><line x1="9" y1="11" x2="15" y2="11"/><line x1="9" y1="15" x2="13" y2="15"/></svg></c:otherwise>
                        </c:choose>
                    </span>
                    <span class="content-detail-step-label">Idea Review</span>
                </div>
                <div class="content-detail-step content-detail-step-${cdStageIndex > 1 ? 'done' : (cdStageIndex == 1 ? 'current' : 'pending')}">
                    <span class="content-detail-step-dot">
                        <c:choose>
                            <c:when test="${cdStageIndex > 1}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg></c:when>
                            <c:otherwise><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg></c:otherwise>
                        </c:choose>
                    </span>
                    <span class="content-detail-step-label">Shoot</span>
                </div>
                <div class="content-detail-step content-detail-step-${cdStageIndex > 2 ? 'done' : (cdStageIndex == 2 ? 'current' : 'pending')}">
                    <span class="content-detail-step-dot">
                        <c:choose>
                            <c:when test="${cdStageIndex > 2}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg></c:when>
                            <c:otherwise><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/></svg></c:otherwise>
                        </c:choose>
                    </span>
                    <span class="content-detail-step-label">Edit</span>
                </div>
                <div class="content-detail-step content-detail-step-${cdStageIndex > 3 ? 'done' : (cdStageIndex == 3 ? 'current' : 'pending')}">
                    <span class="content-detail-step-dot">
                        <c:choose>
                            <c:when test="${cdStageIndex > 3}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg></c:when>
                            <c:otherwise><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg></c:otherwise>
                        </c:choose>
                    </span>
                    <span class="content-detail-step-label">Publishing</span>
                </div>
                <div class="content-detail-step content-detail-step-${cdStageIndex > 4 ? 'done' : (cdStageIndex == 4 ? 'current' : 'pending')}">
                    <span class="content-detail-step-dot">
                        <c:choose>
                            <c:when test="${cdStageIndex > 4}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg></c:when>
                            <c:otherwise><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg></c:otherwise>
                        </c:choose>
                    </span>
                    <span class="content-detail-step-label">Performance</span>
                </div>
                <div class="content-detail-step content-detail-step-${cdStageIndex == 5 ? 'done' : 'pending'}">
                    <span class="content-detail-step-dot"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg></span>
                    <span class="content-detail-step-label">Completed</span>
                </div>
            </div>
        </div>
        <c:if test="${status == 'CAN' or status == 'RJ' or status == 'RET'}">
            <p class="muted">This deliverable's status (${status.statusName}) is outside the normal Planning&rarr;Completed
                progression - the stepper above intentionally shows no stage highlighted.</p>
        </c:if>
    </div>

    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty infoMessage}"><div class="alert-info">${infoMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <%-- ENG-082: top summary bar - high-value fields only, no duplication of the full Overview tab. --%>
    <div class="content-detail-summary-bar">
        <div class="content-detail-summary-cell">
            <span class="content-detail-summary-label">Status</span>
            <span class="content-detail-summary-value">
                <span class="status-badge"><c:out value="${status.statusName}"/></span>
                <c:if test="${delayed}"><span class="flag-chip flag-delayed">Delayed</span></c:if>
                <c:if test="${not empty openHold}"><span class="flag-chip flag-hold">On Hold</span></c:if>
            </span>
        </div>
        <div class="content-detail-summary-cell">
            <span class="content-detail-summary-label">Priority</span>
            <span class="content-detail-summary-value">
                <c:choose>
                    <c:when test="${not empty plan.contentPriority}">
                        <span class="priority-pill priority-${plan.contentPriority == 'HIGH' ? 'high' : (plan.contentPriority == 'MEDIUM' ? 'medium' : 'low')}"><c:out value="${plan.contentPriority}"/></span>
                    </c:when>
                    <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                </c:choose>
            </span>
        </div>
        <div class="content-detail-summary-cell">
            <span class="content-detail-summary-label">Planned Live Date</span>
            <span class="content-detail-summary-value">${empty plan.plannedLiveDate ? '&mdash;' : plan.plannedLiveDate}</span>
        </div>
        <div class="content-detail-summary-cell">
            <span class="content-detail-summary-label">Drive Link</span>
            <span class="content-detail-summary-value">
                <c:choose>
                    <c:when test="${not empty plan.folderLink}"><a class="drive-link" href="${plan.folderLink}" target="_blank" rel="noopener noreferrer">Open &#8599;</a></c:when>
                    <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                </c:choose>
                <%-- Automatic Drive folder provisioning status - only rendered once a structured
                     provisioning record exists (legacy content with just a manually-pasted
                     folder_link and no record is untouched) and only when it isn't already
                     SUCCEEDED, so the normal case (auto-provisioned, working) adds nothing here. --%>
                <c:if test="${not empty driveProvisioning and driveProvisioning.status != 'SUCCEEDED'}">
                    <span class="drive-provisioning-status drive-provisioning-${fn:toLowerCase(driveProvisioning.status)}">
                        <%-- Explicit status literal shown first (NOT_STARTED/IN_PROGRESS/FAILED) so
                             an admin can tell at a glance whether provisioning was ever actually
                             attempted, rather than inferring it from paraphrased text alone. --%>
                        Status: <c:out value="${driveProvisioning.status}"/> &mdash;
                        <c:choose>
                            <c:when test="${driveProvisioning.status == 'FAILED'}">Drive provisioning failed<c:if test="${not empty driveProvisioning.lastError}">: <c:out value="${driveProvisioning.lastError}"/></c:if></c:when>
                            <c:when test="${driveProvisioning.status == 'IN_PROGRESS'}">Drive provisioning in progress&hellip;</c:when>
                            <c:otherwise>Drive folders not yet provisioned</c:otherwise>
                        </c:choose>
                    </span>
                    <c:if test="${canManageDriveFolders and driveProvisioning.status != 'IN_PROGRESS'}">
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/drive/retry"
                              class="drive-provisioning-retry-form">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit" class="link-button">Retry Provisioning</button>
                        </form>
                    </c:if>
                </c:if>
            </span>
        </div>
        <div class="content-detail-summary-cell">
            <span class="content-detail-summary-label">Platform / Channel</span>
            <span class="content-detail-summary-value pipeline-platform-chips content-detail-platform-chips-inline">
                <c:forEach var="summary" items="${platformSummaries}" varStatus="ps">
                    <c:set var="popoverId" value="cd-summarybar-platform-popover-${plan.id}-${ps.index}"/>
                    <%@ include file="fragments/pipeline-platform-chip.jspf" %>
                </c:forEach>
                <c:if test="${empty platformSummaries}"><span class="muted">&mdash;</span></c:if>
            </span>
        </div>
        <div class="content-detail-summary-cell">
            <span class="content-detail-summary-label">Current Stage Lead</span>
            <span class="content-detail-summary-value">
            <c:choose>
                <c:when test="${cdStageIndex == 0}"><span class="muted">&mdash;</span></c:when>
                <c:when test="${cdStageIndex == 1}">
                    <c:set var="cdLead" value=""/>
                    <c:forEach var="a" items="${shootingAssignments}"><c:if test="${a.lead}"><c:set var="cdLead" value="${a.cameraperson.fullName}"/></c:if></c:forEach>
                    <c:out value="${empty cdLead ? '—' : cdLead}"/>
                </c:when>
                <c:when test="${cdStageIndex == 2}">
                    <c:set var="cdLead" value=""/>
                    <c:forEach var="a" items="${editingAssignments}"><c:if test="${a.lead}"><c:set var="cdLead" value="${a.editor.fullName}"/></c:if></c:forEach>
                    <c:out value="${empty cdLead ? '—' : cdLead}"/>
                </c:when>
                <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
            </c:choose>
            </span>
        </div>
    </div>

    <%-- Folder Link Management (PERM_13) admin override - manual repair/relink/recovery only;
         normal content creation never requires this. Works for legacy content with no structured
         provisioning record too (brings it under structured tracking for the first time). --%>
    <c:if test="${canManageDriveFolders}">
        <details class="drive-relink-details">
            <summary>Folder Link Management: relink Drive root folder</summary>
            <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/drive/relink" class="drive-relink-form">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Drive Folder ID or URL <input type="text" name="rootFolderIdOrUrl" placeholder="https://drive.google.com/drive/folders/..." required></label>
                <p class="muted">Updates the structured Drive record first, then resyncs the Drive Link shown above. If the 3 subfolders aren't already known under this folder, they can be created afterward via Retry Provisioning.</p>
                <button type="submit">Relink</button>
            </form>
        </details>
    </c:if>

    <div class="content-detail-body">
    <div class="content-detail-main">
    <%-- Permission-scoped: only the tabs actually relevant to this viewer's real authority/
         participation on THIS plan (canSeeXTab, computed in DeliverableMvcController#view) - never
         the full Overview..Timeline set for every viewer regardless of what they can do. Overview
         always shows; the lifecycle stepper above stays full/read-only regardless of these flags. --%>
    <div class="my-work-tabs content-detail-tabs">
        <button type="button" class="my-work-tab ${activeTab == 'overview' ? 'active' : ''}" data-tab="overview">Overview</button>
        <c:if test="${canSeeShootTab}"><button type="button" class="my-work-tab ${activeTab == 'shoot' ? 'active' : ''}" data-tab="shoot">Shoot</button></c:if>
        <c:if test="${canSeeEditTab}"><button type="button" class="my-work-tab ${activeTab == 'edit' ? 'active' : ''}" data-tab="edit">Edit</button></c:if>
        <c:if test="${canSeePublishingTab}"><button type="button" class="my-work-tab ${activeTab == 'publishing' ? 'active' : ''}" data-tab="publishing">Publishing</button></c:if>
        <c:if test="${canSeePerformanceTab}"><button type="button" class="my-work-tab ${activeTab == 'performance' ? 'active' : ''}" data-tab="performance">Performance</button></c:if>
        <c:if test="${canSeeTimeline}"><button type="button" class="my-work-tab ${activeTab == 'timeline' ? 'active' : ''}" data-tab="timeline">Timeline</button></c:if>
    </div>

    <%-- ============================ OVERVIEW ============================ --%>
    <div class="my-work-tab-panel ${activeTab == 'overview' ? '' : 'hidden'}" data-tab-panel="overview">
        <div class="panel content-detail-overview-card">
            <h3 class="content-detail-card-title">Overview</h3>
            <div class="content-detail-overview-grid">
            <div>
                <h4 class="content-detail-section-heading">Content Information</h4>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Content ID</span>
                    <span class="content-detail-field-value"><c:out value="${plan.contentId}"/></span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">SKU</span>
                    <span class="content-detail-field-value"><c:out value="${empty plan.skuReference ? '—' : plan.skuReference}"/></span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Idea / Content Title</span>
                    <span class="content-detail-field-value"><c:out value="${plan.idea.title}"/></span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Reference Link</span>
                    <span class="content-detail-field-value">
                        <c:choose>
                            <c:when test="${not empty plan.idea.referenceLink}"><a href="${plan.idea.referenceLink}" target="_blank" rel="noopener noreferrer"><c:out value="${plan.idea.referenceLink}"/></a></c:when>
                            <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                        </c:choose>
                    </span>
                </div>
                <c:if test="${not empty plan.idea.additionalNote}">
                    <div class="content-detail-field-row">
                        <span class="content-detail-field-label">Note</span>
                        <span class="content-detail-field-value"><c:out value="${plan.idea.additionalNote}"/></span>
                    </div>
                </c:if>
                <c:if test="${not empty plan.idea.notesRemarks}">
                    <div class="content-detail-field-row">
                        <span class="content-detail-field-label">Script Description</span>
                        <span class="content-detail-field-value">
                            <button type="button" class="script-description-icon-btn" id="scriptDescriptionOpen"
                                    aria-label="View full script description" title="View full script description">&#128221;</button>
                        </span>
                    </div>
                    <%-- Read-only viewer: the Idea Description/Details field (ideas.notes_remarks)
                         has no length limit (it may hold a full script) and is never rendered
                         inline here - only this modal, opened by the note-icon button above,
                         shows the complete text. Visibility is already governed by whatever
                         permission lets this viewer see the Overview tab at all; this modal adds
                         no new permission and no edit control of its own. --%>
                    <div class="kcpc-modal-overlay hidden" id="scriptDescriptionModalOverlay">
                        <div class="kcpc-modal" role="dialog" aria-modal="true" aria-labelledby="scriptDescriptionModalTitle">
                            <div class="kcpc-modal-header">
                                <h3 id="scriptDescriptionModalTitle">&#128221; Script Description</h3>
                                <button type="button" class="kcpc-modal-close" id="scriptDescriptionModalClose" aria-label="Close">&times;</button>
                            </div>
                            <div class="kcpc-modal-body">
                                <pre class="script-description-text"><c:out value="${plan.idea.notesRemarks}"/></pre>
                            </div>
                        </div>
                    </div>
                </c:if>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Priority</span>
                    <span class="content-detail-field-value"><c:out value="${empty plan.contentPriority ? '—' : plan.contentPriority}"/></span>
                </div>

                <h4 class="content-detail-section-heading">People</h4>
                <%-- Each contributor's mark is the DECIDED role-level value (marks.predefinedXMark,
                     from PredefinedRoleMarks - set at Idea Review approval, in the same transaction
                     that creates this ContentPlan), shown identically to every contributor currently
                     assigned to that role - available immediately on assignment, never waiting on
                     that stage's own submission/review. Not PersonalMarkAttribution (that's the
                     later, per-person post-approval award - a different concept). No PredefinedRoleMarks
                     field exists for Publisher, so that row has no decided-mark source and stays "-". --%>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Model(s)</span>
                    <span class="content-detail-field-value">
                        <c:forEach var="t" items="${talentEntries}" varStatus="ts"><c:out value="${t.talentName}"/><span class="content-detail-people-mark"><c:choose><c:when test="${not empty marks and not empty marks.predefinedModelMark}"><c:out value="${marks.predefinedModelMark}"/></c:when><c:otherwise>&mdash;</c:otherwise></c:choose></span><c:if test="${!ts.last}">, </c:if></c:forEach>
                        <c:if test="${empty talentEntries}"><span class="muted">&mdash;</span></c:if>
                    </span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Camera Person(s)</span>
                    <span class="content-detail-field-value">
                        <c:forEach var="a" items="${shootingAssignments}" varStatus="s"><c:out value="${a.cameraperson.fullName}"/><c:if test="${a.lead}"> (Lead)</c:if><span class="content-detail-people-mark"><c:choose><c:when test="${not empty marks and not empty marks.predefinedCameramanMark}"><c:out value="${marks.predefinedCameramanMark}"/></c:when><c:otherwise>&mdash;</c:otherwise></c:choose></span><c:if test="${!s.last}">, </c:if></c:forEach>
                        <c:if test="${empty shootingAssignments}"><span class="muted">&mdash;</span></c:if>
                    </span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Editor(s)</span>
                    <span class="content-detail-field-value">
                        <c:forEach var="a" items="${editingAssignments}" varStatus="s"><c:out value="${a.editor.fullName}"/><c:if test="${a.lead}"> (Lead)</c:if><span class="content-detail-people-mark"><c:choose><c:when test="${not empty marks and not empty marks.predefinedEditorMark}"><c:out value="${marks.predefinedEditorMark}"/></c:when><c:otherwise>&mdash;</c:otherwise></c:choose></span><c:if test="${!s.last}">, </c:if></c:forEach>
                        <c:if test="${empty editingAssignments}"><span class="muted">&mdash;</span></c:if>
                    </span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Publisher(s)</span>
                    <span class="content-detail-field-value">
                        <c:forEach var="a" items="${publishingAssignments}" varStatus="s"><c:out value="${a.publisher.fullName}"/><c:if test="${!s.last}">, </c:if></c:forEach>
                        <c:if test="${empty publishingAssignments}"><span class="muted">&mdash;</span></c:if>
                    </span>
                </div>
            </div>
            <div>
                <h4 class="content-detail-section-heading">Planned Dates</h4>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Planned Shoot Date</span>
                    <span class="content-detail-field-value">${empty plan.plannedShootDate ? '—' : plan.plannedShootDate}</span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Planned Edit Date</span>
                    <span class="content-detail-field-value">${empty plan.plannedEditDate ? '—' : plan.plannedEditDate}</span>
                </div>
                <div class="content-detail-field-row content-detail-field-row-divider">
                    <span class="content-detail-field-label">Planned Live Date</span>
                    <span class="content-detail-field-value">${empty plan.plannedLiveDate ? '—' : plan.plannedLiveDate}</span>
                </div>

                <h4 class="content-detail-section-heading">Actual Dates</h4>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Actual Shoot Date</span>
                    <span class="content-detail-field-value">${empty actualShootDate ? '—' : actualShootDate}</span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Actual Edit Date</span>
                    <span class="content-detail-field-value">${empty actualEditDate ? '—' : actualEditDate}</span>
                </div>
                <div class="content-detail-field-row">
                    <span class="content-detail-field-label">Actual Live Date</span>
                    <span class="content-detail-field-value">${empty actualLiveDate ? '—' : actualLiveDate}</span>
                </div>

                <h4 class="content-detail-section-heading">Platform / Channel Summary</h4>
                <c:forEach var="ps" items="${platformSummaries}">
                    <div class="content-detail-field-row">
                        <span class="content-detail-field-label"><c:out value="${ps.platformName}"/></span>
                        <span class="content-detail-field-value">
                            <c:forEach var="ch" items="${ps.channels}" varStatus="chIdx">@<c:out value="${ch.channelHandle}"/><c:if test="${!chIdx.last}">, </c:if></c:forEach>
                        </span>
                    </div>
                </c:forEach>
                <c:if test="${empty platformSummaries}"><p class="muted">No Platforms planned yet.</p></c:if>
            </div>
            </div>
        </div>

        <%-- ENG-082 (visual polish pass): compact Timeline/Activity preview - the latest few REAL
             WorkflowTransitionHistory entries (the exact same data source the Timeline tab itself
             uses, just the first N of it), so the Overview tab isn't left with a large empty band
             below the summary card. "View full timeline" reuses my-work-tabs.js's own tab-switch
             mechanism by simply clicking the existing Timeline tab button - no duplicated tab logic,
             no page reload. Only real transition data is shown; no comment/audit event types are
             invented since this page has no unified comment+audit feed merged into `timeline`. --%>
        <div class="panel content-detail-timeline-preview">
            <h3 class="content-detail-card-title">Timeline / Activity</h3>
            <ul class="content-detail-timeline-list">
                <c:forEach var="t" items="${timeline}" end="4">
                    <li class="content-detail-timeline-item">
                        <span class="content-detail-timeline-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/></svg>
                        </span>
                        <div class="content-detail-timeline-body">
                            <div class="content-detail-timeline-title-row">
                                <span class="content-detail-timeline-title"><c:out value="${t.toStatusCode.statusName}"/></span>
                                <span class="content-detail-timeline-badge">Milestone</span>
                            </div>
                            <p class="content-detail-timeline-desc">
                                <c:choose>
                                    <c:when test="${not empty t.transitionReason}"><c:out value="${t.transitionReason}"/></c:when>
                                    <c:otherwise><c:out value="${t.fromStatusCode.statusName}"/> &rarr; <c:out value="${t.toStatusCode.statusName}"/></c:otherwise>
                                </c:choose>
                            </p>
                        </div>
                        <div class="content-detail-timeline-meta">
                            <span class="content-detail-timeline-time">${kcpc:ist(t.transitionTimestamp)}</span>
                            <span class="content-detail-timeline-actor"><c:out value="${t.triggeredBy.fullName}"/></span>
                        </div>
                    </li>
                </c:forEach>
                <c:if test="${empty timeline}"><li class="muted">No activity yet.</li></c:if>
            </ul>
            <c:if test="${not empty timeline}">
                <button type="button" class="content-detail-view-full-timeline" id="contentDetailViewFullTimeline">View full timeline &rsaquo;</button>
            </c:if>
        </div>

        <%-- BR-063 Hold/Resume: kept as its own list, never merged into the real transition
             Timeline above (Hold is deliberately not a status transition, ERD-CON-061) - every
             cycle stays individually visible and immutable (ERD-CON-062), so a later Hold never
             overwrites an earlier one's reason. --%>
        <c:if test="${not empty holdHistory}">
            <div class="panel">
                <h3 class="content-detail-card-title">Hold History</h3>
                <ul class="content-detail-timeline-list">
                    <c:forEach var="h" items="${holdHistory}">
                        <li class="content-detail-timeline-item">
                            <div class="content-detail-timeline-body">
                                <div class="content-detail-timeline-title-row">
                                    <span class="content-detail-timeline-title">Task put on Hold</span>
                                    <c:if test="${empty h.resumedAt}"><span class="flag-chip flag-hold">Open</span></c:if>
                                </div>
                                <p class="content-detail-timeline-desc">
                                    Held by <c:out value="${h.heldBy.fullName}"/> &middot; Reason: <c:out value="${h.holdReason}"/>
                                </p>
                                <c:if test="${not empty h.resumedAt}">
                                    <p class="content-detail-timeline-desc">
                                        Task Resumed by <c:out value="${h.resumedBy.fullName}"/>
                                    </p>
                                </c:if>
                            </div>
                            <div class="content-detail-timeline-meta">
                                <span class="content-detail-timeline-time">${kcpc:ist(h.heldAt)}</span>
                                <c:if test="${not empty h.resumedAt}">
                                    <span class="content-detail-timeline-time">Resumed ${kcpc:ist(h.resumedAt)}</span>
                                </c:if>
                            </div>
                        </li>
                    </c:forEach>
                </ul>
            </div>
        </c:if>
    </div>

    <%-- ============================ SHOOT ============================ --%>
    <c:if test="${canSeeShootTab}">
    <div class="my-work-tab-panel ${activeTab == 'shoot' ? '' : 'hidden'}" data-tab-panel="shoot">
        <div class="panel">
            <h2>Shoot</h2>
            <c:choose>
                <c:when test="${status == 'SA'}">
                    <%-- Shoot Assignment Management: the single canonical UI for Shoot team setup,
                         for CEO/MM and delegated PERM_04 employees alike - a self-contained,
                         immediately-effective chip-picker (identical pattern to the Edit tab's own
                         Editor picker). Workflow redesign: an initial Shoot Team is already assigned
                         at Idea Review approval time, but it stays adjustable here up until Shoot
                         execution actually starts (status SA), exactly as it always was pre-redesign
                         (just gated on SA now instead of PL). --%>
                    <c:choose>
                        <c:when test="${canAssignCameraperson}">
                            <h3 class="stage-block-heading">Shoot Assignment Management</h3>
                            <p class="muted">Adjust the Shoot team before shoot execution begins.</p>
                            <div class="kcpc-assignment-picker"
                                 data-add-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting-assignments/team"
                                 data-remove-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting-assignments/remove"
                                 data-param-name="cameramanUserId">
                                <div class="assignment-picker-grid">
                                    <div class="assignment-picker-field">
                                        <label>Shoot Assignee(s)</label>
                                        <div class="kcpc-model-input">
                                            <div class="kcpc-model-chips">
                                                <c:forEach var="a" items="${shootingAssignments}">
                                                    <form class="chip-remove-form" method="post"
                                                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting-assignments/remove">
                                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                        <input type="hidden" name="cameramanUserId" value="${a.cameraperson.id}"/>
                                                        <span class="model-chip" data-user-id="${a.cameraperson.id}" data-name="${a.cameraperson.fullName}">
                                                            ${a.cameraperson.fullName}
                                                            <button type="submit" class="chip-remove" title="Remove ${a.cameraperson.fullName}">&times;</button>
                                                        </span>
                                                    </form>
                                                </c:forEach>
                                            </div>
                                            <input type="text" class="kcpc-model-search" placeholder="Search eligible shoot assignee...">
                                        </div>
                                    </div>
                                    <form class="assignment-add-form" method="post"
                                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting-assignments/team">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <div class="assignment-picker-field">
                                            <label>Shoot Lead
                                                <select name="leadUserId" class="kcpc-lead-select" ${empty shootingAssignments ? 'disabled' : ''}>
                                                    <option value="">&mdash; None &mdash;</option>
                                                    <c:forEach var="a" items="${shootingAssignments}">
                                                        <option value="${a.cameraperson.id}" ${a.lead ? 'selected' : ''}>${a.cameraperson.fullName}</option>
                                                    </c:forEach>
                                                </select>
                                            </label>
                                        </div>
                                        <div class="kcpc-model-checklist">
                                            <c:forEach var="u" items="${camerapersonUsers}">
                                                <c:set var="isAssigned" value="false"/>
                                                <c:forEach var="a" items="${shootingAssignments}">
                                                    <c:if test="${a.cameraperson.id == u.id}"><c:set var="isAssigned" value="true"/></c:if>
                                                </c:forEach>
                                                <c:if test="${!isAssigned}">
                                                    <label class="model-check-item">
                                                        <input type="checkbox" name="cameramanUserIds" value="${u.id}" data-name="${u.fullName}"> ${u.fullName}
                                                        <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                                                    </label>
                                                </c:if>
                                            </c:forEach>
                                        </div>
                                        <button type="submit" class="assignment-add-submit">Assign Shoot Team</button>
                                    </form>
                                </div>
                            </div>
                            <div class="stage-description" data-empty-text="No instructions yet.">
                                <c:choose>
                                    <c:when test="${canEditShootDescription}">
                                        <div class="stage-description-header">
                                            <h3 class="stage-block-heading">Shoot Instructions</h3>
                                            <button type="button" class="stage-description-edit-btn">&#9998; Edit</button>
                                        </div>
                                        <p class="stage-description-text stage-description-view"><c:out value="${empty plan.shootDescription ? 'No instructions yet.' : plan.shootDescription}"/></p>
                                        <form class="action-form stage-description-form hidden" method="post"
                                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/description">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <textarea name="description" rows="3"
                                                      placeholder="Instructions for the Cameraperson team..."><c:out value="${plan.shootDescription}"/></textarea>
                                            <div class="review-actions">
                                                <button type="button" class="stage-description-cancel-btn">Cancel</button>
                                                <button type="submit">Save</button>
                                            </div>
                                        </form>
                                    </c:when>
                                    <c:otherwise>
                                        <h3 class="stage-block-heading">Shoot Instructions</h3>
                                        <p class="stage-description-text ${empty plan.shootDescription ? 'muted' : ''}"><c:out value="${empty plan.shootDescription ? 'No instructions yet.' : plan.shootDescription}"/></p>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <p class="muted">Shoot has not started yet.</p>
                        </c:otherwise>
                    </c:choose>
                </c:when>
                <c:when test="${cdStageIndex < 1}">
                    <p class="muted">Shoot has not started yet.</p>
                </c:when>
                <c:when test="${status == 'SA' or status == 'SIP' or status == 'SRV'}">
                    <%-- Currently in Shoot - the existing interactive workspace, unchanged. --%>
                    <div class="content-summary-grid">
                        <div><span class="summary-field-label">Content ID</span><span class="summary-field-value"><c:out value="${plan.contentId}"/></span></div>
                        <div><span class="summary-field-label">Content Name</span><span class="summary-field-value"><c:out value="${plan.idea.title}"/></span></div>
                        <div><span class="summary-field-label">Priority</span><span class="summary-field-value">
                            <c:if test="${not empty plan.contentPriority}">
                                <span class="priority-pill priority-${plan.contentPriority == 'HIGH' ? 'high' : (plan.contentPriority == 'MEDIUM' ? 'medium' : 'low')}"><c:out value="${plan.contentPriority}"/></span>
                            </c:if>
                        </span></div>
                        <div><span class="summary-field-label">Planned Shoot Date</span><span class="summary-field-value">${plan.plannedShootDate}</span></div>
                        <div><span class="summary-field-label">Model(s)</span><span class="summary-field-value">
                            <c:forEach var="t" items="${talentEntries}" varStatus="ts"><c:out value="${t.talentName}"/><c:if test="${!ts.last}">, </c:if></c:forEach>
                            <c:if test="${empty talentEntries}">&mdash;</c:if>
                        </span></div>
                        <div><span class="summary-field-label">Shoot Lead</span><span class="summary-field-value">
                            <c:set var="shootLeadOnPage" value="" />
                            <c:forEach var="a" items="${shootingAssignments}"><c:if test="${a.lead}"><c:set var="shootLeadOnPage" value="${a.cameraperson.fullName}" /></c:if></c:forEach>
                            <c:out value="${empty shootLeadOnPage ? '—' : shootLeadOnPage}"/>
                        </span></div>
                        <div><span class="summary-field-label">Drive Link</span><span class="summary-field-value">
                            <c:choose>
                                <c:when test="${not empty plan.folderLink}"><a class="drive-link" href="${plan.folderLink}" target="_blank" rel="noopener noreferrer">Open Drive &#8599;</a></c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </span></div>
                        <div><span class="summary-field-label">Status</span><span class="summary-field-value">${status.statusName}</span></div>
                    </div>
                    <c:if test="${not empty shootReworkFeedback}">
                        <div class="rework-feedback-box">
                            <strong>Rework Feedback</strong>
                            <p><c:out value="${shootReworkFeedback}"/></p>
                        </div>
                    </c:if>
                    <c:if test="${status == 'SA' and isShootActiveAssignee}">
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/start">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit">Start Shoot</button>
                        </form>
                    </c:if>
                    <c:if test="${status == 'SIP'}">
                        <c:if test="${isShootActiveAssignee and empty openHold}">
                            <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/review/submit">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <p class="muted">Requires the Drive Link to be set at Idea Review.</p>
                                <button type="submit">Submit for Shoot Review</button>
                            </form>
                        </c:if>
                        <c:if test="${not empty openHold}">
                            <p class="note-box">On Hold since ${openHold.heldAt} — ${openHold.holdReason}</p>
                        </c:if>
                    </c:if>
                    <c:if test="${status == 'SRV'}">
                        <c:choose>
                            <c:when test="${canDecideShootReview}">
                                <form method="post" id="shoot-review-decision-form" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/review/decision">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <div class="review-decision-grid">
                                        <label>Decision
                                            <select name="approve">
                                                <option value="true">Approve</option>
                                                <option value="false">Request Rework</option>
                                            </select>
                                        </label>
                                        <div class="qualifying-picker-field kcpc-model-picker">
                                            <label>Qualifying Cameraperson(s)</label>
                                            <div class="kcpc-model-input">
                                                <div class="kcpc-model-chips"></div>
                                                <input type="text" class="kcpc-model-search" placeholder="Search cameraperson...">
                                            </div>
                                            <div class="kcpc-model-checklist">
                                                <c:forEach var="p" items="${shootingParticipants}">
                                                    <label class="model-check-item">
                                                        <input type="checkbox" name="qualifyingRecipientUserIds"
                                                               value="${p.cameraperson.id}" data-name="${p.cameraperson.fullName}"> ${p.cameraperson.fullName}
                                                    </label>
                                                </c:forEach>
                                            </div>
                                        </div>
                                        <label>Reason
                                            <input type="text" name="reason" placeholder="Describe required changes...">
                                        </label>
                                    </div>
                                    <p class="note-box">Each confirmed contributor receives the FULL predefined Cameraperson mark (no split).</p>
                                    <%-- Editor Assignment: only meaningful on Approve (workflow redesign - folds
                                         Editor team assignment into this same decision), harmlessly ignored by the
                                         server on Request Rework. Not marked HTML-required since this one <form>
                                         covers both decisions - the server enforces it only for Approve. --%>
                                    <div class="review-decision-grid">
                                        <div class="qualifying-picker-field kcpc-model-picker">
                                            <label>Editor(s) (Approve only)</label>
                                            <div class="kcpc-model-input">
                                                <div class="kcpc-model-chips"></div>
                                                <input type="text" class="kcpc-model-search" placeholder="Search editor...">
                                            </div>
                                            <div class="kcpc-model-checklist">
                                                <c:forEach var="eu" items="${videoEditorUsers}">
                                                    <label class="model-check-item">
                                                        <input type="checkbox" name="editorUserIds" value="${eu.id}" data-name="${eu.fullName}"> ${eu.fullName}
                                                    </label>
                                                </c:forEach>
                                            </div>
                                            <label>Editor Lead (Approve only)
                                                <select name="leadEditorUserId" class="kcpc-lead-select" disabled>
                                                    <option value="">- None -</option>
                                                </select>
                                            </label>
                                        </div>
                                    </div>
                                    <div class="review-actions">
                                        <button type="submit">Submit Decision</button>
                                    </div>
                                </form>
                            </c:when>
                            <c:otherwise>
                                <p class="note-box">
                                    <c:choose>
                                        <c:when test="${shootSelfReviewBlocked}">You participated in this shoot — the whole decision block is disabled.</c:when>
                                        <c:otherwise>You do not hold Shoot Review authority.</c:otherwise>
                                    </c:choose>
                                </p>
                            </c:otherwise>
                        </c:choose>
                    </c:if>

                    <div class="stage-description" data-empty-text="No instructions yet.">
                        <c:choose>
                            <c:when test="${canEditShootDescription}">
                                <div class="stage-description-header">
                                    <h3 class="stage-block-heading">Shoot Instructions</h3>
                                    <button type="button" class="stage-description-edit-btn">&#9998; Edit</button>
                                </div>
                                <p class="stage-description-text stage-description-view"><c:out value="${empty plan.shootDescription ? 'No instructions yet.' : plan.shootDescription}"/></p>
                                <form class="action-form stage-description-form hidden" method="post"
                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/description">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <textarea name="description" rows="3"
                                              placeholder="Instructions for the Cameraperson team..."><c:out value="${plan.shootDescription}"/></textarea>
                                    <div class="review-actions">
                                        <button type="button" class="stage-description-cancel-btn">Cancel</button>
                                        <button type="submit">Save</button>
                                    </div>
                                </form>
                            </c:when>
                            <c:otherwise>
                                <h3 class="stage-block-heading">Shoot Instructions</h3>
                                <p class="stage-description-text ${empty plan.shootDescription ? 'muted' : ''}"><c:out value="${empty plan.shootDescription ? 'No instructions yet.' : plan.shootDescription}"/></p>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:when>
                <c:otherwise>
                    <%-- ENG-082: Shoot already approved and the plan has moved on - compact recap. --%>
                    <p><strong>Camera Person(s):</strong>
                        <c:forEach var="a" items="${shootingAssignments}" varStatus="s"><c:out value="${a.cameraperson.fullName}"/><c:if test="${a.lead}"> (Lead)</c:if><c:if test="${!s.last}">, </c:if></c:forEach>
                        <c:if test="${empty shootingAssignments}"><span class="muted">&mdash;</span></c:if>
                    </p>
                    <p><strong>Planned Shoot Date:</strong> ${empty plan.plannedShootDate ? '—' : plan.plannedShootDate}</p>
                    <p><strong>Shoot Instructions:</strong> <c:out value="${empty plan.shootDescription ? '—' : plan.shootDescription}"/></p>
                    <p><strong>Status:</strong> Approved</p>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- Shoot Comments only becomes visible once Shoot has actually been reached (cdStageIndex
             >= 1, the same threshold the stepper/tab-content gate above already uses) - a future
             stage's comment history/composer must never be shown early. --%>
        <c:if test="${cdStageIndex >= 1}">
        <div class="panel content-detail-stage-comments-block">
            <h3 class="content-detail-card-title">Shoot Comments</h3>
            <c:set var="stageCommentsPath" value="shooting"/>
            <c:set var="stageCommentsList" value="${shootComments}"/>
            <c:set var="stageCommentsCanPost" value="${canCommentOnShoot}"/>
            <%@ include file="fragments/stage-comments-block.jspf" %>
        </div>
        </c:if>
    </div>

    </c:if>

    <%-- ============================ EDIT ============================ --%>
    <c:if test="${canSeeEditTab}">
    <div class="my-work-tab-panel ${activeTab == 'edit' ? '' : 'hidden'}" data-tab-panel="edit">
        <div class="panel">
            <h2>Edit</h2>
            <c:choose>
                <c:when test="${cdStageIndex < 1 or (cdStageIndex == 1 and status != 'SAP')}">
                    <p class="muted">Edit has not started yet.</p>
                </c:when>
                <c:when test="${status == 'SAP' or status == 'EA' or status == 'ED' or status == 'ERV'}">
                    <%-- Currently in Edit (or Shoot Approved, about to start Edit) - unchanged workspace. --%>
                    <c:if test="${(status == 'SAP' or status == 'EA') and canAssignEditor}">
                        <p class="note-box">Editor assignment is available only after Shoot Approval. Assigning the first
                            Editor activates Edit (status moves to Edit Assigned) - further Editors can still be added here
                            afterward.</p>
                        <div class="kcpc-assignment-picker"
                             data-add-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/assignments/team"
                             data-remove-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/assignments/remove"
                             data-param-name="editorUserId">
                            <div class="assignment-picker-grid">
                                <div class="assignment-picker-field">
                                    <label>Editor(s)</label>
                                    <div class="kcpc-model-input">
                                        <div class="kcpc-model-chips">
                                            <c:forEach var="a" items="${editingAssignments}">
                                                <form class="chip-remove-form" method="post"
                                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/assignments/remove">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                    <input type="hidden" name="editorUserId" value="${a.editor.id}"/>
                                                    <span class="model-chip" data-user-id="${a.editor.id}" data-name="${a.editor.fullName}">
                                                        ${a.editor.fullName}
                                                        <button type="submit" class="chip-remove" title="Remove ${a.editor.fullName}">&times;</button>
                                                    </span>
                                                </form>
                                            </c:forEach>
                                        </div>
                                        <input type="text" class="kcpc-model-search" placeholder="Search editor...">
                                    </div>
                                </div>
                                <form class="assignment-add-form" method="post"
                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/assignments/team">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <div class="assignment-picker-field">
                                        <label>Edit Lead
                                            <select name="leadUserId" class="kcpc-lead-select" ${empty editingAssignments ? 'disabled' : ''}>
                                                <option value="">&mdash; None &mdash;</option>
                                                <c:forEach var="a" items="${editingAssignments}">
                                                    <option value="${a.editor.id}" ${a.lead ? 'selected' : ''}>${a.editor.fullName}</option>
                                                </c:forEach>
                                            </select>
                                        </label>
                                    </div>
                                    <div class="kcpc-model-checklist">
                                        <c:forEach var="u" items="${videoEditorUsers}">
                                            <c:set var="isAssigned" value="false"/>
                                            <c:forEach var="a" items="${editingAssignments}">
                                                <c:if test="${a.editor.id == u.id}"><c:set var="isAssigned" value="true"/></c:if>
                                            </c:forEach>
                                            <c:if test="${!isAssigned}">
                                                <label class="model-check-item">
                                                    <input type="checkbox" name="editorUserIds" value="${u.id}" data-name="${u.fullName}"> ${u.fullName}
                                                    <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                                                </label>
                                            </c:if>
                                        </c:forEach>
                                    </div>
                                    <button type="submit" class="assignment-add-submit">Assign Editor(s)</button>
                                </form>
                            </div>
                        </div>
                    </c:if>
                    <c:if test="${status == 'EA' and isEditActiveAssignee}">
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/start">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit">Start Edit</button>
                        </form>
                    </c:if>
                    <c:if test="${status == 'ED'}">
                        <c:if test="${isEditActiveAssignee and empty openHold}">
                            <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/review/submit">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <button type="submit">Submit for Edit Review</button>
                            </form>
                        </c:if>
                        <c:if test="${not empty openHold}">
                            <p class="note-box">On Hold since ${openHold.heldAt} — ${openHold.holdReason}</p>
                        </c:if>
                    </c:if>
                    <c:if test="${status == 'ERV'}">
                        <c:choose>
                            <c:when test="${canDecideEditReview}">
                                <form method="post" id="edit-review-decision-form" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/review/decision">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <div class="review-decision-grid">
                                        <label>Decision
                                            <select name="approve">
                                                <option value="true">Approve</option>
                                                <option value="false">Request Rework</option>
                                            </select>
                                        </label>
                                        <div class="qualifying-picker-field kcpc-model-picker">
                                            <label>Qualifying Editor(s)</label>
                                            <div class="kcpc-model-input">
                                                <div class="kcpc-model-chips"></div>
                                                <input type="text" class="kcpc-model-search" placeholder="Search editor...">
                                            </div>
                                            <div class="kcpc-model-checklist">
                                                <c:forEach var="p" items="${editingParticipants}">
                                                    <label class="model-check-item">
                                                        <input type="checkbox" name="qualifyingRecipientUserIds"
                                                               value="${p.editor.id}" data-name="${p.editor.fullName}"> ${p.editor.fullName}
                                                    </label>
                                                </c:forEach>
                                            </div>
                                        </div>
                                        <label>Reason
                                            <input type="text" name="reason" placeholder="Describe required changes...">
                                        </label>
                                    </div>
                                    <p class="note-box">Each confirmed contributor receives the FULL predefined Editor mark (no split).</p>
                                    <%-- Publisher Assignment: only meaningful on Approve (workflow redesign - folds
                                         Publisher team assignment into this same decision), harmlessly ignored by
                                         the server on Request Rework. Publisher(s) only - no Lead concept for
                                         Publishing (explicit product decision - see ENG-036/ENG-044). --%>
                                    <div class="review-decision-grid">
                                        <div class="qualifying-picker-field kcpc-model-picker">
                                            <label>Publisher(s) (Approve only)</label>
                                            <div class="kcpc-model-input">
                                                <div class="kcpc-model-chips"></div>
                                                <input type="text" class="kcpc-model-search" placeholder="Search publisher...">
                                            </div>
                                            <div class="kcpc-model-checklist">
                                                <%-- ENG-097: pre-check a Publisher already assigned from Planning time. --%>
                                                <c:forEach var="pu" items="${publisherUsers}">
                                                    <label class="model-check-item">
                                                        <input type="checkbox" name="publisherUserIds" value="${pu.id}" data-name="${pu.fullName}"
                                                               <c:if test="${alreadyAssignedPublisherUserIds.contains(pu.id)}">checked</c:if>> ${pu.fullName}
                                                        <c:if test="${alreadyAssignedPublisherUserIds.contains(pu.id)}"><span class="muted"> (already assigned)</span></c:if>
                                                    </label>
                                                </c:forEach>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="review-actions">
                                        <button type="submit">Submit Decision</button>
                                    </div>
                                </form>
                            </c:when>
                            <c:otherwise>
                                <p class="note-box">
                                    <c:choose>
                                        <c:when test="${editSelfReviewBlocked}">You participated in this edit — the whole decision block is disabled.</c:when>
                                        <c:otherwise>You do not hold Edit Review authority.</c:otherwise>
                                    </c:choose>
                                </p>
                            </c:otherwise>
                        </c:choose>
                    </c:if>

                    <div class="stage-description" data-empty-text="No description yet.">
                        <c:choose>
                            <c:when test="${canEditEditDescription}">
                                <div class="stage-description-header">
                                    <h3 class="stage-block-heading">Edit Description</h3>
                                    <button type="button" class="stage-description-edit-btn">&#9998; Edit</button>
                                </div>
                                <p class="stage-description-text stage-description-view"><c:out value="${empty plan.editDescription ? 'No description yet.' : plan.editDescription}"/></p>
                                <form class="action-form stage-description-form hidden" method="post"
                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/description">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <textarea name="description" rows="3"
                                              placeholder="Instructions for the Editor team..."><c:out value="${plan.editDescription}"/></textarea>
                                    <div class="review-actions">
                                        <button type="button" class="stage-description-cancel-btn">Cancel</button>
                                        <button type="submit">Save</button>
                                    </div>
                                </form>
                            </c:when>
                            <c:otherwise>
                                <h3 class="stage-block-heading">Edit Description</h3>
                                <p class="stage-description-text ${empty plan.editDescription ? 'muted' : ''}"><c:out value="${empty plan.editDescription ? 'No description yet.' : plan.editDescription}"/></p>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:when>
                <c:otherwise>
                    <%-- ENG-082: Edit already approved - compact recap. --%>
                    <p><strong>Editor(s):</strong>
                        <c:forEach var="a" items="${editingAssignments}" varStatus="s"><c:out value="${a.editor.fullName}"/><c:if test="${a.lead}"> (Lead)</c:if><c:if test="${!s.last}">, </c:if></c:forEach>
                        <c:if test="${empty editingAssignments}"><span class="muted">&mdash;</span></c:if>
                    </p>
                    <p><strong>Planned Edit Date:</strong> ${empty plan.plannedEditDate ? '—' : plan.plannedEditDate}</p>
                    <p><strong>Edit Description:</strong> <c:out value="${empty plan.editDescription ? '—' : plan.editDescription}"/></p>
                    <p><strong>Status:</strong> Approved</p>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- Edit Comments only becomes visible once Edit has actually been reached (cdStageIndex
             >= 2) - Shoot Comments above remain visible as reached-stage history. --%>
        <c:if test="${cdStageIndex >= 2}">
        <div class="panel content-detail-stage-comments-block">
            <h3 class="content-detail-card-title">Edit Comments</h3>
            <c:set var="stageCommentsPath" value="editing"/>
            <c:set var="stageCommentsList" value="${editComments}"/>
            <c:set var="stageCommentsCanPost" value="${canCommentOnEdit}"/>
            <%@ include file="fragments/stage-comments-block.jspf" %>
        </div>
        </c:if>
    </div>

    </c:if>

    <%-- ============================ PUBLISHING ============================ --%>
    <c:if test="${canSeePublishingTab}">
    <div class="my-work-tab-panel ${activeTab == 'publishing' ? '' : 'hidden'}" data-tab-panel="publishing">
        <div class="panel">
            <h2>Publishing</h2>

            <%-- ENG-082: Platform x Channel summary - full-color icon chips, popover per platform,
                 reusing the Content Pipeline dashboard's own fragment/CSS/data verbatim (ENG-075/076). --%>
            <h3 class="stage-block-heading">Platform &times; Channel</h3>
            <div class="pipeline-platform-chips content-detail-platform-chips">
                <c:forEach var="summary" items="${platformSummaries}" varStatus="ps">
                    <c:set var="popoverId" value="cd-platform-popover-${plan.id}-${ps.index}"/>
                    <%@ include file="fragments/pipeline-platform-chip.jspf" %>
                </c:forEach>
                <c:if test="${empty platformSummaries}"><span class="muted">No Platforms planned yet.</span></c:if>
            </div>

            <%-- Workflow redesign: Outputs/Reel Type/Publication Scope management used to live in
                 the Planning tab - that tab is gone (Planning is no longer a separate stage), but
                 addPlannedOutput(s)/mapPublicationScope/syncReelGroup/unmapPublicationTarget carry no
                 status gate and remain fully usable for ongoing management, so their UI now lives
                 here instead, unchanged and still PERM_02-gated. --%>
            <c:if test="${canPlanningExecute}">
            <h3 class="stage-block-heading">Manage Outputs &amp; Publication Scope</h3>
            <table class="data-table" id="planned-outputs-table"
                   data-context-path="${pageContext.request.contextPath}" data-plan-id="${plan.id}">
                <thead><tr><th>Output</th><th>Type</th><th>Publication Targets</th><th>Action</th></tr></thead>
                <tbody id="planned-outputs-tbody">
                <c:forEach var="o" items="${outputGroupRepresentatives}">
                    <c:set var="groupMembers" value="${outputGroupMembers[o.reelGroupId]}" />
                    <c:set var="mappingCount" value="${outputTargetMappings[o.reelGroupId].size()}" />
                    <tr data-group-id="${o.reelGroupId}">
                        <td class="output-title-cell">${empty o.titleDescription ? '—' : o.titleDescription}</td>
                        <td class="output-type-cell">${o.outputType}
                            <c:if test="${o.outputType == 'REEL'}">
                                <c:forEach var="member" items="${groupMembers}">
                                    <span class="reeltype-chip">${member.reelType}</span>
                                </c:forEach>
                            </c:if>
                        </td>
                        <td>
                            <div class="target-list" data-group-id="${o.reelGroupId}">
                                <c:choose>
                                    <c:when test="${mappingCount == 0}">
                                        <span class="muted-summary">(none yet)</span>
                                    </c:when>
                                    <c:otherwise>
                                        <c:forEach var="grp" items="${outputTargetsByPlatform[o.reelGroupId]}">
                                            <div class="target-row">
                                                <span class="target-platform">${grp.key}</span>
                                                <span class="target-channels">
                                                    <c:forEach var="m" items="${grp.value}">
                                                        <c:set var="cdTargetPlatform" value="${m.publicationTarget.platform.platformName}"/>
                                                        <%@ include file="fragments/scope-target-icon.jspf" %>
                                                        <span class="channel-chip" data-target-id="${m.publicationTarget.id}"
                                                              data-channel-handle="${m.publicationTarget.channel.channelHandle}">
                                                            <img class="scope-target-icon" src="${cdTargetIconSrc}" alt="" width="14" height="14"/>
                                                            ${m.publicationTarget.channel.channelHandle}
                                                            <c:choose>
                                                                <c:when test="${publishedMappingIds.contains(m.id)}">
                                                                    <span class="scope-locked" title="Already published - cannot be removed">&#128274;</span>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <form method="post" class="chip-remove-form"
                                                                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.reelGroupId}/targets/${m.publicationTarget.id}/remove">
                                                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                                        <button type="submit" class="chip-remove"
                                                                                title="Remove ${m.publicationTarget.channel.channelHandle}">&times;</button>
                                                                    </form>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </span>
                                                    </c:forEach>
                                                </span>
                                            </div>
                                        </c:forEach>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </td>
                        <td>
                            <div class="action-stack">
                                <details>
                                    <summary>Edit</summary>
                                    <form class="action-form edit-output-form" method="post" data-group-id="${o.reelGroupId}"
                                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.reelGroupId}/edit">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <label>Output Type
                                            <%-- ${outputTypes} carries only the types still open to new
                                                 use (OutputType#selectableValues). An output created
                                                 before a type was retired still carries it, and would
                                                 otherwise render with NO option selected - the browser
                                                 would then submit the first type in the list, silently
                                                 converting a historical row on any unrelated edit (a
                                                 title fix, say). Its own value is therefore re-added as
                                                 a selected option so editing round-trips it unchanged.
                                                 Retired types stay absent from the "+ Add Output" form
                                                 below, which has no existing value to preserve. --%>
                                            <select class="kcpc-output-type-select" name="outputType">
                                                <c:if test="${not outputTypes.contains(o.outputType)}">
                                                    <option value="${o.outputType}" selected>${o.outputType}</option>
                                                </c:if>
                                                <c:forEach var="t" items="${outputTypes}">
                                                    <option value="${t}" ${t == o.outputType ? 'selected' : ''}>${t}</option>
                                                </c:forEach>
                                            </select>
                                        </label>
                                        <div class="kcpc-reeltype-group">
                                            <label>Reel Type (Reel only — select one or more)</label>
                                            <div class="kcpc-reeltype-checklist">
                                                <c:forEach var="rt" items="${reelTypes}">
                                                    <c:set var="checked" value="false" />
                                                    <c:forEach var="member" items="${groupMembers}">
                                                        <c:if test="${rt == member.reelType}"><c:set var="checked" value="true" /></c:if>
                                                    </c:forEach>
                                                    <label class="reeltype-check-item">
                                                        <input type="checkbox" name="reelTypes" value="${rt}" ${checked ? 'checked' : ''}> ${rt}
                                                    </label>
                                                </c:forEach>
                                            </div>
                                            <p class="muted">All Reel Types in this group share one Publication Target
                                                set below — per-Reel-Type targets aren't supported.</p>
                                        </div>
                                        <label>Description <input type="text" name="titleDescription" value="${o.titleDescription}"></label>
                                        <button type="submit">Save</button>
                                    </form>
                                    <form class="action-form add-target-form" method="post" data-group-id="${o.reelGroupId}"
                                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.reelGroupId}/targets">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <label>+ Add Target — Platform
                                            <select class="kcpc-platform-select">
                                                <option value="">Select Platform</option>
                                                <c:forEach var="platformName" items="${activePlatformNames}">
                                                    <option value="${platformName}">${platformName}</option>
                                                </c:forEach>
                                            </select>
                                        </label>
                                        <div class="kcpc-channel-group">
                                            <label>Channels</label>
                                            <div class="kcpc-channel-checklist">
                                                <c:forEach var="pt" items="${activePublicationTargets}">
                                                    <label class="channel-check-item" data-platform="${pt.platform.platformName}">
                                                        <input type="checkbox" name="publicationTargetIds" value="${pt.id}"
                                                               data-platform="${pt.platform.platformName}"
                                                               data-channel="${pt.channel.channelHandle}"> ${pt.channel.channelHandle}
                                                    </label>
                                                </c:forEach>
                                            </div>
                                        </div>
                                        <button type="submit">+ Add Target</button>
                                    </form>
                                </details>
                                <form class="action-form remove-output-form" method="post" data-group-id="${o.reelGroupId}"
                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.reelGroupId}/remove">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <button type="submit" class="secondary">Remove</button>
                                </form>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <tr id="planned-outputs-empty-row" class="${empty outputGroupRepresentatives ? '' : 'hidden'}">
                    <td colspan="4" class="muted">No Planned Outputs yet.</td>
                </tr>
                </tbody>
            </table>
            <script type="application/json" id="kcpc-planning-options">${planningOptionsJson}</script>
            <details>
                <summary>+ Add Output</summary>
                <form class="action-form add-output-form" method="post"
                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Output Type
                        <select class="kcpc-output-type-select" name="outputType">
                            <c:forEach var="t" items="${outputTypes}"><option value="${t}">${t}</option></c:forEach>
                        </select>
                    </label>
                    <div class="kcpc-reeltype-group">
                        <label>Reel Type (Reel only — select one or both)</label>
                        <div class="kcpc-reeltype-checklist">
                            <c:forEach var="rt" items="${reelTypes}">
                                <label class="reeltype-check-item">
                                    <input type="checkbox" name="reelTypes" value="${rt}"> ${rt}
                                </label>
                            </c:forEach>
                        </div>
                        <p class="muted">Selecting both Short and Long creates two separate Planned Outputs (one per
                            Reel Type), so each can be tracked and completed independently.</p>
                    </div>
                    <label>Description <input type="text" name="titleDescription"></label>
                    <button type="submit">Add Output</button>
                </form>
            </details>
            </c:if>

            <%-- Publishing Scope: read-only Output-based breakdown once Publishing has been reached,
                 kept in sync with the same outputTargetsByPlatform/publishedMappingIds data the
                 Assign Publisher(s) modal's verification table below also reads from - one source of
                 truth for "what's the current publishing scope", just two renderings (editable inside
                 the modal, read-only here). --%>
            <c:if test="${cdStageIndex >= 3}">
                <h3 class="stage-block-heading">Publishing Scope</h3>
                <table class="data-table content-detail-scope-table">
                    <thead><tr><th>Output</th><th>Type</th><th>Reel Type</th><th>Publication Targets</th><th>Current State</th></tr></thead>
                    <tbody>
                        <c:forEach var="o" items="${outputGroupRepresentatives}">
                            <tr>
                                <td>${empty o.titleDescription ? '—' : o.titleDescription}</td>
                                <td><span class="badge-type badge-type-${o.outputType}">${o.outputType}</span></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${o.outputType == 'REEL'}">
                                            <c:forEach var="member" items="${outputGroupMembers[o.reelGroupId]}">
                                                <span class="reeltype-chip">${member.reelType}</span>
                                            </c:forEach>
                                        </c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:forEach var="grp" items="${outputTargetsByPlatform[o.reelGroupId]}">
                                        <c:forEach var="m" items="${grp.value}">
                                            <c:set var="cdTargetPlatform" value="${m.publicationTarget.platform.platformName}"/>
                                            <%@ include file="fragments/scope-target-icon.jspf" %>
                                            <span class="scope-target-chip ${publishedMappingIds.contains(m.id) ? 'published' : 'planned'}">
                                                <img class="scope-target-icon" src="${cdTargetIconSrc}" alt="" width="14" height="14"/>
                                                <c:out value="${m.publicationTarget.channel.channelHandle}"/>
                                            </span>
                                        </c:forEach>
                                    </c:forEach>
                                    <c:if test="${empty outputTargetMappings[o.reelGroupId]}"><span class="muted-summary">(none yet)</span></c:if>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${empty outputTargetMappings[o.reelGroupId]}"><span class="muted">&mdash;</span></c:when>
                                        <c:when test="${outputHasPublishedTarget[o.reelGroupId]}"><span class="status-pill status-completed">Published</span></c:when>
                                        <c:otherwise><span class="status-pill status-pending">Planned</span></c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty outputGroupRepresentatives}">
                            <tr><td colspan="5" class="muted">No Planned Outputs yet.</td></tr>
                        </c:if>
                    </tbody>
                </table>
            </c:if>

            <c:choose>
                <c:when test="${cdStageIndex < 3}">
                    <p class="muted">Publishing has not started yet.</p>
                </c:when>
                <c:otherwise>
                    <c:if test="${status == 'RFP'}">
                        <c:if test="${isRepostPublishingCycle}"><span class="repost-cycle-badge">Repost Cycle</span></c:if>
                        <c:if test="${canAssignPublisher}">
                            <button type="button" id="publishingAssignmentModalOpen" class="content-detail-action-btn content-detail-action-primary">
                                <c:choose>
                                    <c:when test="${empty publishingAssignments}">Assign Publisher(s)</c:when>
                                    <c:otherwise>Manage Publisher(s) &amp; Publishing Scope</c:otherwise>
                                </c:choose>
                            </button>

                            <div class="kcpc-modal-overlay hidden" id="publishingAssignmentModalOverlay">
                                <div class="kcpc-modal" role="dialog" aria-modal="true" aria-labelledby="publishingAssignmentModalTitle">
                                    <div class="kcpc-modal-header">
                                        <h3 id="publishingAssignmentModalTitle">Assign Publisher &amp; Verify Publishing Scope
                                            <c:if test="${isRepostPublishingCycle}"><span class="repost-cycle-badge">Repost Cycle</span></c:if>
                                        </h3>
                                        <button type="button" class="kcpc-modal-close" id="publishingAssignmentModalClose" aria-label="Close">&times;</button>
                                    </div>
                                    <div class="kcpc-modal-body">
                                        <h4 class="content-detail-section-heading">1. Publisher(s)</h4>
                                        <div class="kcpc-assignment-picker"
                                             data-add-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing-assignments"
                                             data-remove-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing-assignments/remove"
                                             data-param-name="publisherUserId">
                                            <label>Publisher(s)</label>
                                            <div class="kcpc-model-input">
                                                <div class="kcpc-model-chips">
                                                    <c:forEach var="a" items="${publishingAssignments}">
                                                        <form class="chip-remove-form" method="post"
                                                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing-assignments/remove">
                                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                            <input type="hidden" name="publisherUserId" value="${a.publisher.id}"/>
                                                            <span class="model-chip" data-user-id="${a.publisher.id}" data-name="${a.publisher.fullName}">
                                                                ${a.publisher.fullName}
                                                                <button type="submit" class="chip-remove" title="Remove ${a.publisher.fullName}">&times;</button>
                                                            </span>
                                                        </form>
                                                    </c:forEach>
                                                </div>
                                                <input type="text" class="kcpc-model-search" placeholder="Search publisher...">
                                            </div>
                                            <form class="assignment-add-form" method="post" id="publishingAssignmentAddForm"
                                                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing-assignments">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                <div class="kcpc-model-checklist">
                                                    <c:forEach var="u" items="${publisherUsers}">
                                                        <c:set var="isAssigned" value="false"/>
                                                        <c:forEach var="a" items="${publishingAssignments}">
                                                            <c:if test="${a.publisher.id == u.id}"><c:set var="isAssigned" value="true"/></c:if>
                                                        </c:forEach>
                                                        <c:if test="${!isAssigned}">
                                                            <label class="model-check-item">
                                                                <input type="checkbox" name="publisherUserIds" value="${u.id}" data-name="${u.fullName}"> ${u.fullName}
                                                                <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                                                            </label>
                                                        </c:if>
                                                    </c:forEach>
                                                </div>
                                            </form>
                                        </div>

                                        <h4 class="content-detail-section-heading">2. Publishing Scope Verification</h4>
                                        <p class="muted">Already-planned outputs and their approved Platform &times; Channel
                                            targets, for verification. Remove a target and add its replacement below to
                                            change a Platform/Channel. Published targets are locked.</p>
                                        <ul class="scope-change-log hidden" id="publishingScopeChangeLog"></ul>
                                        <table class="data-table content-detail-scope-table" id="publishingScopeVerifyTable">
                                            <thead><tr><th>Output</th><th>Type</th><th>Reel Type</th><th>Publication Targets</th><th>Action</th></tr></thead>
                                            <tbody>
                                                <c:forEach var="o" items="${outputGroupRepresentatives}">
                                                    <tr data-group-id="${o.reelGroupId}">
                                                        <td>${empty o.titleDescription ? '—' : o.titleDescription}</td>
                                                        <td><span class="badge-type badge-type-${o.outputType}">${o.outputType}</span></td>
                                                        <td>
                                                            <c:choose>
                                                                <c:when test="${o.outputType == 'REEL'}">
                                                                    <c:forEach var="member" items="${outputGroupMembers[o.reelGroupId]}">
                                                                        <span class="reeltype-chip">${member.reelType}</span>
                                                                    </c:forEach>
                                                                </c:when>
                                                                <c:otherwise>&mdash;</c:otherwise>
                                                            </c:choose>
                                                        </td>
                                                        <td>
                                                            <div class="target-list" data-group-id="${o.reelGroupId}">
                                                                <c:forEach var="grp" items="${outputTargetsByPlatform[o.reelGroupId]}">
                                                                    <div class="target-row">
                                                                        <span class="target-platform">${grp.key}</span>
                                                                        <span class="target-channels">
                                                                            <c:forEach var="m" items="${grp.value}">
                                                                                <c:set var="cdTargetPlatform" value="${m.publicationTarget.platform.platformName}"/>
                                                                                <%@ include file="fragments/scope-target-icon.jspf" %>
                                                                                <span class="channel-chip" data-target-id="${m.publicationTarget.id}">
                                                                                    <img class="scope-target-icon" src="${cdTargetIconSrc}" alt="" width="14" height="14"/>
                                                                                    <c:out value="${m.publicationTarget.channel.channelHandle}"/>
                                                                                    <c:choose>
                                                                                        <c:when test="${publishedMappingIds.contains(m.id)}">
                                                                                            <span class="scope-locked" title="Already published - cannot be removed">Published &#128274;</span>
                                                                                        </c:when>
                                                                                        <c:otherwise>
                                                                                            <form method="post" class="chip-remove-form"
                                                                                                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.reelGroupId}/targets/${m.publicationTarget.id}/remove">
                                                                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                                                                <button type="submit" class="chip-remove"
                                                                                                        title="Remove ${m.publicationTarget.channel.channelHandle}">&times;</button>
                                                                                            </form>
                                                                                        </c:otherwise>
                                                                                    </c:choose>
                                                                                </span>
                                                                            </c:forEach>
                                                                        </span>
                                                                    </div>
                                                                </c:forEach>
                                                                <c:if test="${empty outputTargetMappings[o.reelGroupId]}"><span class="muted-summary">(none yet)</span></c:if>
                                                            </div>
                                                        </td>
                                                        <td>
                                                            <details class="scope-add-target-details">
                                                                <summary>+ Add Platform / Channel</summary>
                                                                <form class="action-form add-target-form" method="post" data-group-id="${o.reelGroupId}"
                                                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.reelGroupId}/targets">
                                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                                    <label>Platform
                                                                        <select class="kcpc-platform-select">
                                                                            <option value="">Select Platform</option>
                                                                            <c:forEach var="platformName" items="${activePlatformNames}">
                                                                                <option value="${platformName}">${platformName}</option>
                                                                            </c:forEach>
                                                                        </select>
                                                                    </label>
                                                                    <div class="kcpc-channel-group hidden">
                                                                        <label>Channels</label>
                                                                        <div class="kcpc-channel-checklist">
                                                                            <c:forEach var="pt" items="${activePublicationTargets}">
                                                                                <label class="channel-check-item" data-platform="${pt.platform.platformName}">
                                                                                    <input type="checkbox" name="publicationTargetIds" value="${pt.id}"
                                                                                           data-platform="${pt.platform.platformName}"
                                                                                           data-channel="${pt.channel.channelHandle}"> ${pt.channel.channelHandle}
                                                                                </label>
                                                                            </c:forEach>
                                                                        </div>
                                                                    </div>
                                                                    <button type="submit">+ Add</button>
                                                                </form>
                                                            </details>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                                <c:if test="${empty outputGroupRepresentatives}">
                                                    <tr><td colspan="5" class="muted">No Planned Outputs yet - use "+ Add Output" above to add one.</td></tr>
                                                </c:if>
                                            </tbody>
                                        </table>
                                    </div>
                                    <div class="kcpc-modal-footer">
                                        <button type="button" class="secondary" id="publishingAssignmentModalCancel">Cancel</button>
                                        <button type="submit" form="publishingAssignmentAddForm" class="assignment-add-submit">Assign Publisher(s)</button>
                                    </div>
                                </div>
                            </div>
                        </c:if>

                        <c:if test="${canPublishingExecute and isPublishActiveAssignee}">
                            <h3>Your Assignment</h3>
                            <p class="muted">Status: Ready for Publishing</p>
                            <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/start">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <button type="submit"><c:choose><c:when test="${isRepostPublishingCycle}">Start Repost</c:when><c:otherwise>Start Publishing</c:otherwise></c:choose></button>
                            </form>
                        </c:if>
                    </c:if>
                    <c:if test="${(status == 'PUBG' or status == 'PP') and canPublishingExecute}">
                        <c:if test="${isPublishActiveAssignee}">
                            <h3>Record Actual Publication Event <c:if test="${isRepostPublishingCycle}"><span class="repost-cycle-badge">Repost Cycle</span></c:if></h3>
                            <form method="post" id="publishing-checklist-form"
                                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/events/bulk">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <table class="data-table" id="publishing-checklist-table">
                                    <thead>
                                    <tr>
                                        <th><input type="checkbox" id="publishing-checklist-select-all"
                                                   title="Select all pending tasks"></th>
                                        <th>Planned Output</th>
                                        <th>Type</th>
                                        <th>Platform</th>
                                        <th>Channel</th>
                                        <th>Evidence URL</th>
                                        <th>Status</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <c:forEach var="row" items="${publishingChecklist}">
                                        <tr class="publishing-checklist-row">
                                            <td>
                                                <c:choose>
                                                    <c:when test="${row.completed}">
                                                        <span class="muted" title="Completed">&#10003;</span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <input type="checkbox" class="publishing-checklist-select">
                                                        <input type="hidden" name="plannedOutputIds" value="${row.plannedOutput.id}"
                                                               class="publishing-checklist-hidden" disabled>
                                                        <input type="hidden" name="publicationTargetIds" value="${row.publicationTarget.id}"
                                                               class="publishing-checklist-hidden" disabled>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><c:out value="${empty row.plannedOutput.titleDescription ? '—' : row.plannedOutput.titleDescription}"/></td>
                                            <td>${row.plannedOutput.outputType}<c:if test="${row.plannedOutput.outputType == 'REEL'}"> &middot; ${row.plannedOutput.reelType}</c:if></td>
                                            <td><c:out value="${row.publicationTarget.platform.platformName}"/></td>
                                            <td><c:out value="${row.publicationTarget.channel.channelHandle}"/></td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${row.completed}">
                                                        <a href="${row.completedEvent.evidenceUrl}" target="_blank" rel="noopener noreferrer">
                                                            <c:out value="${row.completedEvent.evidenceUrl}"/></a>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <input type="text" name="evidenceUrls" class="publishing-checklist-evidence"
                                                               placeholder="Evidence URL" disabled>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${row.completed and isRepostPublishingCycle}"><span class="status-pill status-completed">Reposted</span></c:when>
                                                    <c:when test="${row.completed}"><span class="status-pill status-completed">Completed</span></c:when>
                                                    <c:when test="${isRepostPublishingCycle}"><span class="status-pill status-pending">Pending Repost</span></c:when>
                                                    <c:otherwise><span class="status-pill status-pending">Pending</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty publishingChecklist}">
                                        <tr><td colspan="7" class="muted">No Publication Targets to publish.</td></tr>
                                    </c:if>
                                    </tbody>
                                </table>
                                <div class="review-actions">
                                    <button type="submit" id="publishing-checklist-submit" disabled>
                                        <c:choose><c:when test="${isRepostPublishingCycle}">Submit Repost</c:when><c:otherwise>Submit Published Tasks</c:otherwise></c:choose>
                                    </button>
                                </div>
                            </form>

                            <details>
                                <summary>Record a Repost / Manual Entry</summary>
                                <%-- Event Type is never a user choice - PublishingService derives ORIGINAL vs
                                     REPOST itself from whether a live post already exists for this exact
                                     (Planned Output, Publication Target) pair, so a Publisher can never
                                     mis-record a repost as another ORIGINAL (or vice versa) by picking wrong. --%>
                                <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/events">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <label>Planned Output
                                        <select name="plannedOutputId">
                                            <c:forEach var="o" items="${outputs}"><option value="${o.id}">${o.outputType} ${o.titleDescription}</option></c:forEach>
                                        </select>
                                    </label>
                                    <label>Publication Target
                                        <select name="publicationTargetId">
                                            <c:forEach var="pt" items="${activePublicationTargets}">
                                                <option value="${pt.id}">${pt.platform.platformName} / ${pt.channel.channelHandle}</option>
                                            </c:forEach>
                                        </select>
                                    </label>
                                    <label>Actual Publication Date * <input type="date" name="actualPublicationTimestamp" required></label>
                                    <label>Evidence URL * <input type="url" name="evidenceUrl" required></label>
                                    <button type="submit">Save Event</button>
                                </form>
                            </details>
                        </c:if>
                    </c:if>

                    <h3>Actual Publication Events</h3>
                    <table class="data-table">
                        <thead><tr><th>Event Type</th><th>Content Type</th><th>Target</th><th>Timestamp (IST)</th><th>Evidence</th><th></th></tr></thead>
                        <tbody>
                        <%-- Each event carries its OWN plannedOutput (EAGER-fetched, ERD-TBL-021) - Content
                             Type is read directly from that event's actual output/reel-type relationship,
                             never inferred from platform/channel/order/timestamp, so multiple events for the
                             same Platform x Channel (e.g. REEL VERY_SHORT/SHORT/LONG all posted to the same
                             Instagram handle) each show their own correct, distinct variation. --%>
                        <c:forEach var="e" items="${events}">
                            <c:set var="cdEvtOutputType" value="${e.plannedOutput.outputType}"/>
                            <c:set var="cdEvtReelType" value="${e.plannedOutput.reelType}"/>
                            <tr>
                                <td>${e.eventType}</td>
                                <td>
                                    <c:choose>
                                        <c:when test="${empty cdEvtOutputType}">-</c:when>
                                        <c:when test="${cdEvtOutputType == 'REEL' and not empty cdEvtReelType}">REEL &middot; ${cdEvtReelType}</c:when>
                                        <c:otherwise>${cdEvtOutputType}</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>${e.publicationTarget.platform.platformName} / ${e.publicationTarget.channel.channelHandle}</td>
                                <td>${kcpc:ist(e.actualPublicationTimestamp)}</td>
                                <td>
                                    <%-- Current EFFECTIVE URL (latest correction if one exists, else the
                                         original event.evidenceUrl) - never the raw immutable original field
                                         directly, so a saved correction is actually reflected here. --%>
                                    <c:set var="cdEvtEffectiveUrl" value="${effectiveEvidenceUrlByEventId[e.id]}"/>
                                    <c:choose>
                                        <c:when test="${not empty cdEvtEffectiveUrl}">
                                            <a class="drive-link" href="${fn:escapeXml(cdEvtEffectiveUrl)}" target="_blank" rel="noopener noreferrer">Open &#8599;</a>
                                        </c:when>
                                        <c:otherwise>-</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:if test="${canPublishingExecute}">
                                        <details>
                                            <summary>Correct evidence</summary>
                                            <form class="action-form" method="post"
                                                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/events/${e.id}/evidence-corrections">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                <label>Corrected URL * <input type="url" name="correctedEvidenceUrl" required></label>
                                                <label>Reason * <input type="text" name="correctionReason" required></label>
                                                <button type="submit">Save Correction</button>
                                            </form>
                                        </details>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty events}"><tr><td colspan="6" class="muted">No publication events yet.</td></tr></c:if>
                        </tbody>
                    </table>

                    <div class="stage-description" data-empty-text="No description yet.">
                        <c:choose>
                            <c:when test="${canEditPublishingDescription}">
                                <div class="stage-description-header">
                                    <h3 class="stage-block-heading">Publishing Description</h3>
                                    <button type="button" class="stage-description-edit-btn">&#9998; Edit</button>
                                </div>
                                <p class="stage-description-text stage-description-view"><c:out value="${empty plan.publishingDescription ? 'No description yet.' : plan.publishingDescription}"/></p>
                                <form class="action-form stage-description-form hidden" method="post"
                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/description">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <textarea name="description" rows="3"
                                              placeholder="Instructions for the Publisher team..."><c:out value="${plan.publishingDescription}"/></textarea>
                                    <div class="review-actions">
                                        <button type="button" class="stage-description-cancel-btn">Cancel</button>
                                        <button type="submit">Save</button>
                                    </div>
                                </form>
                            </c:when>
                            <c:otherwise>
                                <h3 class="stage-block-heading">Publishing Description</h3>
                                <p class="stage-description-text ${empty plan.publishingDescription ? 'muted' : ''}"><c:out value="${empty plan.publishingDescription ? 'No description yet.' : plan.publishingDescription}"/></p>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- Publishing Comments only becomes visible once Publishing has actually been reached
             (cdStageIndex >= 3) - Shoot/Edit Comments above remain visible as reached-stage history. --%>
        <c:if test="${cdStageIndex >= 3}">
        <div class="panel content-detail-stage-comments-block">
            <h3 class="content-detail-card-title">Publishing Comments</h3>
            <div class="stage-comments" data-comments-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/comments">
                <div class="stage-comments-list">
                    <c:forEach var="cm" items="${publishingComments}">
                        <div class="stage-comment" data-comment-id="${cm.id}" data-commenter-name="${cm.commenter.fullName}" data-created-at="${kcpc:ist(cm.createdAt)}">
                            <c:choose>
                                <c:when test="${cm.deleted}">
                                    <div class="stage-comment-meta"><strong><c:out value="${cm.commenter.fullName}"/></strong> &middot; ${kcpc:ist(cm.createdAt)}</div>
                                    <div class="stage-comment-text muted">This comment was deleted.</div>
                                </c:when>
                                <c:otherwise>
                                    <div class="stage-comment-meta">
                                        <span class="stage-comment-meta-text"><strong><c:out value="${cm.commenter.fullName}"/></strong> &middot; ${kcpc:ist(cm.createdAt)}<c:if test="${not empty cm.editedAt}"> &middot; <span class="stage-comment-edited">edited</span></c:if></span>
                                        <c:if test="${cm.commenter.id == user.id}">
                                            <div class="stage-comment-menu">
                                                <button type="button" class="stage-comment-menu-btn" aria-label="Comment actions">&hellip;</button>
                                                <div class="stage-comment-menu-dropdown hidden">
                                                    <button type="button" class="stage-comment-edit-trigger">Edit</button>
                                                    <button type="button" class="stage-comment-delete-trigger">Delete</button>
                                                </div>
                                            </div>
                                        </c:if>
                                    </div>
                                    <div class="stage-comment-text"><c:out value="${cm.commentText}"/></div>
                                    <c:if test="${cm.commenter.id == user.id}">
                                        <form class="action-form stage-comment-edit-form hidden" method="post"
                                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/comments/${cm.id}/edit">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <textarea name="commentText" rows="2" required><c:out value="${cm.commentText}"/></textarea>
                                            <div class="review-actions">
                                                <button type="button" class="stage-comment-edit-cancel-btn">Cancel</button>
                                                <button type="submit">Save</button>
                                            </div>
                                        </form>
                                        <form class="action-form stage-comment-delete-form hidden" method="post"
                                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/comments/${cm.id}/delete">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <span class="stage-comment-delete-confirm-text">Delete this comment? This cannot be undone.</span>
                                            <div class="review-actions">
                                                <button type="button" class="stage-comment-delete-cancel-btn">Cancel</button>
                                                <button type="submit" class="stage-comment-delete-confirm-btn">Yes, delete</button>
                                            </div>
                                        </form>
                                    </c:if>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </c:forEach>
                </div>
                <c:if test="${canCommentOnPublishing}">
                    <form class="action-form stage-comment-form" method="post"
                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/comments">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <textarea name="commentText" rows="2" placeholder="Write a comment..." required></textarea>
                        <button type="submit">Comment</button>
                    </form>
                </c:if>
            </div>
        </div>
        </c:if>
    </div>

    </c:if>

    <%-- ============================ PERFORMANCE ============================ --%>
    <c:if test="${canSeePerformanceTab}">
    <div class="my-work-tab-panel ${activeTab == 'performance' ? '' : 'hidden'}" data-tab-panel="performance">
        <h2 id="performance">Performance</h2>
        <c:choose>
            <c:when test="${cdStageIndex < 4}">
                <div class="panel"><p class="muted">Performance Pending &mdash; not reached yet.</p></div>
            </c:when>
            <c:otherwise>
                <%-- Each PerformanceObligation is 1:1 with exactly one ActualPublicationEvent
                     (ERD-TBL-023) - which is itself already linked to a real PlannedOutput
                     (Output Type/Reel Type) and PublicationTarget (Platform/Channel), both
                     EAGER-fetched. Nothing new to persist: this identity block is read straight
                     from those existing relationships, so a Reel with 3 variations published to
                     multiple channels always renders as separate, individually-identified cards -
                     never a generic "Obligation - Due X" heading a manager could mismatch. Every
                     ActualPublicationEvent (ORIGINAL or REPOST alike) gets its own obligation, so
                     the Event Type badge is shown too - two obligations can otherwise share the
                     exact same Reel Type/Platform/Channel after a Repost. --%>
                <c:forEach var="ob" items="${obligations}">
                    <c:set var="obOutputType" value="${ob.event.plannedOutput.outputType}"/>
                    <c:set var="obReelType" value="${ob.event.plannedOutput.reelType}"/>
                    <c:set var="obEffectiveUrl" value="${effectiveEvidenceUrlByEventId[ob.event.id]}"/>
                    <div class="panel performance-obligation-card" data-obligation-id="${ob.id}">
                        <div class="performance-obligation-identity">
                            <div class="performance-obligation-identity-row">
                                <span class="performance-obligation-content-type">
                                    <c:choose>
                                        <c:when test="${empty obOutputType}">-</c:when>
                                        <c:when test="${obOutputType == 'REEL' and not empty obReelType}">REEL &middot; ${obReelType}</c:when>
                                        <c:otherwise>${obOutputType}</c:otherwise>
                                    </c:choose>
                                </span>
                                <span class="performance-obligation-event-type-badge"><c:out value="${ob.event.eventType}"/></span>
                            </div>
                            <div class="performance-obligation-target">
                                <c:choose>
                                    <c:when test="${empty ob.event.publicationTarget}">-</c:when>
                                    <c:otherwise>${ob.event.publicationTarget.platform.platformName} &middot; <c:out value="${ob.event.publicationTarget.channel.channelHandle}"/></c:otherwise>
                                </c:choose>
                            </div>
                            <div class="performance-obligation-meta">
                                <span>Published ${kcpc:ist(ob.event.actualPublicationTimestamp)}</span>
                                <c:choose>
                                    <c:when test="${not empty obEffectiveUrl}">
                                        <a class="drive-link" href="${fn:escapeXml(obEffectiveUrl)}" target="_blank" rel="noopener noreferrer">Open Published Content &#8599;</a>
                                    </c:when>
                                    <c:otherwise><span class="muted">Publication context unavailable</span></c:otherwise>
                                </c:choose>
                            </div>
                            <div class="performance-obligation-due">Performance Due ${ob.performanceDueDate} (non-reschedulable)${ob.completed ? ' — COMPLETED' : ''}</div>
                        </div>
                        <c:set var="sc" value="${scorecardsByObligation[ob.id]}"/>
                        <%-- V26: Performance tracking is Meta-only (Instagram/Facebook - both platforms
                             already guaranteed by the controller's eligibility filter, so every ${ob} here
                             is eligible by construction) with 4 direct-entry metrics from Meta Ads Manager,
                             replacing the old 6-field/derived-Hook-Hold-CTR model. A scorecard created
                             before this change (sc.usesMetaMetricModel == false) keeps rendering through
                             its ORIGINAL form/summary/correction blocks below (unchanged) so historical data
                             stays fully readable and correctable - every scorecard created from now on is
                             the new model by construction (PerformanceService#saveDraft only ever calls
                             CreativePerformanceScorecard#updateMetaDraft), so "no scorecard yet" always
                             means the new draft form. --%>
                        <c:choose>
                            <c:when test="${not empty sc and sc.submitted}">
                                <c:choose>
                                    <c:when test="${sc.usesMetaMetricModel}">
                                        <%-- Effective (correction-resolved) values, never the raw scorecard
                                             frozen at submission - otherwise a correction would never
                                             visibly change the summary. --%>
                                        <c:set var="effMetrics" value="${effectiveMetricsByObligation[ob.id]}"/>
                                        <p>Hook Rate: <c:choose><c:when test="${effMetrics.hookRateIsNa}">N/A</c:when><c:otherwise><c:out value="${effMetrics.hookRatePercent}" default="N/A"/>%</c:otherwise></c:choose> &middot;
                                           Hold Rate: <c:choose><c:when test="${effMetrics.holdRateIsNa}">N/A</c:when><c:otherwise><c:out value="${effMetrics.holdRatePercent}" default="N/A"/>%</c:otherwise></c:choose> &middot;
                                           Views: ${kcpc:count(effMetrics.views)} &middot;
                                           Average View Duration: <c:choose><c:when test="${effMetrics.avgViewDurationIsNa}">N/A</c:when><c:otherwise><c:out value="${effMetrics.averageViewDurationSeconds}" default="N/A"/>s</c:otherwise></c:choose></p>
                                        <c:if test="${canPerformanceUpdate}">
                                            <details>
                                                <summary>Correct a metric</summary>
                                                <%-- Only metrics not marked N/A on THIS scorecard are offered - Views
                                                     has no N/A concept (always required for an eligible Meta record). --%>
                                                <form class="action-form performance-correction-form" method="post"
                                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/scorecards/${sc.id}/corrections">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                    <label>Metric to correct
                                                        <select class="metric-correction-select" required>
                                                            <option value="" disabled selected>Select metric&hellip;</option>
                                                            <c:if test="${not effMetrics.hookRateIsNa}">
                                                                <option value="hookRate">Hook Rate</option>
                                                            </c:if>
                                                            <c:if test="${not effMetrics.holdRateIsNa}">
                                                                <option value="holdRate">Hold Rate</option>
                                                            </c:if>
                                                            <option value="views">Views</option>
                                                            <c:if test="${not effMetrics.avgViewDurationIsNa}">
                                                                <option value="avgViewDuration">Average View Duration</option>
                                                            </c:if>
                                                        </select>
                                                    </label>
                                                    <div class="performance-metric-correction-field hidden" data-metric="hookRate">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${effMetrics.hookRatePercent}" default="N/A"/>%</p>
                                                        <label>Corrected Value (%) <input type="number" step="0.01" min="0" max="100" name="correctedHookRatePercent"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="holdRate">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${effMetrics.holdRatePercent}" default="N/A"/>%</p>
                                                        <label>Corrected Value (%) <input type="number" step="0.01" min="0" max="100" name="correctedHoldRatePercent"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="views">
                                                        <p class="performance-metric-correction-current">Current Value: ${kcpc:count(effMetrics.views)}</p>
                                                        <label>Corrected Value <input type="number" min="0" step="1" name="correctedViews"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="avgViewDuration">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${effMetrics.averageViewDurationSeconds}" default="N/A"/>s</p>
                                                        <label>Corrected Value (s) <input type="number" step="0.1" min="0" name="correctedAverageViewDurationSeconds"></label>
                                                    </div>
                                                    <label>Reason * <input type="text" name="correctionReason" required></label>
                                                    <button type="submit">Save Correction</button>
                                                </form>
                                            </details>
                                            <details>
                                                <summary>Correction History</summary>
                                                <%-- Per-scorecard history, distinct from the company-wide Reports -> Logs
                                                     audit trail (that stays untouched - see PERFORMANCE_METRIC_CORRECTED
                                                     audit record still written by PerformanceService#correctMetrics). --%>
                                                <c:forEach var="corr" items="${correctionsByObligation[ob.id]}">
                                                    <div class="correction-history-entry">
                                                        <c:if test="${not empty corr.newMetaHookRate}">
                                                            <p>Hook Rate: <c:out value="${corr.priorMetaHookRate}" default="N/A"/>% &rarr; ${corr.newMetaHookRate}%</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newMetaHoldRate}">
                                                            <p>Hold Rate: <c:out value="${corr.priorMetaHoldRate}" default="N/A"/>% &rarr; ${corr.newMetaHoldRate}%</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newMetaViews}">
                                                            <p>Views: ${kcpc:count(corr.priorMetaViews)} &rarr; ${kcpc:count(corr.newMetaViews)}</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newMetaAvgViewDuration}">
                                                            <p>Average View Duration: <c:out value="${corr.priorMetaAvgViewDuration}" default="N/A"/>s &rarr; ${corr.newMetaAvgViewDuration}s</p>
                                                        </c:if>
                                                        <p class="muted">Reason: <c:out value="${corr.mandatoryReason}"/> &middot;
                                                           By <c:out value="${corr.correctedBy.fullName}"/> &middot; ${kcpc:ist(corr.correctedAt)}</p>
                                                    </div>
                                                </c:forEach>
                                                <c:if test="${empty correctionsByObligation[ob.id]}"><p class="muted">No corrections yet.</p></c:if>
                                            </details>
                                        </c:if>
                                    </c:when>
                                    <c:otherwise>
                                        <%-- Pre-V26 scorecard - original 6-field model, unchanged, so historical
                                             data stays fully readable and correctable. --%>
                                        <c:set var="legacyMetrics" value="${legacyEffectiveMetricsByObligation[ob.id]}"/>
                                        <p class="muted note-box">This scorecard was recorded before Performance
                                            tracking moved to the Meta-only model - shown in its original format.</p>
                                        <p>Hook Rate: <c:out value="${legacyMetrics.hookRatePercent}" default="N/A"/>% &middot;
                                           Hold Rate: <c:out value="${legacyMetrics.holdRatePercent}" default="N/A"/>% &middot;
                                           CTR: <c:out value="${legacyMetrics.ctrPercent}" default="N/A"/>%</p>
                                        <c:if test="${canPerformanceUpdate}">
                                            <details>
                                                <summary>Correct a metric</summary>
                                                <form class="action-form performance-correction-form" method="post"
                                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/scorecards/${sc.id}/corrections">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                    <label>Metric to correct
                                                        <select class="metric-correction-select" required>
                                                            <option value="" disabled selected>Select metric&hellip;</option>
                                                            <c:if test="${not legacyMetrics.views3secIsNa}">
                                                                <option value="views3sec">3-sec Views</option>
                                                            </c:if>
                                                            <option value="plays">Plays</option>
                                                            <c:if test="${not legacyMetrics.watchTimeIsNa}">
                                                                <option value="watchTime">Avg Watch</option>
                                                            </c:if>
                                                            <c:if test="${not legacyMetrics.videoLengthIsNa}">
                                                                <option value="videoLength">Video Length</option>
                                                            </c:if>
                                                            <c:if test="${not legacyMetrics.clicksIsNa}">
                                                                <option value="linkClicks">Link Clicks</option>
                                                            </c:if>
                                                            <option value="impressions">Impressions</option>
                                                        </select>
                                                    </label>
                                                    <div class="performance-metric-correction-field hidden" data-metric="views3sec">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${legacyMetrics.views3sec}" default="N/A"/></p>
                                                        <label>Corrected Value <input type="number" min="0" name="correctedViews3sec"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="plays">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${legacyMetrics.plays}" default="N/A"/></p>
                                                        <label>Corrected Value <input type="number" min="0" name="correctedPlays"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="watchTime">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${legacyMetrics.averageWatchTimeSeconds}" default="N/A"/></p>
                                                        <label>Corrected Value <input type="number" step="0.01" min="0" name="correctedWatchTimeSeconds"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="videoLength">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${legacyMetrics.videoLengthSeconds}" default="N/A"/></p>
                                                        <label>Corrected Value <input type="number" step="0.01" min="0" name="correctedVideoLengthSeconds"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="linkClicks">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${legacyMetrics.linkClicks}" default="N/A"/></p>
                                                        <label>Corrected Value <input type="number" min="0" name="correctedLinkClicks"></label>
                                                    </div>
                                                    <div class="performance-metric-correction-field hidden" data-metric="impressions">
                                                        <p class="performance-metric-correction-current">Current Value: <c:out value="${legacyMetrics.impressions}" default="N/A"/></p>
                                                        <label>Corrected Value <input type="number" min="0" name="correctedImpressions"></label>
                                                    </div>
                                                    <label>Reason * <input type="text" name="correctionReason" required></label>
                                                    <button type="submit">Save Correction</button>
                                                </form>
                                            </details>
                                            <details>
                                                <summary>Correction History</summary>
                                                <c:forEach var="corr" items="${correctionsByObligation[ob.id]}">
                                                    <div class="correction-history-entry">
                                                        <c:if test="${not empty corr.newViews3sec}">
                                                            <p>3-sec Views: <c:out value="${corr.priorViews3sec}" default="N/A"/> &rarr; ${corr.newViews3sec}</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newPlays}">
                                                            <p>Plays: <c:out value="${corr.priorPlays}" default="N/A"/> &rarr; ${corr.newPlays}</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newWatchTime}">
                                                            <p>Avg Watch: <c:out value="${corr.priorWatchTime}" default="N/A"/> &rarr; ${corr.newWatchTime}</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newVideoLength}">
                                                            <p>Video Length: <c:out value="${corr.priorVideoLength}" default="N/A"/> &rarr; ${corr.newVideoLength}</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newClicks}">
                                                            <p>Link Clicks: <c:out value="${corr.priorClicks}" default="N/A"/> &rarr; ${corr.newClicks}</p>
                                                        </c:if>
                                                        <c:if test="${not empty corr.newImpressions}">
                                                            <p>Impressions: <c:out value="${corr.priorImpressions}" default="N/A"/> &rarr; ${corr.newImpressions}</p>
                                                        </c:if>
                                                        <p class="muted">Reason: <c:out value="${corr.mandatoryReason}"/> &middot;
                                                           By <c:out value="${corr.correctedBy.fullName}"/> &middot; ${kcpc:ist(corr.correctedAt)}</p>
                                                    </div>
                                                </c:forEach>
                                                <c:if test="${empty correctionsByObligation[ob.id]}"><p class="muted">No corrections yet.</p></c:if>
                                            </details>
                                        </c:if>
                                    </c:otherwise>
                                </c:choose>
                            </c:when>
                            <c:when test="${canPerformanceUpdate}">
                                <c:choose>
                                    <c:when test="${empty sc or sc.usesMetaMetricModel}">
                                        <c:if test="${not empty sc}"><p class="performance-draft-status">Draft saved</p></c:if>
                                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/${ob.id}/draft">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <div class="field-row">
                                                <div><label>Hook Rate (%) <input type="number" step="0.01" min="0" max="100" name="hookRatePercent" value="${sc.metaHookRatePercent}"></label></div>
                                                <div><label>Hold Rate (%) <input type="number" step="0.01" min="0" max="100" name="holdRatePercent" value="${sc.metaHoldRatePercent}"></label></div>
                                            </div>
                                            <div class="field-row">
                                                <div><label>Views <input type="number" min="0" step="1" name="views" value="${sc.metaViews}"></label></div>
                                                <div><label>Average View Duration (s) <input type="number" step="0.1" min="0" name="averageViewDurationSeconds" value="${sc.metaAverageViewDurationSeconds}"></label></div>
                                            </div>
                                            <p class="note-box">Enter values exactly as reported by Meta Ads Manager. Any field may be
                                                left blank while a draft. Hook Rate / Hold Rate / Average View Duration are video-specific -
                                                use the N/A option for a static photo output (not shown on this quick form; use the
                                                correction path after submission, or leave blank and mark N/A there). Views is always
                                                required for an eligible Instagram/Facebook record.</p>
                                            <button type="submit">Save Draft</button>
                                        </form>
                                    </c:when>
                                    <c:otherwise>
                                        <%-- Pre-V26 draft still in progress - original 6-field draft form, unchanged. --%>
                                        <c:if test="${not empty sc}"><p class="performance-draft-status">Draft saved</p></c:if>
                                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/${ob.id}/draft">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <div class="field-row">
                                                <div><label>3-sec Views <input type="number" min="0" name="views3sec" value="${sc.views3sec}"></label></div>
                                                <div><label>Plays <input type="number" min="0" name="plays" value="${sc.plays}"></label></div>
                                            </div>
                                            <div class="field-row">
                                                <div><label>Avg Watch (s) <input type="number" step="0.01" min="0" name="averageWatchTimeSeconds" value="${sc.averageWatchTimeSeconds}"></label></div>
                                                <div><label>Video Length (s) <input type="number" step="0.01" min="0" name="videoLengthSeconds" value="${sc.videoLengthSeconds}"></label></div>
                                            </div>
                                            <div class="field-row">
                                                <div><label>Link Clicks <input type="number" min="0" name="linkClicks" value="${sc.linkClicks}"></label></div>
                                                <div><label>Impressions <input type="number" min="0" name="impressions" value="${sc.impressions}"></label></div>
                                            </div>
                                            <p class="note-box">Any field may be left blank while a draft. Metrics not applicable to this platform/output should be handled via N/A (server-side default false here; use the correction path once submitted).</p>
                                            <button type="submit">Save Draft</button>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                                <c:choose>
                                    <c:when test="${not empty sc}">
                                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/${ob.id}/submit">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <button type="submit">Submit Scorecard (final)</button>
                                        </form>
                                    </c:when>
                                    <c:otherwise><p class="muted">Save a draft first to enable final submission.</p></c:otherwise>
                                </c:choose>
                            </c:when>
                            <c:otherwise><p class="muted">No draft yet.</p></c:otherwise>
                        </c:choose>
                    </div>
                </c:forEach>
                <c:if test="${empty obligations}"><div class="panel"><p class="muted">No performance obligations yet.</p></div></c:if>
            </c:otherwise>
        </c:choose>
    </div>

    </c:if>

    <%-- ============================ TIMELINE ============================ --%>
    <c:if test="${canSeeTimeline}">
    <div class="my-work-tab-panel ${activeTab == 'timeline' ? '' : 'hidden'}" data-tab-panel="timeline">
        <div class="panel">
            <h2>Timeline</h2>
            <ul class="timeline">
                <%-- ENG-091: a stage excluded by the Idea Review "Stages" selection (Direct Edit /
                     Direct Publishing) never has a real WorkflowTransitionHistory row - its status
                     was never entered at all - so these are plain notes, not real transitions,
                     shown first since the exclusion was decided at planning time. --%>
                <c:if test="${not empty plan.shootStageSkipReason}">
                    <li class="timeline-skipped">Shoot — Skipped — ${plan.shootStageSkipReason}</li>
                </c:if>
                <c:if test="${not empty plan.editStageSkipReason}">
                    <li class="timeline-skipped">Edit — Skipped — ${plan.editStageSkipReason}</li>
                </c:if>
                <c:forEach var="t" items="${timeline}">
                    <li><span class="ts">${kcpc:ist(t.transitionTimestamp)}</span>
                        ${t.fromStatusCode.statusName} &rarr; ${t.toStatusCode.statusName} (${t.triggerCommand}) by ${t.triggeredBy.fullName}
                        <c:if test="${not empty t.transitionReason}"> — ${t.transitionReason}</c:if>
                    </li>
                </c:forEach>
                <c:if test="${empty timeline and empty plan.shootStageSkipReason and empty plan.editStageSkipReason}">
                    <li class="muted">No transitions yet.</li>
                </c:if>
            </ul>
        </div>
    </div>
    </c:if>

    </div>

    <%-- ============================ RIGHT SIDEBAR ============================ --%>
    <aside class="content-detail-sidebar">
        <div class="panel content-detail-action-center">
            <h3 class="content-detail-card-title">Action Center</h3>
            <p class="content-detail-current-stage">Current Stage: <span class="status-badge"><c:out value="${currentStage.label}"/></span></p>
            <c:set var="cdHasPrimary" value="false"/>
            <c:forEach var="a" items="${availableActions}"><c:if test="${a.group == 'primary'}"><c:set var="cdHasPrimary" value="true"/></c:if></c:forEach>
            <c:if test="${cdHasPrimary}">
                <h4>Primary Actions</h4>
                <div class="content-detail-action-row content-detail-action-row-grid">
                    <c:forEach var="a" items="${availableActions}">
                        <c:if test="${a.group == 'primary'}">
                            <button type="button" class="content-detail-action-btn content-detail-action-${a.style}"
                                    data-action-key="${a.actionKey}" data-requires-reason="${a.requiresReason}">${a.label}</button>
                        </c:if>
                    </c:forEach>
                </div>
            </c:if>
            <c:set var="cdHasOther" value="false"/>
            <c:forEach var="a" items="${availableActions}"><c:if test="${a.group == 'other'}"><c:set var="cdHasOther" value="true"/></c:if></c:forEach>
            <%-- Skip Stage (ENG-090) buttons render as additional cells of this SAME grid (not the
                 availableActions/content-detail-action-form loop - that mechanism reveals a plain
                 inline form with no confirm step, and picker-heavy, review-like actions are already
                 kept out of it; Skip needs a genuine confirmation modal, reusing the same
                 .kcpc-modal-overlay shell as the Publisher Assignment modal above). Layout fix:
                 .content-detail-action-skip-row-start forces grid-column:1, so Skip Stage always
                 starts its own new row at the same left edge as whichever "other" action lands in
                 column 1 (Cancel, most commonly) - reusing the grid's own row-gap for consistent
                 vertical spacing rather than a second, separately-spaced container. Visible
                 independently of canSkipShoot/canSkipEdit's underlying eligibility - both flags
                 already combine PERM_20_SKIP_STAGE with the correct workflow-status window
                 (SA/SIP/SRV for Shoot, EA/ED/ERV for Edit) server-side, so a visible button here
                 always means the POST below is expected to succeed. --%>
            <c:if test="${cdHasOther || canSkipShoot || canSkipEdit}">
                <h4>Available Actions</h4>
                <p class="content-detail-action-helper">Actions available for the current workflow stage and your permissions.</p>
                <div class="content-detail-action-row content-detail-action-row-grid">
                    <c:forEach var="a" items="${availableActions}">
                        <c:if test="${a.group == 'other'}">
                            <button type="button" class="content-detail-action-btn content-detail-action-${a.style}"
                                    data-action-key="${a.actionKey}" data-requires-reason="${a.requiresReason}">${a.label}</button>
                        </c:if>
                    </c:forEach>
                    <c:if test="${canSkipShoot}">
                        <button type="button" class="content-detail-action-btn content-detail-action-secondary content-detail-action-skip-row-start"
                                id="skipShootBtn">Skip Stage</button>
                    </c:if>
                    <c:if test="${canSkipEdit}">
                        <button type="button" class="content-detail-action-btn content-detail-action-secondary content-detail-action-skip-row-start"
                                id="skipEditBtn">Skip Stage</button>
                    </c:if>
                </div>
            </c:if>
            <c:if test="${!cdHasOther && !canSkipShoot && !canSkipEdit}"><p class="muted">No administrative actions available at this stage.</p></c:if>

            <c:if test="${canSkipShoot}">
                <div class="kcpc-modal-overlay hidden" id="skipShootModalOverlay">
                    <div class="kcpc-modal" role="dialog" aria-modal="true" aria-labelledby="skipShootModalTitle">
                        <div class="kcpc-modal-header">
                            <h3 id="skipShootModalTitle">Skip Shoot</h3>
                            <button type="button" class="kcpc-modal-close" id="skipShootModalClose" aria-label="Close">&times;</button>
                        </div>
                        <div class="kcpc-modal-body">
                            <p>Are you sure you want to skip this stage?</p>
                            <div class="reviews-field-row"><span class="reviews-field-label">Current Stage</span><span class="reviews-field-value">Shoot</span></div>
                            <div class="reviews-field-row"><span class="reviews-field-label">Next Stage</span><span class="reviews-field-value">Edit Assigned</span></div>
                            <form method="post" id="skipShootForm" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/skip">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <div class="kcpc-model-picker">
                                    <label>Editor(s) *</label>
                                    <div class="kcpc-model-input">
                                        <div class="kcpc-model-chips"></div>
                                        <input type="text" class="kcpc-model-search" placeholder="Search editor...">
                                    </div>
                                    <div class="kcpc-model-checklist">
                                        <c:forEach var="u" items="${videoEditorUsers}">
                                            <label class="model-check-item">
                                                <input type="checkbox" name="editorUserIds" value="${u.id}" data-name="${u.fullName}"> ${u.fullName}
                                                <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                                            </label>
                                        </c:forEach>
                                    </div>
                                    <label for="skipShootLead">Editor Lead *</label>
                                    <select name="leadEditorUserId" id="skipShootLead" class="kcpc-lead-select" disabled>
                                        <option value="">N/A</option>
                                    </select>
                                </div>
                                <label>Reason *
                                    <textarea name="reason" rows="3" required placeholder="Enter reason for skipping this stage..."></textarea>
                                </label>
                            </form>
                        </div>
                        <div class="kcpc-modal-footer">
                            <button type="button" class="btn-outline" id="skipShootCancelBtn">Cancel</button>
                            <button type="submit" form="skipShootForm" id="skipShootConfirmBtn">Confirm Skip</button>
                        </div>
                    </div>
                </div>
            </c:if>
            <c:if test="${canSkipEdit}">
                <div class="kcpc-modal-overlay hidden" id="skipEditModalOverlay">
                    <div class="kcpc-modal" role="dialog" aria-modal="true" aria-labelledby="skipEditModalTitle">
                        <div class="kcpc-modal-header">
                            <h3 id="skipEditModalTitle">Skip Edit</h3>
                            <button type="button" class="kcpc-modal-close" id="skipEditModalClose" aria-label="Close">&times;</button>
                        </div>
                        <div class="kcpc-modal-body">
                            <p>Are you sure you want to skip this stage?</p>
                            <div class="reviews-field-row"><span class="reviews-field-label">Current Stage</span><span class="reviews-field-value">Edit</span></div>
                            <div class="reviews-field-row"><span class="reviews-field-label">Next Stage</span><span class="reviews-field-value">Ready for Publishing</span></div>
                            <form method="post" id="skipEditForm" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/skip">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <div class="kcpc-model-picker">
                                    <label>Publisher(s) *</label>
                                    <div class="kcpc-model-input">
                                        <div class="kcpc-model-chips"></div>
                                        <input type="text" class="kcpc-model-search" placeholder="Search publisher...">
                                    </div>
                                    <div class="kcpc-model-checklist">
                                        <%-- ENG-097: pre-check a Publisher already assigned from Planning time. --%>
                                        <c:forEach var="u" items="${publisherUsers}">
                                            <label class="model-check-item">
                                                <input type="checkbox" name="publisherUserIds" value="${u.id}" data-name="${u.fullName}"
                                                       <c:if test="${alreadyAssignedPublisherUserIds.contains(u.id)}">checked</c:if>> ${u.fullName}
                                                <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                                                <c:if test="${alreadyAssignedPublisherUserIds.contains(u.id)}"><span class="muted"> (already assigned)</span></c:if>
                                            </label>
                                        </c:forEach>
                                    </div>
                                </div>
                                <label>Reason *
                                    <textarea name="reason" rows="3" required placeholder="Enter reason for skipping this stage..."></textarea>
                                </label>
                            </form>
                        </div>
                        <div class="kcpc-modal-footer">
                            <button type="button" class="btn-outline" id="skipEditCancelBtn">Cancel</button>
                            <button type="submit" form="skipEditForm" id="skipEditConfirmBtn">Confirm Skip</button>
                        </div>
                    </div>
                </div>
            </c:if>

            <%-- Hidden action forms - one per possible actionKey, revealed by content-detail.js when
                 its matching button is clicked. Same endpoints/fields as before, just relocated here. --%>

            <%-- Shoot Review / Edit Review decisions are deliberately NOT here (UI consistency fix):
                 approval requires selecting at least one qualifying final Cameraperson/Editor, a
                 control that only exists in the Shoot/Edit tabs' own canonical review UI below -
                 duplicating a reduced Approve-only form here would always fail server-side
                 (ShootingService/EditingService reject Approve without that selection). Make the
                 decision from the Shoot/Edit tab (or Reviews -> Shoot/Edit), which share the exact
                 same backend endpoint this form used to post to. --%>

            <form class="content-detail-action-form hidden" data-action-key="RESCHEDULE" method="post"
                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reschedule">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Stage Context
                    <select name="stageContext">
                        <c:forEach var="sc" items="${stageContexts}"><option value="${sc}">${sc}</option></c:forEach>
                    </select>
                </label>
                <label>New Shoot Date <input type="date" name="newShootDate" min="${today}"></label>
                <label>New Edit Date <input type="date" name="newEditDate" min="${today}"></label>
                <label>New Live Date <input type="date" name="newLiveDate" min="${today}"></label>
                <label>Reason * <input type="text" name="reason" required></label>
                <button type="submit">Confirm Reschedule</button>
            </form>

            <form class="content-detail-action-form hidden" data-action-key="REASSIGN" method="post" id="reassign-form"
                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reassign">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Task Stage
                    <select name="taskStage" id="reassign-task-stage">
                        <c:forEach var="ts" items="${taskStages}"><option value="${ts}">${ts}</option></c:forEach>
                    </select>
                </label>
                <div class="kcpc-model-picker reassign-assignee-picker" data-stage="SHOOTING">
                    <label>New Cameraperson(s)</label>
                    <div class="kcpc-model-input">
                        <div class="kcpc-model-chips"></div>
                        <input type="text" class="kcpc-model-search" placeholder="Search cameraperson...">
                    </div>
                    <div class="kcpc-model-checklist">
                        <c:forEach var="u" items="${camerapersonUsers}">
                            <c:set var="isCurrentAssignee" value="false"/>
                            <c:forEach var="a" items="${shootingAssignments}">
                                <c:if test="${a.cameraperson.id == u.id}"><c:set var="isCurrentAssignee" value="true"/></c:if>
                            </c:forEach>
                            <label class="model-check-item">
                                <input type="checkbox" name="newAssigneeUserIds" value="${u.id}"
                                       data-name="${u.fullName}" ${isCurrentAssignee ? 'checked' : ''}> ${u.fullName}
                                <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                            </label>
                        </c:forEach>
                    </div>
                </div>
                <%-- ENG-102: Model(s)/Talent reassignment - SHOOTING only, same .kcpc-model-picker
                     pattern as Idea Review's own Model(s)/Talent field (reviews-ideas.jspf), reusing
                     the page's existing ${modelUsers} model attribute. Optional (no * in the label),
                     matching Planning's own Model(s) picker - talentUserIds there is never mandatory
                     either. A separate picker from the Cameraperson one above (own checkbox name
                     newModelUserIds) so AdminActionService can tell "no Model(s) change requested"
                     (field omitted) apart from "replace with this exact set" (field present) -
                     reassign-form.js keeps it in sync with the Task Stage select the same way it
                     already does for the Cameraperson/Editor pickers above. --%>
                <div class="kcpc-model-picker reassign-model-picker" data-stage="SHOOTING">
                    <label>Model(s) / Talent</label>
                    <div class="kcpc-model-input">
                        <div class="kcpc-model-chips"></div>
                        <input type="text" class="kcpc-model-search" placeholder="Search model...">
                    </div>
                    <div class="kcpc-model-checklist">
                        <c:forEach var="u" items="${modelUsers}">
                            <c:set var="isCurrentTalent" value="false"/>
                            <c:forEach var="t" items="${talentEntries}">
                                <c:if test="${not empty t.talentUser and t.talentUser.id == u.id}"><c:set var="isCurrentTalent" value="true"/></c:if>
                            </c:forEach>
                            <label class="model-check-item">
                                <input type="checkbox" name="newModelUserIds" value="${u.id}"
                                       data-name="${u.fullName}" ${isCurrentTalent ? 'checked' : ''}> ${u.fullName}
                                <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                            </label>
                        </c:forEach>
                    </div>
                </div>
                <div class="kcpc-model-picker reassign-assignee-picker hidden" data-stage="EDITING">
                    <label>New Assignee(s)</label>
                    <div class="kcpc-model-input">
                        <div class="kcpc-model-chips"></div>
                        <input type="text" class="kcpc-model-search" placeholder="Search editor...">
                    </div>
                    <div class="kcpc-model-checklist">
                        <c:forEach var="u" items="${videoEditorUsers}">
                            <c:set var="isCurrentAssignee" value="false"/>
                            <c:forEach var="a" items="${editingAssignments}">
                                <c:if test="${a.editor.id == u.id}"><c:set var="isCurrentAssignee" value="true"/></c:if>
                            </c:forEach>
                            <label class="model-check-item">
                                <input type="checkbox" name="newAssigneeUserIds" value="${u.id}"
                                       data-name="${u.fullName}" ${isCurrentAssignee ? 'checked' : ''}> ${u.fullName}
                                <span class="muted assignee-task-count">(<c:out value="${u.activeTaskLabel}"/>)</span>
                            </label>
                        </c:forEach>
                    </div>
                </div>
                <label>Reason * <input type="text" name="reason" required></label>
                <button type="submit">Confirm Reassignment</button>
            </form>

            <form class="content-detail-action-form hidden" data-action-key="CANCEL" method="post"
                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/cancel">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Reason * <input type="text" name="reason" required></label>
                <p class="note-box">This permanently cancels the deliverable. Irreversible once Completed.</p>
                <button type="submit" class="secondary">Confirm Cancel</button>
            </form>

            <form class="content-detail-action-form hidden" data-action-key="HOLD" method="post"
                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/hold">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Hold Reason * <input type="text" name="reason" required></label>
                <label>Expected Resume Date (Optional) <input type="date" name="expectedResumeDate"></label>
                <button type="submit">Confirm Hold Work</button>
            </form>
            <form class="content-detail-action-form hidden" data-action-key="RESUME" method="post"
                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/resume">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <button type="submit">Confirm Resume Work</button>
            </form>

            <form class="content-detail-action-form hidden" data-action-key="REOPEN_PUBLISHING" method="post"
                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reopen-publishing">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Reason * <input type="text" name="reason" required></label>
                <label>Assign Publisher now (optional)
                    <select name="publisherUserId">
                        <option value="">&mdash; Select Publisher &mdash;</option>
                        <c:forEach var="u" items="${publisherUsers}">
                            <option value="${u.id}"><c:out value="${u.fullName}"/> (<c:out value="${u.activeTaskLabel}"/>)</option>
                        </c:forEach>
                    </select>
                </label>
                <p class="muted">Leave unselected to assign a Publisher later from the Publishing tab instead.</p>
                <button type="submit">Confirm Reopen</button>
            </form>
            <form class="content-detail-action-form hidden" data-action-key="REOPEN_PERFORMANCE" method="post"
                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reopen-performance">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Reason * <input type="text" name="reason" required></label>
                <button type="submit">Confirm Reopen</button>
            </form>
        </div>

        <%-- ENG-082: Contributors - grouped by actual workflow participation, never inferred from
             Designation (spec §17: "Marketing Manager" ≠ "Approver" unless a real record says so). --%>
        <div class="panel content-detail-contributors">
            <h3 class="content-detail-card-title">Contributors</h3>
            <%-- Avatar is an initial-letter circle (no real photo data exists anywhere in this
                 app) - a badge is only shown for a real, backed distinction (Lead, from the
                 assignment record's own `lead` flag; Reviewer, from an actual decided ReviewCycle
                 in reviewFeedbackHistory) - never a generic "Planned"/"Approver" label inferred
                 from Designation alone (spec §17). --%>
            <ul class="content-detail-contributor-list">
                <c:forEach var="a" items="${shootingAssignments}">
                    <li class="content-detail-contributor-row">
                        <span class="content-detail-avatar"><c:out value="${fn:toUpperCase(fn:substring(a.cameraperson.fullName, 0, 1))}"/></span>
                        <span class="content-detail-contributor-info">
                            <span class="content-detail-contributor-name"><c:out value="${a.cameraperson.fullName}"/></span>
                            <span class="content-detail-contributor-role">Camera Person</span>
                        </span>
                        <c:if test="${a.lead}"><span class="content-detail-contributor-badge">Shoot Lead</span></c:if>
                    </li>
                </c:forEach>
                <c:forEach var="t" items="${talentEntries}">
                    <li class="content-detail-contributor-row">
                        <span class="content-detail-avatar"><c:out value="${fn:toUpperCase(fn:substring(t.talentName, 0, 1))}"/></span>
                        <span class="content-detail-contributor-info">
                            <span class="content-detail-contributor-name"><c:out value="${t.talentName}"/></span>
                            <span class="content-detail-contributor-role">Model</span>
                        </span>
                    </li>
                </c:forEach>
                <c:forEach var="a" items="${editingAssignments}">
                    <li class="content-detail-contributor-row">
                        <span class="content-detail-avatar"><c:out value="${fn:toUpperCase(fn:substring(a.editor.fullName, 0, 1))}"/></span>
                        <span class="content-detail-contributor-info">
                            <span class="content-detail-contributor-name"><c:out value="${a.editor.fullName}"/></span>
                            <span class="content-detail-contributor-role">Video Editor</span>
                        </span>
                        <c:if test="${a.lead}"><span class="content-detail-contributor-badge">Edit Lead</span></c:if>
                    </li>
                </c:forEach>
                <c:forEach var="a" items="${publishingAssignments}">
                    <li class="content-detail-contributor-row">
                        <span class="content-detail-avatar"><c:out value="${fn:toUpperCase(fn:substring(a.publisher.fullName, 0, 1))}"/></span>
                        <span class="content-detail-contributor-info">
                            <span class="content-detail-contributor-name"><c:out value="${a.publisher.fullName}"/></span>
                            <span class="content-detail-contributor-role">Publisher</span>
                        </span>
                    </li>
                </c:forEach>
                <c:forEach var="f" items="${reviewFeedbackHistory}">
                    <c:if test="${not empty f.reviewerName}">
                        <li class="content-detail-contributor-row">
                            <span class="content-detail-avatar"><c:out value="${fn:toUpperCase(fn:substring(f.reviewerName, 0, 1))}"/></span>
                            <span class="content-detail-contributor-info">
                                <span class="content-detail-contributor-name"><c:out value="${f.reviewerName}"/></span>
                                <span class="content-detail-contributor-role">Reviewer &middot; <c:out value="${f.reviewStage}"/></span>
                            </span>
                            <span class="status-pill ${f.decisionCssClass}"><c:out value="${f.decisionLabel}"/></span>
                        </li>
                    </c:if>
                </c:forEach>
                <c:if test="${empty shootingAssignments and empty talentEntries and empty editingAssignments and empty publishingAssignments}">
                    <li class="muted">No contributors yet.</li>
                </c:if>
            </ul>
        </div>

        <%-- ENG-082: Review Feedback History - formal review decisions only (Planning/Shoot/Edit -
             Publishing has no review gate); ordinary Comments are on their own tab, never mixed in. --%>
        <div class="panel content-detail-feedback-history">
            <h3 class="content-detail-card-title">Review Feedback History</h3>
            <c:forEach var="f" items="${reviewFeedbackHistory}" varStatus="fs" begin="0" end="2">
                <div class="content-detail-feedback-entry">
                    <span class="status-pill ${f.decisionCssClass}"><c:out value="${f.reviewStage}"/> &middot; Cycle ${f.cycleNumber}</span>
                    <p><c:out value="${empty f.reason ? f.decisionLabel : f.reason}"/></p>
                    <p class="muted"><c:out value="${empty f.reviewerName ? '' : f.reviewerName}"/><c:if test="${not empty f.decidedAt}"> &middot; ${kcpc:ist(f.decidedAt)}</c:if></p>
                </div>
            </c:forEach>
            <c:if test="${empty reviewFeedbackHistory}"><p class="muted">No review decisions yet.</p></c:if>
            <c:if test="${reviewFeedbackHistory.size() > 3}">
                <details class="feedback-history">
                    <summary>View all feedback</summary>
                    <c:forEach var="f" items="${reviewFeedbackHistory}" begin="3">
                        <div class="content-detail-feedback-entry">
                            <span class="status-pill ${f.decisionCssClass}"><c:out value="${f.reviewStage}"/> &middot; Cycle ${f.cycleNumber}</span>
                            <p><c:out value="${empty f.reason ? f.decisionLabel : f.reason}"/></p>
                            <p class="muted"><c:out value="${empty f.reviewerName ? '' : f.reviewerName}"/><c:if test="${not empty f.decidedAt}"> &middot; ${kcpc:ist(f.decidedAt)}</c:if></p>
                        </div>
                    </c:forEach>
                </details>
            </c:if>
        </div>
    </aside>
    </div>
</main>
<script src="<c:url value='/js/publication-scope.js'/>" defer></script>
<script src="<c:url value='/js/model-picker.js'/>" defer></script>
<script src="<c:url value='/js/assignment-picker.js'/>" defer></script>
<script src="<c:url value='/js/review-decision.js'/>" defer></script>
<script src="<c:url value='/js/stage-discussion.js'/>" defer></script>
<script src="<c:url value='/js/reassign-form.js'/>" defer></script>
<script src="<c:url value='/js/publishing-checklist.js'/>" defer></script>
<script src="<c:url value='/js/my-work-tabs.js'/>" defer></script>
<script src="<c:url value='/js/content-detail.js'/>" defer></script>
<script src="<c:url value='/js/publisher-assignment-modal.js'/>" defer></script>
<script src="<c:url value='/js/skip-stage-modal.js'/>" defer></script>
<script src="<c:url value='/js/performance-metric-correction.js'/>" defer></script>
<script src="<c:url value='/js/script-description-modal.js'/>" defer></script>
</body>
</html>
