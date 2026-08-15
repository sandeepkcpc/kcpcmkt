<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — ${plan.contentId}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/home">Home</a>
    <a class="header-link" href="${pageContext.request.contextPath}/app/pipeline">Pipeline</a>
</header>
<main class="app-main">
    <h1>${plan.contentId} &middot; ${plan.idea.title}</h1>
    <div class="status-strip">
        <span class="status-badge">${status.statusName}</span>
        <c:if test="${delayed}"><span class="flag-chip flag-delayed">Delayed</span></c:if>
        <c:if test="${not empty openHold}"><span class="flag-chip flag-hold">On Hold</span></c:if>
    </div>

    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <%-- ============================ ADMIN ACTIONS ============================ --%>
    <div class="actions-bar">
        <c:if test="${canReschedule}">
            <details>
                <summary>Reschedule</summary>
                <form class="action-form" method="post"
                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reschedule">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Stage Context
                        <select name="stageContext">
                            <c:forEach var="sc" items="${stageContexts}"><option value="${sc}">${sc}</option></c:forEach>
                        </select>
                    </label>
                    <label>New Shoot Date <input type="date" name="newShootDate"></label>
                    <label>New Edit Date <input type="date" name="newEditDate"></label>
                    <label>New Live Date <input type="date" name="newLiveDate"></label>
                    <label>Reason * <input type="text" name="reason" required></label>
                    <button type="submit">Confirm Reschedule</button>
                </form>
            </details>
        </c:if>
        <c:if test="${canReassign}">
            <details>
                <summary>Reassign</summary>
                <form class="action-form" method="post"
                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reassign">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Task Stage
                        <select name="taskStage">
                            <c:forEach var="ts" items="${taskStages}"><option value="${ts}">${ts}</option></c:forEach>
                        </select>
                    </label>
                    <label>New Assignee(s)
                        <select name="newAssigneeUserIds" multiple size="4">
                            <c:forEach var="u" items="${activeUsers}"><option value="${u.id}">${u.fullName}</option></c:forEach>
                        </select>
                    </label>
                    <label>Reason * <input type="text" name="reason" required></label>
                    <button type="submit">Confirm Reassignment</button>
                </form>
            </details>
        </c:if>
        <c:if test="${canCancel}">
            <details>
                <summary>Cancel</summary>
                <form class="action-form" method="post"
                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/cancel">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Reason * <input type="text" name="reason" required></label>
                    <p class="note-box">This permanently cancels the deliverable. Irreversible once Completed.</p>
                    <button type="submit" class="secondary">Confirm Cancel</button>
                </form>
            </details>
        </c:if>
        <c:if test="${isNative and (status == 'SIP' or status == 'ED')}">
            <c:choose>
                <c:when test="${empty openHold}">
                    <details>
                        <summary>Hold</summary>
                        <form class="action-form" method="post"
                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/hold">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Reason * <input type="text" name="reason" required></label>
                            <button type="submit">Confirm Hold</button>
                        </form>
                    </details>
                </c:when>
                <c:otherwise>
                    <form class="action-form" method="post"
                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/resume">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <button type="submit">Resume</button>
                    </form>
                </c:otherwise>
            </c:choose>
        </c:if>
        <c:if test="${status == 'COMP'}">
            <c:if test="${canPublishingExecute}">
                <details>
                    <summary>Reopen for Publishing</summary>
                    <form class="action-form" method="post"
                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reopen-publishing">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <label>Reason * <input type="text" name="reason" required></label>
                        <button type="submit">Confirm Reopen</button>
                    </form>
                </details>
            </c:if>
            <c:if test="${canPerformanceUpdate}">
                <details>
                    <summary>Reopen for Performance</summary>
                    <form class="action-form" method="post"
                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/reopen-performance">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <label>Reason * <input type="text" name="reason" required></label>
                        <button type="submit">Confirm Reopen</button>
                    </form>
                </details>
            </c:if>
        </c:if>
    </div>

    <%-- ============================ OVERVIEW ============================ --%>
    <div class="panel">
        <h2>Overview</h2>
        <p><strong>Priority:</strong> ${plan.contentPriority} &middot;
           <strong>Planning Mode:</strong> ${plan.planningMode}
           <c:if test="${plan.planningMode == 'URGENT'}"> &mdash; ${plan.urgencyReason}</c:if></p>
        <p><strong>Live Date:</strong> ${plan.plannedLiveDate} &middot;
           <strong>Shoot Date:</strong> ${plan.plannedShootDate} &middot;
           <strong>Edit Date:</strong> ${plan.plannedEditDate}</p>
        <p><strong>Folder Link:</strong>
            <c:choose>
                <c:when test="${not empty plan.folderLink}">${plan.folderLink}</c:when>
                <c:otherwise><span class="muted">(not set)</span></c:otherwise>
            </c:choose>
        </p>
        <p><strong>Cameraperson(s):</strong>
            <c:forEach var="a" items="${shootingAssignments}" varStatus="s">${a.cameraperson.fullName}<c:if test="${!s.last}">, </c:if></c:forEach>
            <c:if test="${empty shootingAssignments}"><span class="muted">(none)</span></c:if>
        </p>
        <p><strong>Editor(s):</strong>
            <c:forEach var="a" items="${editingAssignments}" varStatus="s">${a.editor.fullName}<c:if test="${!s.last}">, </c:if></c:forEach>
            <c:if test="${empty editingAssignments}"><span class="muted">(none)</span></c:if>
        </p>
        <c:if test="${not empty marks}">
            <p><strong>Predefined Marks:</strong> Cameraperson ${marks.predefinedCameramanMark} &middot; Editor ${marks.predefinedEditorMark}</p>
        </c:if>
    </div>

    <%-- ============================ PLANNING ============================ --%>
    <c:if test="${status == 'PL' or status == 'PLRV'}">
        <div class="panel">
            <h2>Planning Workspace</h2>
            <c:choose>
                <c:when test="${canPlanningExecute and status == 'PL'}">
                    <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/parameters">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <label>Category (optional) <input type="text" name="categoryText" value="${plan.categoryText}"></label>
                        <label>Priority
                            <select name="contentPriority">
                                <c:forEach var="p" items="${priorities}">
                                    <option value="${p}" ${p == plan.contentPriority ? 'selected' : ''}>${p}</option>
                                </c:forEach>
                            </select>
                        </label>
                        <div class="field-row">
                            <div><label>SKU Reference <input type="text" name="skuReference" value="${plan.skuReference}"></label></div>
                            <div><label>SKU N/A <input type="checkbox" name="skuNotApplicable" value="true" style="width:auto"
                                                        ${plan.skuNotApplicable ? 'checked' : ''}></label></div>
                        </div>
                        <label>Talent (comma-separated names)
                            <input type="text" name="talentNamesCsv"
                                   value="<c:forEach var="t" items="${talentEntries}" varStatus="s">${t.talentName}<c:if test="${!s.last}">, </c:if></c:forEach>">
                        </label>
                        <label>Content Asset Folder Link <input type="text" name="folderLink" value="${plan.folderLink}"></label>
                        <button type="submit">Save Parameters</button>
                    </form>

                    <h3>Schedule</h3>
                    <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/schedule/standard">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <div class="field-row">
                            <div><label>Planned Live Date * <input type="date" name="plannedLiveDate" required></label></div>
                            <div><label>Shoot Date override <input type="date" name="shootDateOverride"></label></div>
                            <div><label>Edit Date override <input type="date" name="editDateOverride"></label></div>
                        </div>
                        <button type="submit">Save Standard Schedule</button>
                    </form>
                    <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/schedule/urgent">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <p class="note-box">Use Urgent when the Planned Live Date is fewer than 5 days away, or intentionally at any distance.</p>
                        <div class="field-row">
                            <div><label>Planned Live Date * <input type="date" name="plannedLiveDate" required></label></div>
                            <div><label>Shoot Date * <input type="date" name="shootDate" required></label></div>
                            <div><label>Edit Date * <input type="date" name="editDate" required></label></div>
                        </div>
                        <label>Urgency Reason * <input type="text" name="urgencyReason" required></label>
                        <button type="submit">Save Urgent Schedule</button>
                    </form>

                    <h3>Planned Outputs</h3>
                    <table class="data-table">
                        <thead><tr><th>Type</th><th>Reel Type</th><th>Description</th><th>Targets</th></tr></thead>
                        <tbody>
                        <c:forEach var="o" items="${outputs}">
                            <tr>
                                <td>${o.outputType}</td>
                                <td>${o.reelType}</td>
                                <td>${o.titleDescription}</td>
                                <td>
                                    <c:forEach var="m" items="${outputTargetMappings[o.id]}">
                                        ${m.publicationTarget.platform.platformName}/${m.publicationTarget.channel.channelHandle}<br/>
                                    </c:forEach>
                                    <details>
                                        <summary>Map targets</summary>
                                        <form class="action-form" method="post"
                                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.id}/targets">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <select name="publicationTargetIds" multiple size="5">
                                                <c:forEach var="pt" items="${activePublicationTargets}">
                                                    <option value="${pt.id}">${pt.platform.platformName} / ${pt.channel.channelHandle}</option>
                                                </c:forEach>
                                            </select>
                                            <button type="submit">Save Targets</button>
                                        </form>
                                    </details>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                    <details>
                        <summary>+ Add Output</summary>
                        <form class="action-form" method="post"
                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Output Type
                                <select name="outputType">
                                    <c:forEach var="t" items="${outputTypes}"><option value="${t}">${t}</option></c:forEach>
                                </select>
                            </label>
                            <label>Reel Type (Reel only)
                                <select name="reelType">
                                    <option value="">(n/a)</option>
                                    <c:forEach var="rt" items="${reelTypes}"><option value="${rt}">${rt}</option></c:forEach>
                                </select>
                            </label>
                            <label>Description <input type="text" name="titleDescription"></label>
                            <button type="submit">Add Output</button>
                        </form>
                    </details>
                </c:when>
                <c:otherwise>
                    <p class="muted">Read-only view: your permissions do not include Planning Execution, or the plan is under review.</p>
                </c:otherwise>
            </c:choose>

            <c:if test="${canAssignCameraperson and status == 'PL'}">
                <h3>Cameraperson Assignment</h3>
                <form class="action-form" method="post"
                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting-assignments">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <select name="cameramanUserId">
                        <c:forEach var="u" items="${activeUsers}"><option value="${u.id}">${u.fullName}</option></c:forEach>
                    </select>
                    <button type="submit">Assign</button>
                </form>
            </c:if>

            <c:if test="${status == 'PL' and canPlanningExecute}">
                <h3>Submit for Review</h3>
                <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/planning-review/submit">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <button type="submit">Submit for Planning Review</button>
                </form>
            </c:if>

            <c:if test="${status == 'PLRV'}">
                <h3>Planning Review Decision</h3>
                <c:choose>
                    <c:when test="${canDecidePlanningReview}">
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/planning-review/decision">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Decision
                                <select name="approve">
                                    <option value="true">Approve</option>
                                    <option value="false">Request Rework</option>
                                </select>
                            </label>
                            <label>Reason (mandatory for Request Rework) <input type="text" name="reason"></label>
                            <button type="submit">Submit Decision</button>
                        </form>
                    </c:when>
                    <c:otherwise>
                        <p class="note-box">
                            <c:choose>
                                <c:when test="${planningSelfReviewBlocked}">You prepared this plan — the whole decision block (Approve and Request Rework) is disabled.</c:when>
                                <c:otherwise>You do not hold Planning Review authority.</c:otherwise>
                            </c:choose>
                        </p>
                    </c:otherwise>
                </c:choose>
            </c:if>
        </div>
    </c:if>

    <%-- ============================ SHOOT ============================ --%>
    <c:if test="${status == 'SA' or status == 'SIP' or status == 'SRV'}">
        <div class="panel">
            <h2>Shoot</h2>
            <c:if test="${status == 'SA' and isShootAssigneeOrNative}">
                <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/start">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <button type="submit">Start Shoot</button>
                </form>
            </c:if>
            <c:if test="${status == 'SIP'}">
                <c:if test="${isShootAssigneeOrNative and empty openHold}">
                    <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/review/submit">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <p class="muted">Requires the Content Asset Folder Link to be set in Planning.</p>
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
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/review/decision">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Decision
                                <select name="approve">
                                    <option value="true">Approve</option>
                                    <option value="false">Request Rework</option>
                                </select>
                            </label>
                            <label>Confirm qualifying Cameraperson(s) (Approve only)
                                <select name="qualifyingRecipientUserIds" multiple size="4">
                                    <c:forEach var="p" items="${shootingParticipants}">
                                        <option value="${p.cameraperson.id}">${p.cameraperson.fullName}</option>
                                    </c:forEach>
                                </select>
                            </label>
                            <p class="note-box">Each confirmed contributor receives the FULL predefined Cameraperson mark (no split).</p>
                            <label>Reason (mandatory for Request Rework) <input type="text" name="reason"></label>
                            <button type="submit">Submit Decision</button>
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
        </div>
    </c:if>

    <%-- ============================ EDIT ============================ --%>
    <c:if test="${status == 'SAP' or status == 'EA' or status == 'ED' or status == 'ERV'}">
        <div class="panel">
            <h2>Edit</h2>
            <c:if test="${status == 'SAP' and canAssignEditor}">
                <p class="note-box">Editor assignment is available only after Shoot Approval.</p>
                <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/assignments">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <select name="editorUserId">
                        <c:forEach var="u" items="${activeUsers}"><option value="${u.id}">${u.fullName}</option></c:forEach>
                    </select>
                    <button type="submit">Confirm Assignment</button>
                </form>
            </c:if>
            <c:if test="${status == 'EA' and isEditAssigneeOrNative}">
                <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/start">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <button type="submit">Start Edit</button>
                </form>
            </c:if>
            <c:if test="${status == 'ED'}">
                <c:if test="${isEditAssigneeOrNative and empty openHold}">
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
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/editing/review/decision">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Decision
                                <select name="approve">
                                    <option value="true">Approve</option>
                                    <option value="false">Request Rework</option>
                                </select>
                            </label>
                            <label>Confirm qualifying Editor(s) (Approve only)
                                <select name="qualifyingRecipientUserIds" multiple size="4">
                                    <c:forEach var="p" items="${editingParticipants}">
                                        <option value="${p.editor.id}">${p.editor.fullName}</option>
                                    </c:forEach>
                                </select>
                            </label>
                            <label>Reason (mandatory for Request Rework) <input type="text" name="reason"></label>
                            <button type="submit">Submit Decision</button>
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
        </div>
    </c:if>

    <%-- ============================ PUBLISHING ============================ --%>
    <c:if test="${status == 'RFP' or status == 'PUBG' or status == 'PP'}">
        <div class="panel">
            <h2>Publishing</h2>
            <c:if test="${status == 'RFP' and canPublishingExecute}">
                <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/start">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <button type="submit">Start Publishing</button>
                </form>
            </c:if>
            <c:if test="${(status == 'PUBG' or status == 'PP') and canPublishingExecute}">
                <h3>Record Actual Publication Event</h3>
                <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/events">
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
                    <label>Event Type
                        <select name="eventType">
                            <c:forEach var="et" items="${eventTypes}"><option value="${et}">${et}</option></c:forEach>
                        </select>
                    </label>
                    <label>Actual Publication Date * <input type="date" name="actualPublicationTimestamp" required></label>
                    <label>Evidence URL * <input type="url" name="evidenceUrl" required></label>
                    <button type="submit">Save Event</button>
                </form>

                <h3>Target N/A</h3>
                <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/targets/na">
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
                    <label>Reason * <input type="text" name="reason" required></label>
                    <p class="note-box">At least one target must remain live or eligible — all-N/A is blocked.</p>
                    <button type="submit">Mark N/A</button>
                </form>
            </c:if>

            <h3>Actual Publication Events</h3>
            <table class="data-table">
                <thead><tr><th>Type</th><th>Target</th><th>Timestamp</th><th>Evidence</th><th></th></tr></thead>
                <tbody>
                <c:forEach var="e" items="${events}">
                    <tr>
                        <td>${e.eventType}</td>
                        <td>${e.publicationTarget.platform.platformName} / ${e.publicationTarget.channel.channelHandle}</td>
                        <td>${e.actualPublicationTimestamp}</td>
                        <td>${e.evidenceUrl}</td>
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
                <c:if test="${empty events}"><tr><td colspan="5" class="muted">No publication events yet.</td></tr></c:if>
                </tbody>
            </table>
        </div>
    </c:if>

    <%-- ============================ PERFORMANCE ============================ --%>
    <c:if test="${status == 'PP' or status == 'PFUP' or status == 'COMP'}">
        <div class="panel">
            <h2>Performance</h2>
            <c:forEach var="ob" items="${obligations}">
                <h3>Obligation — Due ${ob.performanceDueDate} (non-reschedulable) ${ob.completed ? '— COMPLETED' : ''}</h3>
                <c:set var="sc" value="${scorecardsByObligation[ob.id]}"/>
                <c:choose>
                    <c:when test="${not empty sc and sc.submitted}">
                        <p>Hook Rate: <c:out value="${sc.hookRatePercent}" default="N/A"/>% &middot;
                           Hold Rate: <c:out value="${sc.holdRatePercent}" default="N/A"/>% &middot;
                           CTR: <c:out value="${sc.ctrPercent}" default="N/A"/>%</p>
                        <c:if test="${canPerformanceUpdate}">
                            <details>
                                <summary>Correct a metric</summary>
                                <form class="action-form" method="post"
                                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/scorecards/${sc.id}/corrections">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <label>Corrected Link Clicks <input type="number" name="correctedLinkClicks"></label>
                                    <label>Reason * <input type="text" name="correctionReason" required></label>
                                    <button type="submit">Save Correction</button>
                                </form>
                            </details>
                        </c:if>
                    </c:when>
                    <c:when test="${canPerformanceUpdate}">
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/${ob.id}/draft">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <div class="field-row">
                                <div><label>3-sec Views <input type="number" name="views3sec"></label></div>
                                <div><label>Plays <input type="number" name="plays"></label></div>
                            </div>
                            <div class="field-row">
                                <div><label>Avg Watch (s) <input type="number" step="0.01" name="averageWatchTimeSeconds"></label></div>
                                <div><label>Video Length (s) <input type="number" step="0.01" name="videoLengthSeconds"></label></div>
                            </div>
                            <div class="field-row">
                                <div><label>Link Clicks <input type="number" name="linkClicks"></label></div>
                                <div><label>Impressions <input type="number" name="impressions"></label></div>
                            </div>
                            <p class="note-box">Any field may be left blank while a draft. Metrics not applicable to this platform/output should be handled via N/A (server-side default false here; use the correction path once submitted).</p>
                            <button type="submit">Save Draft</button>
                        </form>
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/performance/${ob.id}/submit">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit">Submit Scorecard (final)</button>
                        </form>
                    </c:when>
                    <c:otherwise><p class="muted">No draft yet.</p></c:otherwise>
                </c:choose>
            </c:forEach>
            <c:if test="${empty obligations}"><p class="muted">No performance obligations yet.</p></c:if>
        </div>
    </c:if>

    <%-- ============================ TIMELINE ============================ --%>
    <div class="panel">
        <h2>Timeline</h2>
        <ul class="timeline">
            <c:forEach var="t" items="${timeline}">
                <li><span class="ts">${t.transitionTimestamp}</span>
                    ${t.fromStatusCode} &rarr; ${t.toStatusCode} (${t.triggerCommand}) by ${t.triggeredBy.fullName}
                    <c:if test="${not empty t.transitionReason}"> — ${t.transitionReason}</c:if>
                </li>
            </c:forEach>
            <c:if test="${empty timeline}"><li class="muted">No transitions yet.</li></c:if>
        </ul>
    </div>
</main>
</body>
</html>
