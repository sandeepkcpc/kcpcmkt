<%@ page contentType="text/html;charset=UTF-8" language="java" %>
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
<main class="app-main">
    <h1>${idea.businessIdeaCode} &middot; ${idea.title}
        <span class="status-pill ${ideaStatusCssClass}"><c:out value="${ideaStatusLabel}"/></span></h1>

    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <h2>Idea Details</h2>
        <p><strong>Idea ID:</strong> ${idea.businessIdeaCode}</p>
        <p><strong>Title:</strong> <c:out value="${idea.title}"/></p>
        <p><strong>Idea Description / Details:</strong>
            <c:choose>
                <c:when test="${not empty idea.notesRemarks}"><c:out value="${idea.notesRemarks}"/></c:when>
                <c:otherwise><span class="muted">(none)</span></c:otherwise>
            </c:choose>
        </p>
        <p><strong>Additional Note:</strong>
            <c:choose>
                <c:when test="${not empty idea.additionalNote}"><c:out value="${idea.additionalNote}"/></c:when>
                <c:otherwise><span class="muted">(none)</span></c:otherwise>
            </c:choose>
        </p>
        <p><strong>Reference Link:</strong>
            <c:choose>
                <c:when test="${not empty idea.referenceLink}">
                    <a href="${fn:escapeXml(idea.referenceLink)}" target="_blank" rel="noopener noreferrer"><c:out value="${idea.referenceLink}"/></a>
                </c:when>
                <c:otherwise><span class="muted">(none)</span></c:otherwise>
            </c:choose>
        </p>
        <p><strong>Submitted by:</strong> ${idea.submittedBy.fullName}</p>
        <p><strong>Submitted On:</strong> ${kcpc:ist(idea.submittedAt)}</p>
        <p><strong>Current Idea Status:</strong> <span class="status-pill ${ideaStatusCssClass}"><c:out value="${ideaStatusLabel}"/></span></p>
    </div>

    <%-- ENG-059: the actual review decision reason, shown clearly and separately from Idea Details -
         never invented when none exists (Approve records no reason at all). --%>
    <c:if test="${not empty ideaFeedback}">
        <div class="panel">
            <h2>Review Feedback</h2>
            <p><c:out value="${ideaFeedback}"/></p>
        </div>
    </c:if>

    <%-- ENG-061: Idea lifecycle events only (Submitted/Approved/Retained/Rejected/Reopened) - never
         the resulting content's Planning/Shoot/Edit/Publishing operational events. --%>
    <div class="panel">
        <h2>Idea Decision History</h2>
        <ul class="timeline">
            <c:forEach var="event" items="${ideaStatusHistory}">
                <li><span class="ts">${kcpc:ist(event.timestamp)}</span>
                    <c:out value="${event.eventLabel}"/> by <c:out value="${event.triggeredByName}"/>
                    <c:if test="${not empty event.reason}">&mdash; <c:out value="${event.reason}"/></c:if>
                </li>
            </c:forEach>
            <c:if test="${empty ideaStatusHistory}"><li class="muted">No history yet.</li></c:if>
        </ul>
    </div>

    <c:if test="${idea.workflowInstance.currentStatusCode == 'PA'}">
        <div class="panel">
            <h2>Review Decision</h2>
            <c:choose>
                <c:when test="${canDecide}">
                    <form method="post" action="${pageContext.request.contextPath}/app/ideas/${idea.id}/review">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <label>Decision
                            <select name="decision" required>
                                <option value="APPROVE">Approve</option>
                                <option value="REJECT">Reject</option>
                                <option value="RETAIN">Retain</option>
                            </select>
                        </label>
                        <div class="field-row">
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
                        <label>Reason (mandatory for Reject; optional for Retain)
                            <input type="text" name="reason">
                        </label>
                        <button type="submit">Submit Decision</button>
                    </form>
                </c:when>
                <c:otherwise>
                    <p class="note-box">You cannot make a review decision on work you submitted, prepared, or
                        participated in. This idea is routed to another authorized reviewer.</p>
                </c:otherwise>
            </c:choose>
        </div>
    </c:if>

    <c:if test="${idea.workflowInstance.currentStatusCode == 'RET'}">
        <div class="panel">
            <h2>Retained</h2>
            <c:if test="${canDecide}">
                <form method="post" action="${pageContext.request.contextPath}/app/ideas/${idea.id}/reopen">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <button type="submit">Reopen to Pending Approval</button>
                </form>
            </c:if>
        </div>
    </c:if>

    <%-- ENG-061: informational only - the Idea Status above stays "Approved" regardless; this never
         replaces it with a Planning status, and the operational deliverable link itself is CEO/MM-only
         ("Do not expose Planning/Shoot/Edit/Publishing operational details here" for an Employee). --%>
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
</main>
</body>
</html>
