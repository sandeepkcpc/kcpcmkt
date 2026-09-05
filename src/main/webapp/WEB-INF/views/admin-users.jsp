<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Users</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
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
                <tr><th>Name</th><th>Email</th><th>Business Role</th><th>Access Class</th><th>Status</th><th>Actions</th></tr>
            </thead>
            <tbody>
            <c:forEach var="u" items="${users}">
                <tr data-user-row="${u.id}">
                    <td><a class="admin-user-name-link" href="${pageContext.request.contextPath}/app/admin/users/${u.id}">${u.fullName}</a></td>
                    <td class="admin-email-cell">${u.email}</td>
                    <td class="admin-role-cell">${u.businessRole.roleName}</td>
                    <td class="admin-access-class">${u.businessRole.accessClassCode}</td>
                    <td class="admin-status-cell">
                        <c:choose>
                            <c:when test="${u.active}"><span class="status-pill status-active">Active</span></c:when>
                            <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                        </c:choose>
                    </td>
                    <td class="admin-actions-cell">
                        <button type="button" class="script-description-icon-btn admin-edit-user-btn"
                                title="Edit User" aria-label="Edit User for ${fn:escapeXml(u.fullName)}"
                                data-user-id="${u.id}" data-full-name="${fn:escapeXml(u.fullName)}"
                                data-email="${fn:escapeXml(u.email)}" data-business-role-id="${u.businessRole.id}"
                                data-active="${u.active}">&#9998;</button>
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

<%-- Edit User modal: one shared dialog, populated per-row from the clicked edit icon's data-*
     attributes (already server-rendered above - no extra fetch needed to open it). Access Class is
     never an independent field here - it always mirrors the selected Business Role's own
     accessClassCode (BRS-REQ-001/002), read-only, exactly like admin-user-detail.jsp's own
     Business Role panel already documents. --%>
<div class="kcpc-modal-overlay hidden" id="editUserModalOverlay">
    <div class="kcpc-modal" role="dialog" aria-modal="true" aria-labelledby="editUserModalTitle">
        <div class="kcpc-modal-header">
            <h3 id="editUserModalTitle">Edit User</h3>
            <button type="button" class="kcpc-modal-close" id="editUserModalClose" aria-label="Close">&times;</button>
        </div>
        <div class="kcpc-modal-body">
            <p class="muted" id="editUserTargetLabel"></p>
            <form id="editUserForm" novalidate>
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <div class="admin-edit-user-grid">
                    <label>Full Name *
                        <input type="text" id="editUserFullName" name="fullName" required maxlength="100">
                    </label>
                    <label>Email *
                        <input type="email" id="editUserEmail" name="email" required maxlength="255">
                    </label>
                    <label>Business Role *
                        <select id="editUserBusinessRoleId" name="businessRoleId" required>
                            <c:forEach var="r" items="${businessRoles}">
                                <option value="${r.id}" data-access-class="${r.accessClassCode}">${r.roleName}</option>
                            </c:forEach>
                        </select>
                    </label>
                    <label>Access Class
                        <input type="text" id="editUserAccessClass" disabled readonly>
                    </label>
                    <label>Status *
                        <select id="editUserActive" name="active" required>
                            <option value="true">Active</option>
                            <option value="false">Deactivated</option>
                        </select>
                    </label>
                    <label class="admin-edit-user-reason">Reason *
                        <input type="text" id="editUserReason" name="reason" required
                               placeholder="Why are you making this change?">
                    </label>
                </div>
                <div class="reviews-decision-error hidden" id="editUserError"></div>
            </form>
        </div>
        <div class="kcpc-modal-footer">
            <button type="button" class="btn-outline" id="editUserCancelBtn">Cancel</button>
            <button type="submit" form="editUserForm" id="editUserSaveBtn">Save Changes</button>
        </div>
    </div>
</div>

<script src="<c:url value='/js/admin-shared.js'/>" defer></script>
<script src="<c:url value='/js/admin-edit-user-modal.js'/>" defer></script>
</body>
</html>
