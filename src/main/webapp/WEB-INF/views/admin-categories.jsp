<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Category Catalogue</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="page-header-row">
        <div>
            <h1>Category Catalogue</h1>
        </div>
        <span class="muted" style="font-size:0.82rem;white-space:nowrap;">&#8505; All times are shown in IST</span>
    </div>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <div class="card-head-row">
            <h2>Category Catalogue</h2>
            <button type="button" class="btn-outline" data-toggle-panel="createCategoryPanel">+ Create Category</button>
        </div>
        <div class="catalogue-table-scroll">
            <table class="data-table admin-table">
                <thead><tr><th>Category</th><th>Status</th><th></th></tr></thead>
                <tbody>
                <c:forEach var="cat" items="${categoryEntries}">
                    <tr>
                        <td>${cat.name}</td>
                        <td>
                            <c:choose>
                                <c:when test="${cat.active}"><span class="status-pill status-active">Active</span></c:when>
                                <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${cat.defaultCategory}">
                                    <span class="muted">&mdash;</span>
                                </c:when>
                                <c:otherwise>
                                    <details style="display:inline-block;">
                                        <summary class="icon-btn">&#9998;</summary>
                                        <div class="catalogue-edit-panel">
                                            <form class="action-form" method="post"
                                                  action="${pageContext.request.contextPath}/app/admin/categories/${cat.id}">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                <label>Category Name <input type="text" name="name" placeholder="${cat.name}"></label>
                                                <label class="checkbox-inline" style="display:flex;align-items:center;gap:0.4rem;">
                                                    <input type="checkbox" name="isActive" value="true" style="width:auto;margin-top:0;" ${cat.active ? 'checked' : ''}> Active
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
                                                  action="${pageContext.request.contextPath}/app/admin/categories/${cat.id}/delete">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                <p class="note-box">If this category is already used by an existing Content Plan,
                                                    delete is refused - deactivate it instead so those records keep their
                                                    historical category.</p>
                                                <label>Reason * <input type="text" name="catalogueReason" required></label>
                                                <div class="btn-row"><button type="submit" class="btn-outline-danger" style="width:auto;">Delete</button></div>
                                            </form>
                                        </div>
                                    </details>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty categoryEntries}">
                    <tr><td colspan="3" class="muted">No category catalogue entries yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
        <div class="catalogue-create-body hidden" id="createCategoryPanel">
            <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/admin/categories">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Category Name * <input type="text" name="name" placeholder="e.g. Saree" required></label>
                <label>Reason * <input type="text" name="catalogueReason" placeholder="Enter reason" required></label>
                <div class="btn-row"><button type="submit">Create Category</button></div>
            </form>
        </div>
    </div>

    <p class="note-box" style="margin-top:1.25rem;">These values drive the Planning Category dropdown on Idea
        Review approval. N/A is the permanent default - it can never be renamed, deactivated, or deleted.
        Deactivating or deleting a category only affects future approvals - Content Plans that already used it
        keep their recorded category unchanged.</p>
</main>
<script src="<c:url value='/js/admin-shared.js'/>" defer></script>
</body>
</html>
