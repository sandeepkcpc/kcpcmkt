<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
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
            <h1>Shoot Task &mdash; ${plan.contentId}</h1>
            <p class="muted"><c:out value="${plan.idea.title}"/></p>
        </div>
        <div class="shoot-status-card">
            <div class="summary-field-label">Current Status</div>
            <div class="shoot-status-value"><span class="status-pill ${shootFriendlyStatusCssClass}"><c:out value="${shootFriendlyStatusLabel}"/></span></div>
            <c:if test="${not empty shootDelayDays}">
                <p class="shoot-delay-note">Shoot delayed by ${shootDelayDays} day<c:if test="${shootDelayDays != 1}">s</c:if></p>
            </c:if>
        </div>
    </div>

    <%-- ENG-064: display-only progress tracker - never a new backend status, just a friendly view
         of the real WorkflowStatus/ReviewCycle data already on this plan. --%>
    <div class="panel shoot-progress-panel">
        <div class="progress-tracker">
            <c:forEach var="step" items="${shootProgressSteps}" varStatus="ps">
                <div class="progress-step progress-step-${step.state} ${step.label == 'Rework Required' ? 'progress-step-rework' : ''}">
                    <div class="progress-step-marker">
                        <c:choose>
                            <c:when test="${step.state == 'done'}">&#10003;</c:when>
                            <c:when test="${step.label == 'Rework Required' and step.state == 'current'}">&#8635;</c:when>
                        </c:choose>
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
                <h2>Content &amp; Shoot Information</h2>
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
                            <div class="info-row"><span class="summary-field-label">Reel Type</span><span class="summary-field-value">
                                <c:set var="hasReelType" value="false"/>
                                <c:forEach var="o" items="${outputGroupRepresentatives}" varStatus="os">
                                    <c:if test="${o.outputType == 'REEL' and not empty o.reelType}"><c:set var="hasReelType" value="true"/><c:out value="${o.reelType}"/><c:if test="${!os.last}">, </c:if></c:if>
                                </c:forEach>
                                <c:if test="${!hasReelType}">&mdash;</c:if>
                            </span></div>
                        </div>
                    </div>
                    <div>
                        <h3 class="stage-block-heading">Shoot Information</h3>
                        <div class="info-list">
                            <div class="info-row"><span class="summary-field-label">Planned Shoot Date</span><span class="summary-field-value">${empty plan.plannedShootDate ? '—' : plan.plannedShootDate}</span></div>
                            <div class="info-row"><span class="summary-field-label">Planned Live Date</span><span class="summary-field-value">${empty plan.plannedLiveDate ? '—' : plan.plannedLiveDate}</span></div>
                            <div class="info-row"><span class="summary-field-label">Model(s)</span><span class="summary-field-value">
                                <c:forEach var="t" items="${talentEntries}" varStatus="ts"><c:out value="${t.talentName}"/><c:if test="${!ts.last}">, </c:if></c:forEach>
                                <c:if test="${empty talentEntries}">&mdash;</c:if>
                            </span></div>
                            <div class="info-row"><span class="summary-field-label">Cameraperson(s)</span><span class="summary-field-value">
                                <c:forEach var="a" items="${shootingAssignments}" varStatus="as"><c:out value="${a.cameraperson.fullName}"/><c:if test="${a.lead}"> <span class="lead-badge">Lead</span></c:if><c:if test="${!as.last}">, </c:if></c:forEach>
                                <c:if test="${empty shootingAssignments}">&mdash;</c:if>
                            </span></div>
                            <div class="info-row"><span class="summary-field-label">Shoot Lead</span><span class="summary-field-value">
                                <c:set var="shootLeadOnPage" value="" />
                                <c:forEach var="a" items="${shootingAssignments}"><c:if test="${a.lead}"><c:set var="shootLeadOnPage" value="${a.cameraperson.fullName}" /></c:if></c:forEach>
                                <c:choose>
                                    <c:when test="${empty shootLeadOnPage}">&mdash;</c:when>
                                    <c:otherwise><c:out value="${shootLeadOnPage}"/> <span class="lead-badge">Lead</span></c:otherwise>
                                </c:choose>
                            </span></div>
                            <div class="info-row"><span class="summary-field-label">Drive Link</span><span class="summary-field-value">
                                <c:choose>
                                    <c:when test="${not empty plan.folderLink}"><a class="drive-link" href="${plan.folderLink}" target="_blank" rel="noopener noreferrer">Open Drive Link &#8599;</a></c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </span></div>
                            <%-- Raw shoot footage goes here - the governed "01 - Raw Shoot-{CONTENT_ID}" subfolder. --%>
                            <c:if test="${not empty driveProvisioning and not empty driveProvisioning.rawShootFolderId}">
                                <div class="info-row"><span class="summary-field-label">Raw Shoot Folder</span><span class="summary-field-value">
                                    <a class="drive-link" href="https://drive.google.com/drive/folders/${driveProvisioning.rawShootFolderId}" target="_blank" rel="noopener noreferrer">Open Raw Shoot &#8599;</a>
                                </span></div>
                            </c:if>
                        </div>
                    </div>
                </div>

                <c:if test="${isShootActiveAssignee}">
                    <p class="info-strip">&#8505;&nbsp; You are participating in this shoot.</p>
                </c:if>
            </div>

            <div class="panel">
                <h2>Shoot Instructions</h2>
                <p class="stage-description-text ${empty plan.shootDescription ? 'muted' : ''}"><c:out value="${empty plan.shootDescription ? 'No instructions provided.' : plan.shootDescription}"/></p>
            </div>

            <%-- Comments: identical markup/classes/action URLs to the shared deliverable-detail.jsp
                 Shoot panel (own-comment-only edit/delete, soft-delete, AJAX-enhanced) so the
                 existing stage-discussion.js keeps working unmodified. --%>
            <div class="panel">
                <h2>Comments</h2>
                <div class="stage-comments" data-comments-action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/comments">
                    <div class="stage-comments-list">
                        <c:forEach var="cm" items="${shootComments}">
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
                                                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/comments/${cm.id}/edit">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                <textarea name="commentText" rows="2" required><c:out value="${cm.commentText}"/></textarea>
                                                <div class="review-actions">
                                                    <button type="button" class="stage-comment-edit-cancel-btn">Cancel</button>
                                                    <button type="submit">Save</button>
                                                </div>
                                            </form>
                                            <form class="action-form stage-comment-delete-form hidden" method="post"
                                                  action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/comments/${cm.id}/delete">
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
                        <c:if test="${empty shootComments}"><p class="muted">No comments yet.</p></c:if>
                    </div>
                    <c:if test="${canCommentOnShoot}">
                        <form class="action-form stage-comment-form" method="post"
                              action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/comments">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <textarea name="commentText" rows="2" placeholder="Write a comment..." maxlength="1000" required></textarea>
                            <button type="submit">Comment</button>
                        </form>
                    </c:if>
                </div>
            </div>
        </div>

        <div class="shoot-task-col-side">
            <div class="panel">
                <h2>Latest Reviewer Feedback</h2>
                <c:choose>
                    <c:when test="${not empty shootFeedbackLatest}">
                        <div class="feedback-card shoot-latest-feedback ${shootFeedbackLatest.decisionCssClass == 'status-needschanges' ? 'shoot-feedback-warning' : ''}">
                            <div class="feedback-card-main shoot-feedback-card-main">
                                <div>
                                    <span class="status-pill ${shootFeedbackLatest.decisionCssClass}"><c:out value="${fn:toUpperCase(shootFeedbackLatest.decisionLabel)}"/></span>
                                    <div class="feedback-stage-value">Shoot Review</div>
                                </div>
                                <div class="feedback-date-col">
                                    <div>${kcpc:istDate(shootFeedbackLatest.decidedAt)}</div>
                                    <div class="muted">${kcpc:istTime(shootFeedbackLatest.decidedAt)} IST</div>
                                </div>
                            </div>
                            <div class="feedback-field-label">Feedback / Reason</div>
                            <p class="shoot-feedback-reason"><c:out value="${empty shootFeedbackLatest.reason ? '—' : shootFeedbackLatest.reason}"/></p>
                            <c:if test="${not empty shootFeedbackLatest.reviewerName}">
                                <div class="feedback-field-label">Decided by</div>
                                <div class="feedback-reviewer-name"><c:out value="${shootFeedbackLatest.reviewerName}"/><c:if test="${shootFeedbackLatest.reviewerIsLead}"> (Lead)</c:if></div>
                            </c:if>
                        </div>
                        <c:if test="${not empty shootFeedbackPriorHistory}">
                            <details class="feedback-history">
                                <summary>View Feedback History</summary>
                                <ul class="feedback-history-list">
                                    <c:forEach var="entry" items="${shootFeedbackPriorHistory}">
                                        <li>
                                            <span class="ts">${kcpc:istDate(entry.decidedAt)} &middot; ${kcpc:istTime(entry.decidedAt)}</span>
                                            <span class="status-pill ${entry.decisionCssClass}"><c:out value="${fn:toUpperCase(entry.decisionLabel)}"/></span>
                                            <c:out value="${empty entry.reason ? '—' : entry.reason}"/>
                                        </li>
                                    </c:forEach>
                                </ul>
                            </details>
                        </c:if>
                    </c:when>
                    <c:otherwise><p class="muted">No review feedback yet.</p></c:otherwise>
                </c:choose>
            </div>

            <div class="panel shoot-action-panel">
                <c:choose>
                    <c:when test="${status == 'SA' and isShootActiveAssignee}">
                        <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/start">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit" class="shoot-primary-action">&#9654; Start Shoot</button>
                        </form>
                    </c:when>
                    <c:when test="${status == 'SIP'}">
                        <c:choose>
                            <c:when test="${isShootActiveAssignee and empty openHold}">
                                <form method="post" action="${pageContext.request.contextPath}/app/deliverables/${plan.id}/shooting/review/submit">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <button type="submit" class="shoot-primary-action">&#9654; Submit for Review</button>
                                </form>
                            </c:when>
                            <c:when test="${not empty openHold}">
                                <div class="hold-info-block">
                                    <p class="hold-info-title">&#9888; This task is currently on hold.</p>
                                    <p><strong>Hold Reason:</strong> <c:out value="${openHold.holdReason}"/></p>
                                    <p><strong>Held By:</strong> <c:out value="${openHold.heldBy.fullName}"/></p>
                                    <p><strong>Held On:</strong> ${kcpc:ist(openHold.heldAt)}</p>
                                    <c:if test="${not empty openHold.expectedResumeDate}">
                                        <p><strong>Expected Resume Date:</strong> ${openHold.expectedResumeDate}</p>
                                    </c:if>
                                </div>
                            </c:when>
                        </c:choose>
                    </c:when>
                    <c:when test="${status == 'SRV'}">
                        <p class="shoot-status-compact">Submitted for Review</p>
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
<script src="${pageContext.request.contextPath}/js/stage-discussion.js" defer></script>
</body>
</html>
