<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — ${plan.contentId}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
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
                    <label>New Shoot Date <input type="date" name="newShootDate" min="${today}"></label>
                    <label>New Edit Date <input type="date" name="newEditDate" min="${today}"></label>
                    <label>New Live Date <input type="date" name="newLiveDate" min="${today}"></label>
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
        <div class="overview-grid">
            <p><strong>Priority:</strong> ${plan.contentPriority}</p>
            <p><strong>Planning Mode:</strong> ${plan.planningMode}
               <c:if test="${plan.planningMode == 'URGENT'}"> &mdash; ${plan.urgencyReason}</c:if></p>
            <p><strong>Live Date:</strong> ${plan.plannedLiveDate}</p>
            <p><strong>Shoot Date:</strong> ${plan.plannedShootDate}</p>
            <p><strong>Edit Date:</strong> ${plan.plannedEditDate}</p>
            <p><strong>Drive Link:</strong>
                <c:choose>
                    <c:when test="${not empty plan.folderLink}">${plan.folderLink}</c:when>
                    <c:otherwise><span class="muted">(not set)</span></c:otherwise>
                </c:choose>
            </p>
            <p><strong>Cameraperson(s):</strong>
                <c:forEach var="a" items="${shootingAssignments}" varStatus="s">${a.cameraperson.fullName}<c:if test="${!s.last}">, </c:if></c:forEach>
                <c:if test="${empty shootingAssignments}"><span class="muted">(none)</span></c:if>
            </p>
            <c:if test="${not empty editingAssignments}">
                <p><strong>Editor(s):</strong>
                    <c:forEach var="a" items="${editingAssignments}" varStatus="s">${a.editor.fullName}<c:if test="${!s.last}">, </c:if></c:forEach>
                </p>
            </c:if>
            <c:if test="${not empty marks}">
                <p><strong>Predefined Marks:</strong> Cameraperson ${marks.predefinedCameramanMark} &middot; Editor ${marks.predefinedEditorMark}</p>
            </c:if>
        </div>
    </div>

    <%-- ============================ PLANNING ============================ --%>
    <c:if test="${status == 'PL' or status == 'PLRV'}">
        <div class="panel">
            <h2>Planning Workspace</h2>
            <c:choose>
                <c:when test="${canPlanningExecute and status == 'PL'}">
                    <h3>1. Planning Details</h3>
                    <form id="planning-details-form" method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/plan-submit">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <div class="form-grid">
                            <label>Category (optional) <input type="text" name="categoryText" value="${plan.categoryText}"></label>
                            <label>Priority
                                <select name="contentPriority">
                                    <c:forEach var="p" items="${priorities}">
                                        <option value="${p}" ${p == plan.contentPriority ? 'selected' : ''}>${p}</option>
                                    </c:forEach>
                                </select>
                            </label>
                            <label>SKU Reference
                                <input type="text" name="skuReference" value="${plan.skuReference}">
                                <span class="checkbox-inline">
                                    <input type="checkbox" name="skuNotApplicable" value="true"
                                           ${plan.skuNotApplicable ? 'checked' : ''}> N/A
                                </span>
                            </label>

                            <div class="kcpc-model-picker">
                                <label>Model(s)</label>
                                <div class="kcpc-model-input">
                                    <div class="kcpc-model-chips"></div>
                                    <input type="text" class="kcpc-model-search" placeholder="Search model...">
                                </div>
                                <div class="kcpc-model-checklist">
                                    <c:forEach var="mu" items="${modelUsers}">
                                        <c:set var="isSelected" value="false" />
                                        <c:forEach var="t" items="${talentEntries}">
                                            <c:if test="${t.talentName == mu.fullName}"><c:set var="isSelected" value="true" /></c:if>
                                        </c:forEach>
                                        <label class="model-check-item">
                                            <input type="checkbox" name="modelUserIds" value="${mu.id}"
                                                   data-name="${mu.fullName}" ${isSelected ? 'checked' : ''}> ${mu.fullName}
                                        </label>
                                    </c:forEach>
                                </div>
                            </div>
                            <label>Drive Link <input type="text" name="folderLink" value="${plan.folderLink}"></label>
                            <label>Planning Mode
                                <select name="planningMode">
                                    <option value="STANDARD" ${plan.planningMode == 'STANDARD' ? 'selected' : ''}>Standard</option>
                                    <option value="URGENT" ${plan.planningMode == 'URGENT' ? 'selected' : ''}>Urgent</option>
                                </select>
                            </label>

                            <h3 class="grid-span-all">Schedule</h3>
                            <p class="note-box grid-span-all">Standard: Shoot/Edit Date default to Live Date minus 5/2
                                days unless overridden below. Urgent: required when the Planned Live Date is fewer than
                                5 days away (or intentionally at any distance) — Shoot Date, Edit Date and Urgency
                                Reason become mandatory.</p>

                            <label>Planned Live Date * <input type="date" name="plannedLiveDate" min="${today}" required></label>
                            <label>Shoot Date <input type="date" name="shootDate" min="${today}"></label>
                            <label>Edit Date <input type="date" name="editDate" min="${today}"></label>

                            <label class="grid-span-all">Urgency Reason (required for Urgent)
                                <input type="text" name="urgencyReason"></label>
                        </div>
                    </form>

                    <h3>2. Planned Outputs</h3>
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
                                                                <span class="channel-chip" data-target-id="${m.publicationTarget.id}">${m.publicationTarget.channel.channelHandle}
                                                                    <form method="post" class="chip-remove-form"
                                                                          action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/outputs/${o.reelGroupId}/targets/${m.publicationTarget.id}/remove">
                                                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                                        <button type="submit" class="chip-remove"
                                                                                title="Remove ${m.publicationTarget.channel.channelHandle}">&times;</button>
                                                                    </form>
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
                                                    <select class="kcpc-output-type-select" name="outputType">
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
                </c:when>
                <c:otherwise>
                    <p class="muted">Read-only view: your permissions do not include Planning Execution, or the plan is under review.</p>
                </c:otherwise>
            </c:choose>

            <c:if test="${canAssignCameraperson and status == 'PL'}">
                <h3>3. Shoot Assignment</h3>
                <form class="action-form" method="post"
                      action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting-assignments">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Cameraperson(s)</label>
                    <div class="assign-row">
                        <select name="cameramanUserId">
                            <c:forEach var="u" items="${activeUsers}"><option value="${u.id}">${u.fullName}</option></c:forEach>
                        </select>
                        <button type="submit">Assign</button>
                    </div>
                </form>
            </c:if>

            <c:if test="${status == 'PL' and canPlanningExecute}">
                <h3>4. Submit for Review</h3>
                <p class="note-box">Submits the Planning Details fields above (Section 1) together with this
                    Content ID for Planning Review.</p>
                <div class="btn-row">
                    <button type="submit" form="planning-details-form">Submit for Planning Review</button>
                </div>
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
                        <p class="muted">Requires the Drive Link to be set in Planning.</p>
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
                    <div class="assign-row">
                        <select name="editorUserId">
                            <c:forEach var="u" items="${activeUsers}"><option value="${u.id}">${u.fullName}</option></c:forEach>
                        </select>
                        <button type="submit">Confirm Assignment</button>
                    </div>
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
                <thead><tr><th>Type</th><th>Target</th><th>Timestamp (IST)</th><th>Evidence</th><th></th></tr></thead>
                <tbody>
                <c:forEach var="e" items="${events}">
                    <tr>
                        <td>${e.eventType}</td>
                        <td>${e.publicationTarget.platform.platformName} / ${e.publicationTarget.channel.channelHandle}</td>
                        <td>${kcpc:ist(e.actualPublicationTimestamp)}</td>
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
            <h2 id="performance">Performance</h2>
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
                <li><span class="ts">${kcpc:ist(t.transitionTimestamp)}</span>
                    ${t.fromStatusCode} &rarr; ${t.toStatusCode} (${t.triggerCommand}) by ${t.triggeredBy.fullName}
                    <c:if test="${not empty t.transitionReason}"> — ${t.transitionReason}</c:if>
                </li>
            </c:forEach>
            <c:if test="${empty timeline}"><li class="muted">No transitions yet.</li></c:if>
        </ul>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/publication-scope.js" defer></script>
<script src="${pageContext.request.contextPath}/js/model-picker.js" defer></script>
</body>
</html>
