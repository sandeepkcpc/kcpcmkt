<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
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
<main class="app-main app-main-wide shoot-task-detail">
    <div class="breadcrumb"><a href="${pageContext.request.contextPath}/app/my-work">My Work</a> / Task Detail</div>

    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="shoot-task-header-row">
        <div>
            <h1>Publishing Task &mdash; ${plan.contentId}</h1>
            <p class="muted"><c:out value="${plan.idea.title}"/></p>
        </div>
        <div class="shoot-status-card">
            <div class="summary-field-label">Current Status</div>
            <div class="shoot-status-value"><span class="status-pill ${publishFriendlyStatusCssClass}"><c:out value="${publishFriendlyStatusLabel}"/></span></div>
            <c:if test="${not empty publishDelayDays}">
                <p class="shoot-delay-note">Publishing delayed by ${publishDelayDays} day<c:if test="${publishDelayDays != 1}">s</c:if></p>
            </c:if>
        </div>
    </div>

    <%-- ENG-068: display-only progress tracker, Publishing's own 5 milestones (no Review/Rework -
         Publishing has no review gate) - never a new backend status, just a friendly view of the
         real WorkflowStatus/WorkflowTransitionHistory/PublishingAssignment data already on this plan. --%>
    <div class="panel shoot-progress-panel">
        <div class="progress-tracker">
            <c:forEach var="step" items="${publishProgressSteps}" varStatus="ps">
                <div class="progress-step progress-step-${step.state}">
                    <div class="progress-step-marker">
                        <c:if test="${step.state == 'done'}">&#10003;</c:if>
                    </div>
                    <div class="progress-step-label"><c:out value="${step.label}"/></div>
                    <c:if test="${not empty step.date}"><div class="progress-step-date">${kcpc:istDate(step.date)}</div></c:if>
                </div>
                <c:if test="${!ps.last}"><div class="progress-step-connector"></div></c:if>
            </c:forEach>
        </div>
    </div>

    <div class="shoot-task-columns">
        <div class="shoot-task-col-main">
            <div class="panel">
                <h2>Content &amp; Publishing Information</h2>
                <div class="shoot-info-sections">
                    <div>
                        <h3 class="stage-block-heading">Content Information</h3>
                        <div class="info-list">
                            <div class="info-row"><span class="summary-field-label">Content ID</span><span class="summary-field-value">${plan.contentId}</span></div>
                            <div class="info-row"><span class="summary-field-label">Content Name</span><span class="summary-field-value"><c:out value="${plan.idea.title}"/></span></div>
                            <div class="info-row"><span class="summary-field-label">Priority</span><span class="summary-field-value">
                                <c:choose>
                                    <c:when test="${not empty plan.contentPriority}">
                                        <span class="priority-pill priority-${plan.contentPriority == 'HIGH' ? 'high' : (plan.contentPriority == 'MEDIUM' ? 'medium' : 'low')}"><c:out value="${plan.contentPriority}"/></span>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </span></div>
                            <div class="info-row"><span class="summary-field-label">Planned Output</span><span class="summary-field-value">
                                <c:choose>
                                    <c:when test="${not empty outputGroupRepresentatives}">
                                        <c:forEach var="o" items="${outputGroupRepresentatives}" varStatus="os"><c:out value="${o.outputType}"/><c:if test="${!os.last}">, </c:if></c:forEach>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </span></div>
                        </div>
                    </div>
                    <div>
                        <h3 class="stage-block-heading">Publishing Information</h3>
                        <div class="info-list">
                            <div class="info-row"><span class="summary-field-label">Planned Live Date</span><span class="summary-field-value">${empty plan.plannedLiveDate ? '—' : plan.plannedLiveDate}</span></div>
                            <div class="info-row"><span class="summary-field-label">Publisher(s)</span><span class="summary-field-value">
                                <c:forEach var="a" items="${publishingAssignments}" varStatus="as"><c:out value="${a.publisher.fullName}"/><c:if test="${!as.last}">, </c:if></c:forEach>
                                <c:if test="${empty publishingAssignments}">&mdash;</c:if>
                            </span></div>
                            <div class="info-row"><span class="summary-field-label">Targets</span><span class="summary-field-value">
                                <c:set var="resolvedCount" value="0"/>
                                <c:forEach var="row" items="${publishingChecklist}"><c:if test="${row.completed}"><c:set var="resolvedCount" value="${resolvedCount + 1}"/></c:if></c:forEach>
                                ${resolvedCount} / ${publishingChecklist.size()} resolved
                            </span></div>
                            <div class="info-row"><span class="summary-field-label">Drive Link</span><span class="summary-field-value">
                                <c:choose>
                                    <c:when test="${not empty plan.folderLink}"><a class="drive-link" href="${plan.folderLink}" target="_blank" rel="noopener noreferrer">Open Drive Link &#8599;</a></c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </span></div>
                        </div>
                    </div>
                </div>

                <c:if test="${isPublishActiveAssignee}">
                    <p class="info-strip">&#8505;&nbsp; You are participating in this publishing task.</p>
                </c:if>
            </div>

            <div class="panel">
                <h2>Publishing Instructions</h2>
                <p class="stage-description-text ${empty plan.publishingDescription ? 'muted' : ''}"><c:out value="${empty plan.publishingDescription ? 'No instructions provided.' : plan.publishingDescription}"/></p>
            </div>

            <%-- Publication Targets checklist: identical markup/classes/action URL to the shared
                 deliverable-detail.jsp Publishing panel, so the existing publishing-checklist.js
                 keeps working unmodified. --%>
            <div class="panel">
                <h2>Publication Targets</h2>
                <c:choose>
                    <c:when test="${status == 'PUBG' and canPublishingExecute and isPublishActiveAssignee}">
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
                                                <c:when test="${row.completed}"><span class="status-pill status-completed">Completed</span></c:when>
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
                                <button type="submit" id="publishing-checklist-submit" disabled>Submit Published Tasks</button>
                            </div>
                        </form>
                    </c:when>
                    <c:otherwise>
                        <table class="data-table" id="publishing-checklist-table">
                            <thead>
                            <tr><th>Planned Output</th><th>Type</th><th>Platform</th><th>Channel</th><th>Evidence URL</th><th>Status</th></tr>
                            </thead>
                            <tbody>
                            <c:forEach var="row" items="${publishingChecklist}">
                                <tr>
                                    <td><c:out value="${empty row.plannedOutput.titleDescription ? '—' : row.plannedOutput.titleDescription}"/></td>
                                    <td>${row.plannedOutput.outputType}<c:if test="${row.plannedOutput.outputType == 'REEL'}"> &middot; ${row.plannedOutput.reelType}</c:if></td>
                                    <td><c:out value="${row.publicationTarget.platform.platformName}"/></td>
                                    <td><c:out value="${row.publicationTarget.channel.channelHandle}"/></td>
                                    <td>
                                        <c:if test="${row.completed}">
                                            <a href="${row.completedEvent.evidenceUrl}" target="_blank" rel="noopener noreferrer"><c:out value="${row.completedEvent.evidenceUrl}"/></a>
                                        </c:if>
                                        <c:if test="${!row.completed}">&mdash;</c:if>
                                    </td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${row.completed}"><span class="status-pill status-completed">Completed</span></c:when>
                                            <c:otherwise><span class="status-pill status-pending">Pending</span></c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty publishingChecklist}">
                                <tr><td colspan="6" class="muted">No Publication Targets to publish.</td></tr>
                            </c:if>
                            </tbody>
                        </table>
                    </c:otherwise>
                </c:choose>
            </div>

            <%-- Comments: identical markup/classes/action URLs to the shared deliverable-detail.jsp
                 Publishing panel (own-comment-only edit/delete, soft-delete, AJAX-enhanced) so the
                 existing stage-discussion.js keeps working unmodified. --%>
            <div class="panel">
                <h2>Comments</h2>
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
                        <c:if test="${empty publishingComments}"><p class="muted">No comments yet.</p></c:if>
                    </div>
                    <c:if test="${canCommentOnPublishing}">
                        <form class="action-form stage-comment-form" method="post"
                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/comments">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <textarea name="commentText" rows="2" placeholder="Write a comment..." maxlength="1000" required></textarea>
                            <button type="submit">Comment</button>
                        </form>
                    </c:if>
                </div>
            </div>
        </div>

        <div class="shoot-task-col-side">
            <div class="panel shoot-action-panel">
                <c:choose>
                    <c:when test="${status == 'RFP' and isPublishActiveAssignee}">
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/publishing/start">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit" class="shoot-primary-action">&#9654; Start Publishing</button>
                        </form>
                    </c:when>
                    <c:when test="${status == 'RFP'}">
                        <p class="shoot-status-compact">Ready for Publishing</p>
                    </c:when>
                    <c:when test="${status == 'PUBG'}">
                        <p class="shoot-status-compact">Publishing in progress &mdash; record events in Publication Targets</p>
                    </c:when>
                    <c:when test="${status == 'PP'}">
                        <p class="shoot-status-compact">Publication scope resolved &mdash; Performance Pending</p>
                    </c:when>
                </c:choose>
                <c:if test="${not empty plan.folderLink}">
                    <a class="btn-outline shoot-drive-action" href="${plan.folderLink}" target="_blank" rel="noopener noreferrer">&#128193; Open Drive Link</a>
                </c:if>
            </div>

            <div class="panel">
                <h2>Timeline / Activity</h2>
                <ul class="timeline">
                    <c:forEach var="t" items="${timeline}" end="4">
                        <li><span class="ts">${kcpc:ist(t.transitionTimestamp)}</span>
                            <strong>${t.fromStatusCode.statusName} &rarr; ${t.toStatusCode.statusName}</strong> by ${t.triggeredBy.fullName}
                        </li>
                    </c:forEach>
                    <c:if test="${empty timeline}"><li class="muted">No activity yet.</li></c:if>
                </ul>
                <c:if test="${timeline.size() > 5}">
                    <details class="feedback-history">
                        <summary>View Full Timeline</summary>
                        <ul class="timeline">
                            <c:forEach var="t" items="${timeline}" begin="5">
                                <li><span class="ts">${kcpc:ist(t.transitionTimestamp)}</span>
                                    <strong>${t.fromStatusCode.statusName} &rarr; ${t.toStatusCode.statusName}</strong> by ${t.triggeredBy.fullName}
                                </li>
                            </c:forEach>
                        </ul>
                    </details>
                </c:if>
            </div>
        </div>
    </div>

    <p><a class="btn-outline" href="${pageContext.request.contextPath}/app/my-work">&larr; Back to My Work</a></p>
</main>
<script src="${pageContext.request.contextPath}/js/publishing-checklist.js" defer></script>
<script src="${pageContext.request.contextPath}/js/stage-discussion.js" defer></script>
</body>
</html>
