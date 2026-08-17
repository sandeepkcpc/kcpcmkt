<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Idea Queue</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Idea Queue</h1>
    <c:if test="${not empty errorMessage}">
        <div class="alert alert-error">${errorMessage}</div>
    </c:if>
    <table class="data-table">
        <thead>
        <tr>
            <th>Idea ID</th>
            <th>Title</th>
            <th>Submitted By</th>
            <th>Status</th>
            <th>Action</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="idea" items="${ideas}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/app/ideas/${idea.id}">${idea.businessIdeaCode}</a></td>
                <td>${idea.title}</td>
                <td>${idea.submittedBy.fullName}</td>
                <td><span class="status-badge">${idea.workflowInstance.currentStatusCode.statusName}</span></td>
                <td>
                    <c:if test="${idea.workflowInstance.currentStatusCode == 'PA' and canDecideByIdea[idea.id]}">
                        <details>
                            <summary>Decide</summary>
                            <form method="post"
                                  action="${pageContext.request.contextPath}/app/ideas/${idea.id}/review"
                                  class="inline-decision-form">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <label>Decision
                                    <select name="decision">
                                        <option value="APPROVE">Approve</option>
                                        <option value="REJECT">Reject</option>
                                        <option value="RETAIN">Retain</option>
                                    </select>
                                </label>
                                <label>Reason (mandatory for Reject)
                                    <input type="text" name="reason">
                                </label>
                                <label>Cameraperson Mark (Approve only)
                                    <select name="cameramanMark">
                                        <option value="0.0">0</option>
                                        <option value="0.5">0.5</option>
                                        <option value="1.0">1.0</option>
                                        <option value="2.0">2.0</option>
                                        <option value="3.0">3.0</option>
                                    </select>
                                </label>
                                <label>Editor Mark (Approve only)
                                    <select name="editorMark">
                                        <option value="0.0">0</option>
                                        <option value="0.5">0.5</option>
                                        <option value="1.0">1.0</option>
                                        <option value="2.0">2.0</option>
                                        <option value="3.0">3.0</option>
                                    </select>
                                </label>
                                <button type="submit">Confirm</button>
                            </form>
                        </details>
                    </c:if>
                    <c:if test="${idea.workflowInstance.currentStatusCode == 'RET' and canDecideByIdea[idea.id]}">
                        <form method="post" action="${pageContext.request.contextPath}/app/ideas/${idea.id}/reopen">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit">Reopen</button>
                        </form>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</main>
</body>
</html>
