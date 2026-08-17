<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
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
        <span class="status-badge">${idea.workflowInstance.currentStatusCode.statusName}</span></h1>

    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <h2>Idea Details</h2>
        <p><strong>Reference / Note:</strong>
            <c:choose>
                <c:when test="${not empty idea.referenceLink}">${idea.referenceLink}</c:when>
                <c:otherwise><span class="muted">(none)</span></c:otherwise>
            </c:choose>
        </p>
        <p><strong>Submitted by:</strong> ${idea.submittedBy.fullName} &middot; ${kcpc:ist(idea.submittedAt)}</p>
        <p><strong>Remarks:</strong>
            <c:choose>
                <c:when test="${not empty idea.notesRemarks}">${idea.notesRemarks}</c:when>
                <c:otherwise><span class="muted">(none)</span></c:otherwise>
            </c:choose>
        </p>
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

    <c:if test="${not empty contentPlanId}">
        <div class="panel">
            <h2>Deliverable</h2>
            <p>This idea was approved and moved into Planning.
                <a href="${pageContext.request.contextPath}/app/deliverables/${contentPlanId}">Open the deliverable &raquo;</a></p>
        </div>
    </c:if>
</main>
</body>
</html>
