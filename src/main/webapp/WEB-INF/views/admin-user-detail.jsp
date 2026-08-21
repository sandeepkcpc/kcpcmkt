<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
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
<main class="app-main app-main-wide">
    <p><a class="btn-outline" href="${pageContext.request.contextPath}/app/admin/users">&larr; Back to Users</a></p>
    <h1>${targetUser.fullName}</h1>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="admin-userdetail-grid">
        <div class="panel">
            <h2>User Detail</h2>
            <div class="admin-userdetail-identity">
                <span class="admin-avatar" data-fullname="${targetUser.fullName}"></span>
                <div>
                    <div class="admin-userdetail-name">${targetUser.fullName}
                        <c:choose>
                            <c:when test="${targetUser.active}"><span class="status-pill status-active">Active</span></c:when>
                            <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                        </c:choose>
                    </div>
                    <div class="admin-userdetail-email">${targetUser.email}</div>
                </div>
            </div>

            <div class="admin-userdetail-field">
                <div>
                    <div class="admin-userdetail-field-label">Business Role</div>
                    <div class="admin-userdetail-field-value">${targetUser.businessRole.roleName}</div>
                </div>
            </div>
            <div class="admin-userdetail-field">
                <div>
                    <div class="admin-userdetail-field-label">Access Class (resolved from Business Role)</div>
                    <div class="admin-userdetail-field-value">${targetUser.businessRole.accessClassCode} <span class="muted">(read-only)</span></div>
                </div>
            </div>
            <div class="admin-userdetail-field">
                <div>
                    <div class="admin-userdetail-field-label">Account Status</div>
                    <div class="admin-userdetail-field-value">${targetUser.active ? 'Active' : 'Deactivated'}</div>
                </div>
            </div>

            <details style="margin-top:1.25rem;">
                <summary style="cursor:pointer;font-weight:600;font-size:0.85rem;color:var(--kcpc-accent);">Change Business Role</summary>
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
                    <div class="btn-row"><button type="submit">Save Business Role</button></div>
                </form>
            </details>

            <details style="margin-top:0.75rem;">
                <c:choose>
                    <c:when test="${targetUser.active}">
                        <summary style="cursor:pointer;font-weight:600;font-size:0.85rem;color:#b91c1c;">Deactivate User</summary>
                        <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/deactivate">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Reason * <input type="text" name="reason" required></label>
                            <div class="btn-row"><button type="submit" class="btn-outline-danger" style="width:auto;">Deactivate</button></div>
                        </form>
                    </c:when>
                    <c:otherwise>
                        <summary style="cursor:pointer;font-weight:600;font-size:0.85rem;color:#166534;">Activate User</summary>
                        <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/activate">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Reason * <input type="text" name="reason" required></label>
                            <div class="btn-row"><button type="submit" class="btn-outline-success" style="width:auto;">Activate</button></div>
                        </form>
                    </c:otherwise>
                </c:choose>
            </details>
            <p class="note-box">Every status/role change is audit-logged with the reason.</p>
        </div>

        <div class="panel">
            <h2>Current Granted Permissions <span class="count-badge">${fn:length(grants)}</span></h2>
            <table class="data-table admin-table">
                <thead><tr><th>#</th><th>Permission</th><th>Scope</th><th>Valid (IST)</th><th>Active</th><th>Actions</th></tr></thead>
                <tbody>
                <c:forEach var="g" items="${grants}">
                    <tr>
                        <td>${g.permission.number()}</td>
                        <td>${g.permission}</td>
                        <td>${g.scopeType}</td>
                        <td>${kcpc:ist(g.effectiveFrom)} &ndash; ${empty g.effectiveUntil ? '-' : kcpc:ist(g.effectiveUntil)}</td>
                        <td>
                            <c:choose>
                                <c:when test="${g.active and empty g.revokedAt}"><span class="status-pill status-active">Active</span></c:when>
                                <c:otherwise><span class="status-pill status-inactive">Revoked/Expired</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:if test="${g.active and empty g.revokedAt}">
                                <details>
                                    <summary style="cursor:pointer;color:#1d4ed8;font-weight:600;font-size:0.82rem;">Modify</summary>
                                    <form class="action-form" method="post"
                                          action="${pageContext.request.contextPath}/app/admin/permission-grants/${g.id}/modify">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <input type="hidden" name="userId" value="${targetUser.id}"/>
                                        <label>New Expiry <input type="date" name="newEffectiveUntil"></label>
                                        <label>Reason * <input type="text" name="reason" required></label>
                                        <div class="btn-row"><button type="submit">Save</button></div>
                                    </form>
                                </details>
                                <form class="action-form" method="post"
                                      action="${pageContext.request.contextPath}/app/admin/permission-grants/${g.id}/revoke">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                    <input type="hidden" name="userId" value="${targetUser.id}"/>
                                    <label>Revoke Reason * <input type="text" name="reason" required></label>
                                    <div class="btn-row"><button type="submit" class="btn-outline-danger" style="width:auto;">Revoke</button></div>
                                </form>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty grants}"><tr><td colspan="6" class="muted">No permission grants.</td></tr></c:if>
                </tbody>
            </table>
        </div>

        <div class="panel">
            <h2>Grant Permission</h2>
            <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/permission-grants">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Permission *
                    <select name="permission">
                        <c:forEach var="p" items="${permissions}"><option value="${p}">#${p.number()} ${p}</option></c:forEach>
                    </select>
                </label>
                <label>Scope *
                    <select name="scopeType">
                        <c:forEach var="s" items="${scopeTypes}"><option value="${s}">${s}</option></c:forEach>
                    </select>
                </label>
                <label>Stages (Required for Stage Restricted scope)
                    <select name="stages" multiple size="4">
                        <c:forEach var="st" items="${stages}"><option value="${st}">${st}</option></c:forEach>
                    </select>
                </label>
                <div class="field-row">
                    <div><label>Effective From <input type="date" name="effectiveFrom"></label></div>
                    <div><label>Expires (Optional) <input type="date" name="effectiveUntil"></label></div>
                </div>
                <label>Grant Reason * <input type="text" name="reason" required></label>
                <div class="btn-row"><button type="submit">Grant Permission</button></div>
            </form>
        </div>
    </div>
</main>
<script src="${pageContext.request.contextPath}/js/admin-shared.js" defer></script>
</body>
</html>
