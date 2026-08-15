<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Business Roles</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/pipeline">Pipeline</a>
</header>
<nav class="app-nav">
    <a href="${pageContext.request.contextPath}/app/admin/users">Users</a>
    <a href="${pageContext.request.contextPath}/app/admin/business-roles" class="active">Business Roles</a>
    <a href="${pageContext.request.contextPath}/app/admin/permissions">Permissions</a>
    <a href="${pageContext.request.contextPath}/app/admin/catalogue">Publishing Catalogue</a>
</nav>
<main class="app-main">
    <h1>Administration &raquo; Business Roles</h1>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <table class="data-table">
        <thead><tr><th>Business Role</th><th>Access Class</th><th>Active</th><th></th></tr></thead>
        <tbody>
        <c:forEach var="r" items="${roles}">
            <tr>
                <td>${r.roleName}</td>
                <td>${r.accessClassCode}</td>
                <td>${r.active ? 'Yes' : 'No'}</td>
                <td>
                    <c:if test="${r.active}">
                        <form method="post" action="${pageContext.request.contextPath}/app/admin/business-roles/${r.id}/deactivate">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <button type="submit" class="secondary">Deactivate</button>
                        </form>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>

    <div class="panel">
        <h2>Create Business Role</h2>
        <form method="post" action="${pageContext.request.contextPath}/app/admin/business-roles">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <label>Role Name * <input type="text" name="roleName" required></label>
            <label>Access Class
                <select name="accessClass">
                    <c:forEach var="ac" items="${accessClasses}"><option value="${ac}">${ac}</option></c:forEach>
                </select>
            </label>
            <p class="note-box">A Role name never grants a permission. Ordinary roles map to EMPLOYEE.
                Deactivate (never delete) referenced roles.</p>
            <button type="submit">Create</button>
        </form>
    </div>
</main>
</body>
</html>
