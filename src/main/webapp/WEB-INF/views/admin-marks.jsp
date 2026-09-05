<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Mark Catalogue</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="page-header-row">
        <div>
            <h1>Mark Catalogue</h1>
        </div>
        <span class="muted" style="font-size:0.82rem;white-space:nowrap;">&#8505; All times are shown in IST</span>
    </div>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <div class="card-head-row">
            <h2>Mark Catalogue</h2>
            <button type="button" class="btn-outline" data-toggle-panel="createMarkPanel">+ Create Mark</button>
        </div>
        <div class="catalogue-table-scroll">
            <table class="data-table admin-table">
                <thead><tr><th>Mark Name</th><th>Applicable Role</th><th>Value</th><th>Active</th><th></th></tr></thead>
                <tbody>
                <c:forEach var="m" items="${markEntries}">
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${m.roleType == 'CAMERAPERSON'}">Cameraperson Mark</c:when>
                                <c:when test="${m.roleType == 'EDITOR'}">Editor Mark</c:when>
                                <c:otherwise>Model Mark</c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${m.roleType == 'CAMERAPERSON'}">Cameraperson</c:when>
                                <c:when test="${m.roleType == 'EDITOR'}">Editor</c:when>
                                <c:otherwise>Model</c:otherwise>
                            </c:choose>
                        </td>
                        <td>${m.markValue}</td>
                        <td>
                            <c:choose>
                                <c:when test="${m.active}"><span class="status-pill status-active">Active</span></c:when>
                                <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <details style="display:inline-block;">
                                <summary class="icon-btn">&#9998;</summary>
                                <div class="catalogue-edit-panel">
                                    <form class="action-form" method="post"
                                          action="${pageContext.request.contextPath}/app/admin/marks/${m.id}">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <label>Value <input type="number" step="0.1" min="0" name="markValue" placeholder="${m.markValue}"></label>
                                        <label class="checkbox-inline" style="display:flex;align-items:center;gap:0.4rem;">
                                            <input type="checkbox" name="isActive" value="true" style="width:auto;margin-top:0;" ${m.active ? 'checked' : ''}> Active
                                        </label>
                                        <label>Reason * <input type="text" name="catalogueReason" required></label>
                                        <div class="btn-row"><button type="submit">Save</button></div>
                                    </form>
                                </div>
                            </details>
                            <details style="display:inline-block;">
                                <summary class="icon-btn">&#128465;</summary>
                                <div class="catalogue-edit-panel">
                                    <form class="action-form" method="post"
                                          action="${pageContext.request.contextPath}/app/admin/marks/${m.id}/delete">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <p class="note-box">This permanently deletes this mark value from the catalogue. Content
                                            Plans that already used it are unaffected.</p>
                                        <label>Reason * <input type="text" name="catalogueReason" required></label>
                                        <div class="btn-row"><button type="submit" class="btn-outline-danger" style="width:auto;">Delete</button></div>
                                    </form>
                                </div>
                            </details>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty markEntries}">
                    <tr><td colspan="5" class="muted">No mark catalogue entries yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
        <div class="catalogue-create-body hidden" id="createMarkPanel">
            <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/admin/marks">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Applicable Role
                    <select name="roleType">
                        <option value="CAMERAPERSON">Cameraperson</option>
                        <option value="EDITOR">Editor</option>
                        <option value="MODEL">Model</option>
                    </select>
                </label>
                <label>Value * <input type="number" step="0.1" min="0" name="markValue" placeholder="e.g. 0.1" required></label>
                <label>Reason * <input type="text" name="catalogueReason" placeholder="Enter reason" required></label>
                <div class="btn-row"><button type="submit">Create Mark</button></div>
            </form>
        </div>
    </div>

    <p class="note-box" style="margin-top:1.25rem;">These values drive the Cameraperson/Editor/Model Mark
        dropdowns on Idea Review approval. Deactivating or deleting a value only affects future approvals -
        Content Plans that already used it keep their recorded mark unchanged.</p>
</main>
<script src="<c:url value='/js/admin-shared.js'/>" defer></script>
</body>
</html>
