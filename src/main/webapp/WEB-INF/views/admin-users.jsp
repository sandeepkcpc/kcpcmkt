<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Users</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="admin-users-header-row">
        <h1>Users</h1>
        <a class="btn-outline" href="${pageContext.request.contextPath}/app/admin/users/import">Import Users</a>
    </div>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <table class="data-table admin-table">
            <thead>
                <tr><th>Name</th><th>Email</th><th>Business Role</th><th>Access Class</th><th>Status</th></tr>
            </thead>
            <tbody>
            <c:forEach var="u" items="${users}">
                <tr>
                    <td><a href="${pageContext.request.contextPath}/app/admin/users/${u.id}">${u.fullName}</a></td>
                    <td class="admin-email-cell">${u.email}</td>
                    <td>${u.businessRole.roleName}</td>
                    <td class="admin-access-class">${u.businessRole.accessClassCode}</td>
                    <td>
                        <c:choose>
                            <c:when test="${u.active}"><span class="status-pill status-active">Active</span></c:when>
                            <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>

    <div class="panel">
        <h2>Create User</h2>
        <form method="post" action="${pageContext.request.contextPath}/app/admin/users">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <div class="admin-inline-form">
                <label>Full Name * <input type="text" name="fullName" required></label>
                <label>Email * <input type="email" name="email" required></label>
                <label>Initial Password *
                    <div class="password-field">
                        <input type="password" name="password" id="createUserPassword" required>
                        <button type="button" class="password-toggle" data-toggle-password="createUserPassword"
                                aria-label="Show password">&#128065;</button>
                    </div>
                </label>
                <label>Business Role *
                    <select name="businessRoleId" required>
                        <c:forEach var="r" items="${businessRoles}">
                            <option value="${r.id}">${r.roleName} (${r.accessClassCode})</option>
                        </c:forEach>
                    </select>
                </label>
                <label>Reason * <input type="text" name="creationReason" required></label>
            </div>
            <p class="note-box">Reason is mandatory; Create is blocked without it.</p>
            <div class="btn-row"><button type="submit">Create User</button></div>
        </form>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/admin-shared.js" defer></script>
</body>
</html>
