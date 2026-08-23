<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Business Roles</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <h1>Business Roles</h1>
    <p class="admin-page-lead">A Business Role never grants an operational permission by itself.
        Permissions are assigned separately through the Permissions module. Workflow participation
        determines whether employees in this Business Role use a production workspace - it does
        not grant operational permissions either.</p>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="admin-2col">
        <div class="panel">
            <div class="admin-toolbar" id="rolesToolbar">
                <div class="admin-search-field">
                    <span class="admin-search-icon">&#128269;</span>
                    <input type="text" id="roleSearch" placeholder="Search business roles...">
                </div>
                <div class="segmented-control">
                    <button type="button" class="segmented-btn active" data-filter="all">All</button>
                    <button type="button" class="segmented-btn dot-active" data-filter="active"><span class="dot"></span>Active</button>
                    <button type="button" class="segmented-btn dot-inactive" data-filter="inactive"><span class="dot"></span>Deactivated</button>
                </div>
            </div>

            <table class="data-table admin-table" id="rolesTable">
                <thead><tr><th>#</th><th>Business Role</th><th>Access Class</th><th>Workflow</th><th>Status</th><th>Action</th></tr></thead>
                <tbody>
                <c:forEach var="r" items="${roles}" varStatus="st">
                    <tr data-role-row data-status="${r.active ? 'active' : 'inactive'}"
                        data-name="${fn:toLowerCase(r.roleName)}">
                        <td>${st.index + 1}</td>
                        <td>${r.roleName}</td>
                        <td class="admin-access-class">${r.accessClassCode}</td>
                        <td>
                            <c:choose>
                                <c:when test="${r.participatesInWorkflow}"><span class="status-pill status-active">Production</span></c:when>
                                <c:otherwise><span class="status-pill status-inactive">Non-production</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${r.active}"><span class="status-pill status-active">Active</span></c:when>
                                <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:if test="${r.active}">
                                <form method="post" action="${pageContext.request.contextPath}/app/admin/business-roles/${r.id}/deactivate">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <button type="submit" class="btn-outline-danger">&#128274; Deactivate</button>
                                </form>
                            </c:if>
                            <c:if test="${!r.active}">
                                <form method="post" action="${pageContext.request.contextPath}/app/admin/business-roles/${r.id}/activate">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <button type="submit" class="btn-outline-success">&#10003; Activate</button>
                                </form>
                            </c:if>
                            <form method="post" action="${pageContext.request.contextPath}/app/admin/business-roles/${r.id}/workflow-participation">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <input type="hidden" name="participatesInWorkflow" value="${!r.participatesInWorkflow}"/>
                                <c:choose>
                                    <c:when test="${r.participatesInWorkflow}">
                                        <button type="submit" class="btn-outline-danger" title="Restrict this role to My Ideas + Submit Idea only">Mark Non-production</button>
                                    </c:when>
                                    <c:otherwise>
                                        <button type="submit" class="btn-outline-success" title="Give this role full workflow/management nav access">Mark Production</button>
                                    </c:otherwise>
                                </c:choose>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty roles}"><tr><td colspan="6" class="muted">No business roles.</td></tr></c:if>
                </tbody>
            </table>

            <div class="pagination">
                <span id="rolesPaginationSummary"></span>
                <div class="pagination-controls" id="rolesPaginationControls"></div>
            </div>
        </div>

        <div class="panel">
            <h2>Create Business Role</h2>
            <form method="post" action="${pageContext.request.contextPath}/app/admin/business-roles">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Role Name * <input type="text" name="roleName" placeholder="Enter role name" required></label>
                <label>Access Class *
                    <select name="accessClass">
                        <c:forEach var="ac" items="${accessClasses}"><option value="${ac}">${ac}</option></c:forEach>
                    </select>
                </label>
                <label class="checkbox-inline" style="display:flex;align-items:center;gap:0.4rem;margin-top:1rem;">
                    <input type="checkbox" name="participatesInWorkflow" value="true" style="width:auto;margin-top:0;">
                    Participates in Content Production workflow
                </label>
                <p class="note-box">The role will be created as Active by default. You can deactivate it anytime.
                    Safe default: a new role starts Non-production (My Ideas + Submit Idea only) unless you
                    explicitly check workflow participation above.</p>
                <div class="btn-row"><button type="submit">Create</button></div>
            </form>
        </div>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/admin-business-roles.js" defer></script>
</body>
</html>
