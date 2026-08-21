<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — ${targetUser.fullName}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>${targetUser.fullName}</h1>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <h2>Profile</h2>
        <p><strong>Full Name:</strong> ${targetUser.fullName} <span class="muted">(read-only)</span></p>
        <p><strong>Email:</strong> ${targetUser.email} <span class="muted">(read-only)</span></p>
        <p><strong>Access Class (resolved from Business Role):</strong> ${targetUser.businessRole.accessClassCode}
            <span class="muted">(read-only)</span></p>

        <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/business-role">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <label>Business Role
                <select name="businessRoleId">
                    <c:forEach var="r" items="${businessRoles}">
                        <option value="${r.id}" ${r.id == targetUser.businessRole.id ? 'selected' : ''}>${r.roleName}</option>
                    </c:forEach>
                </select>
            </label>
            <label>Reason * <input type="text" name="reason" required></label>
            <button type="submit">Save Business Role</button>
        </form>

        <c:choose>
            <c:when test="${targetUser.active}">
                <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/deactivate">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Reason * <input type="text" name="reason" required></label>
                    <button type="submit" class="secondary">Deactivate</button>
                </form>
            </c:when>
            <c:otherwise>
                <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/activate">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Reason * <input type="text" name="reason" required></label>
                    <button type="submit">Activate</button>
                </form>
            </c:otherwise>
        </c:choose>
        <p class="note-box">Every status/role change is audit-logged with the reason.</p>
    </div>

    <div class="panel">
        <h2>Granted Operational Permissions</h2>
        <table class="data-table">
            <thead><tr><th>#</th><th>Permission</th><th>Scope</th><th>Valid (IST)</th><th>Active</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="g" items="${grants}">
                <tr>
                    <td>${g.permission.number()}</td>
                    <td>${g.permission}</td>
                    <td>${g.scopeType}</td>
                    <td>${kcpc:ist(g.effectiveFrom)} &ndash; ${empty g.effectiveUntil ? '(none)' : kcpc:ist(g.effectiveUntil)}</td>
                    <td>${g.active and empty g.revokedAt ? 'Active' : 'Revoked/Expired'}</td>
                    <td>
                        <c:if test="${g.active and empty g.revokedAt}">
                            <details>
                                <summary>Modify</summary>
                                <form class="action-form" method="post"
                                      action="${pageContext.request.contextPath}/app/admin/permission-grants/${g.id}/modify">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <input type="hidden" name="userId" value="${targetUser.id}"/>
                                    <label>New Expiry <input type="date" name="newEffectiveUntil"></label>
                                    <label>Reason * <input type="text" name="reason" required></label>
                                    <button type="submit">Save</button>
                                </form>
                            </details>
                            <form class="action-form" method="post"
                                  action="${pageContext.request.contextPath}/app/admin/permission-grants/${g.id}/revoke">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <input type="hidden" name="userId" value="${targetUser.id}"/>
                                <label>Revoke Reason * <input type="text" name="reason" required></label>
                                <button type="submit" class="secondary">Revoke</button>
                            </form>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty grants}"><tr><td colspan="6" class="muted">No permission grants.</td></tr></c:if>
            </tbody>
        </table>

        <h3>Grant Permission</h3>
        <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/permission-grants">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <label>Permission
                <select name="permission">
                    <c:forEach var="p" items="${permissions}"><option value="${p}">#${p.number()} ${p}</option></c:forEach>
                </select>
            </label>
            <label>Scope
                <select name="scopeType">
                    <c:forEach var="s" items="${scopeTypes}"><option value="${s}">${s}</option></c:forEach>
                </select>
            </label>
            <label>Stages (Stage-Restricted only)
                <select name="stages" multiple size="4">
                    <c:forEach var="st" items="${stages}"><option value="${st}">${st}</option></c:forEach>
                </select>
            </label>
            <div class="field-row">
                <div><label>Effective From <input type="date" name="effectiveFrom"></label></div>
                <div><label>Expires <input type="date" name="effectiveUntil"></label></div>
            </div>
            <label>Grant Reason * <input type="text" name="reason" required></label>
            <button type="submit">Grant</button>
        </form>
    </div>
</main>
</body>
</html>
