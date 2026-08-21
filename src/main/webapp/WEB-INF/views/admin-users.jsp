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
<main class="app-main">
    <h1>Administration &raquo; Users</h1>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <table class="data-table">
        <thead><tr><th>Name</th><th>Email</th><th>Business Role</th><th>Access Class</th><th>Status</th></tr></thead>
        <tbody>
        <c:forEach var="u" items="${users}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/app/admin/users/${u.id}">${u.fullName}</a></td>
                <td>${u.email}</td>
                <td>${u.businessRole.roleName}</td>
                <td>${u.businessRole.accessClassCode}</td>
                <td>${u.active ? 'Active' : 'Deactivated'}</td>
            </tr>
        </c:forEach>
        </tbody>
    </table>

    <div class="panel">
        <h2>Create User</h2>
        <form method="post" action="${pageContext.request.contextPath}/app/admin/users">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <label>Full Name * <input type="text" name="fullName" required></label>
            <label>Email * <input type="email" name="email" required></label>
            <label>Initial Password * <input type="password" name="password" required></label>
            <label>Business Role *
                <select name="businessRoleId" required>
                    <c:forEach var="r" items="${businessRoles}">
                        <option value="${r.id}">${r.roleName} (${r.accessClassCode})</option>
                    </c:forEach>
                </select>
            </label>
            <label>Reason * <input type="text" name="creationReason" required></label>
            <p class="note-box">Reason is mandatory; Create is blocked without it.</p>
            <button type="submit">Create User</button>
        </form>
    </div>
</main>
</body>
</html>
