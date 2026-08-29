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
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>
    <%-- Admin/CEO Password Reset: the raw temporary password is a flash attribute - present on this
         one redirect only, never persisted/logged/shown again after this page load. --%>
    <c:if test="${not empty temporaryPassword}">
        <div class="alert-success admin-temp-password-box">
            <div class="admin-temp-password-label">Temporary Password (shown once - copy and share with the employee now)</div>
            <div class="admin-temp-password-row">
                <code id="adminTempPasswordValue" class="admin-temp-password-value">${temporaryPassword}</code>
                <button type="button" class="btn-outline" id="adminTempPasswordCopyBtn" data-copy-target="adminTempPasswordValue">Copy</button>
            </div>
            <p class="muted">The employee will be required to set their own password on next login.</p>
        </div>
    </c:if>

    <div class="admin-userdetail-top">
        <div class="panel admin-userinfo-card">
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

            <div class="admin-userinfo-fields">
                <div class="admin-userinfo-field">
                    <div class="admin-userdetail-field-label">Business Role</div>
                    <div class="admin-userdetail-field-value">${targetUser.businessRole.roleName}</div>
                </div>
                <div class="admin-userinfo-field">
                    <div class="admin-userdetail-field-label">Access Class</div>
                    <div class="admin-userdetail-field-value">${targetUser.businessRole.accessClassCode} <span class="muted">(read-only)</span></div>
                </div>
                <div class="admin-userinfo-field">
                    <div class="admin-userdetail-field-label">Account Status</div>
                    <div class="admin-userdetail-field-value">${targetUser.active ? 'Active' : 'Deactivated'}</div>
                </div>
                <div class="admin-userinfo-field">
                    <div class="admin-userdetail-field-label">Account Created</div>
                    <div class="admin-userdetail-field-value">${kcpc:istDate(targetUser.createdAt)}</div>
                </div>
            </div>

            <div class="admin-userdetail-actions">
                <details class="admin-action-toggle">
                    <summary class="admin-action-summary admin-action-warn">&#128260; Change Business Role</summary>
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

                <details class="admin-action-toggle">
                    <summary class="admin-action-summary admin-action-warn">&#128273; Reset Password</summary>
                    <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/reset-password">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <p class="muted">Generates a temporary password for this employee and signs them out of every
                            active session. They will be required to set their own password on next login.</p>
                        <label>Reason * <input type="text" name="reason" required placeholder="e.g. Employee requested reset"></label>
                        <div class="btn-row"><button type="submit">Generate Temporary Password</button></div>
                    </form>
                </details>

                <details class="admin-action-toggle">
                    <c:choose>
                        <c:when test="${targetUser.active}">
                            <summary class="admin-action-summary admin-action-danger">&#128681; Deactivate User</summary>
                            <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/deactivate">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <label>Reason * <input type="text" name="reason" required></label>
                                <div class="btn-row"><button type="submit" class="btn-outline-danger" style="width:auto;">Deactivate</button></div>
                            </form>
                        </c:when>
                        <c:otherwise>
                            <summary class="admin-action-summary admin-action-success">&#9989; Activate User</summary>
                            <form method="post" action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/activate">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <label>Reason * <input type="text" name="reason" required></label>
                                <div class="btn-row"><button type="submit" class="btn-outline-success" style="width:auto;">Activate</button></div>
                            </form>
                        </c:otherwise>
                    </c:choose>
                </details>
            </div>
        </div>

        <div class="panel" id="permissionSummaryCard">
            <h2><span class="panel-icon panel-icon-blue">&#128193;</span> Permission Summary</h2>
            <div class="perm-summary-stats">
                <div class="perm-summary-stat">
                    <span class="perm-summary-icon perm-icon-total">&#128203;</span>
                    <span class="perm-summary-count">${permissionSummary.total}</span>
                    <span class="perm-summary-label">Total Permissions</span>
                </div>
                <div class="perm-summary-stat">
                    <span class="perm-summary-icon perm-icon-granted">&#10003;</span>
                    <span class="perm-summary-count">${permissionSummary.granted}</span>
                    <span class="perm-summary-label">Granted</span>
                </div>
                <div class="perm-summary-stat">
                    <span class="perm-summary-icon perm-icon-restricted">&#9888;</span>
                    <span class="perm-summary-count">${permissionSummary.restricted}</span>
                    <span class="perm-summary-label">Restricted</span>
                </div>
                <div class="perm-summary-stat">
                    <span class="perm-summary-icon perm-icon-notgranted">&#10007;</span>
                    <span class="perm-summary-count">${permissionSummary.notGranted}</span>
                    <span class="perm-summary-label">Not Granted</span>
                </div>
            </div>
            <p class="perm-summary-note">Restricted permissions are a subset of Granted.</p>
        </div>
    </div>

    <div class="panel" id="permChecklistPanel">
        <div class="perm-checklist-head">
            <div>
                <h2><span class="panel-icon panel-icon-blue">&#128737;</span> Permission Management <span class="info-icon" title="Grant or revoke operational permissions for this user. Business Role and Access Class are never changed here.">&#9432;</span></h2>
                <p class="perm-checklist-lead">Check a permission to grant it with GLOBAL scope by default. Uncheck to revoke.</p>
            </div>
            <div class="perm-checklist-toolbar">
                <div class="perm-checklist-search">
                    <span class="admin-search-icon">&#128269;</span>
                    <input type="search" id="permChecklistSearch" placeholder="Search permissions...">
                </div>
                <label class="perm-checklist-filter">
                    <input type="checkbox" id="permChecklistGrantedOnly"> Show granted only
                </label>
            </div>
        </div>
        <div class="reports-table-scroll">
        <table class="data-table admin-table perm-mgmt-table">
            <thead><tr><th>Grant</th><th>Permission</th>
                <th>Scope <span class="info-icon" title="Global applies everywhere. Stage Restricted limits to selected lifecycle stages. Item Specific limits to one content item.">&#9432;</span></th>
                <th>Stage(s) <span class="info-icon" title="Only used when Scope is Stage Restricted.">&#9432;</span></th>
                <th>Effective From <span class="info-icon" title="When this grant became active. Set automatically on grant.">&#9432;</span></th>
                <th>Expires <span class="info-icon" title="Leave blank for no expiry.">&#9432;</span></th>
                <th>Reason <span class="info-icon" title="Optional note for this grant. Defaults to N/A.">&#9432;</span></th>
                <th>Status</th><th>Action</th></tr></thead>
            <tbody>
                    <c:forEach var="row" items="${managementRows}">
                        <c:set var="formId" value="perm-update-form-${row.permission}"/>
                        <tr class="perm-row" data-granted="${row.granted}" data-permission="${row.permission}"
                            data-search="${fn:toLowerCase(row.displayName)} ${fn:toLowerCase(row.permission)} ${fn:toLowerCase(row.description)}">
                            <td>
                                <c:choose>
                                    <c:when test="${row.multi}">
                                        <input type="checkbox" class="perm-checkbox perm-checkbox-multi" checked disabled
                                               title="${fn:length(row.activeGrants)} active grants - manage individually below">
                                    </c:when>
                                    <c:when test="${row.granted}">
                                        <form class="perm-toggle-form" method="post"
                                              action="${pageContext.request.contextPath}/app/admin/permission-grants/${row.grantId}/revoke">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <input type="hidden" name="userId" value="${targetUser.id}"/>
                                            <input type="hidden" name="reason" value="N/A"/>
                                            <input type="checkbox" class="perm-checkbox" checked>
                                        </form>
                                    </c:when>
                                    <c:otherwise>
                                        <form class="perm-toggle-form" method="post"
                                              action="${pageContext.request.contextPath}/app/admin/users/${targetUser.id}/permission-grants/quick">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <input type="hidden" name="permission" value="${row.permission}"/>
                                            <input type="checkbox" class="perm-checkbox">
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td class="perm-name-cell">
                                <button type="button" class="perm-name-link"
                                        data-display-name="${row.displayName}" data-code="${row.permission}"
                                        data-description="${fn:escapeXml(row.description)}">${row.displayName}</button>
                                <div class="perm-code-muted">${row.permission}</div>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${row.multi}"><span class="perm-dash">Multiple</span></c:when>
                                    <c:otherwise>
                                        <select class="perm-inline-select perm-scope-select" name="scopeType" form="${formId}"
                                                data-original="${empty row.scopeType ? 'GLOBAL' : row.scopeType}"
                                                ${row.granted ? '' : 'disabled'}>
                                            <option value="GLOBAL" ${(empty row.scopeType or row.scopeType == 'GLOBAL') ? 'selected' : ''}>Global</option>
                                            <option value="STAGE_RESTRICTED" ${row.scopeType == 'STAGE_RESTRICTED' ? 'selected' : ''}>Stage Restricted</option>
                                            <option value="ITEM_SPECIFIC" ${row.scopeType == 'ITEM_SPECIFIC' ? 'selected' : ''}>Item Specific</option>
                                        </select>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td class="perm-stage-cell">
                                <c:choose>
                                    <c:when test="${row.multi}"><span class="perm-dash">-</span></c:when>
                                    <c:otherwise>
                                        <span class="perm-dash perm-stage-dash"
                                              style="${(row.granted and row.scopeType == 'STAGE_RESTRICTED') or (row.granted and row.scopeType == 'ITEM_SPECIFIC') ? 'display:none;' : ''}">-</span>
                                        <select class="perm-inline-select perm-stage-select" name="stages" form="${formId}" multiple size="4"
                                                style="${(row.granted and row.scopeType == 'STAGE_RESTRICTED') ? '' : 'display:none;'}"
                                                ${(row.granted and row.scopeType == 'STAGE_RESTRICTED') ? '' : 'disabled'}>
                                            <c:forEach var="st" items="${stages}">
                                                <option value="${st}" ${row.stages.contains(st) ? 'selected' : ''}>${st}</option>
                                            </c:forEach>
                                        </select>
                                        <input type="text" class="perm-inline-input perm-item-input" name="itemContentId" form="${formId}"
                                               placeholder="Content ID" data-original=""
                                               style="${(row.granted and row.scopeType == 'ITEM_SPECIFIC') ? '' : 'display:none;'}"
                                               ${(row.granted and row.scopeType == 'ITEM_SPECIFIC') ? '' : 'disabled'}>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${row.multi}"><span class="perm-dash">Multiple</span></c:when>
                                    <c:when test="${row.granted}">${kcpc:istDate(row.effectiveFrom)}</c:when>
                                    <c:otherwise><span class="perm-dash">-</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${row.multi}"><span class="perm-dash">-</span></c:when>
                                    <c:otherwise>
                                        <input type="date" class="perm-inline-input perm-expiry-input" name="effectiveUntil" form="${formId}"
                                               value="${empty row.effectiveUntil ? '' : kcpc:isoDate(row.effectiveUntil)}"
                                               data-original="${empty row.effectiveUntil ? '' : kcpc:isoDate(row.effectiveUntil)}"
                                               ${row.granted ? '' : 'disabled'}>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${row.multi}"><span class="perm-dash">-</span></c:when>
                                    <c:otherwise>
                                        <input type="text" class="perm-inline-input perm-reason-input" name="reason" form="${formId}"
                                               value="${row.reason}" data-original="${row.reason}" ${row.granted ? '' : 'disabled'}>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td><span class="perm-status-badge ${row.statusClass}">${row.statusLabel}</span></td>
                            <td>
                                <c:choose>
                                    <c:when test="${row.multi}">
                                        <button type="button" class="perm-expand-btn" data-target="multi-row-${row.permission}">Manage (${fn:length(row.activeGrants)})</button>
                                    </c:when>
                                    <c:when test="${row.granted}">
                                        <form id="${formId}" class="perm-update-form" method="post"
                                              action="${pageContext.request.contextPath}/app/admin/permission-grants/${row.grantId}/update">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <input type="hidden" name="userId" value="${targetUser.id}"/>
                                            <button type="submit" class="perm-update-btn" disabled>Update</button>
                                        </form>
                                        <span class="perm-update-saved" style="display:none;">Updated</span>
                                    </c:when>
                                    <c:otherwise>
                                        <button type="button" class="perm-update-btn" disabled>Update</button>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                        <c:if test="${row.multi}">
                            <tr class="perm-multi-subrow" id="multi-row-${row.permission}" style="display:none;">
                                <td></td>
                                <td colspan="8">
                                    <p class="perm-multi-note">This user has ${fn:length(row.activeGrants)} simultaneously active
                                        grants for this permission. Revoke individually below - none are merged or hidden.</p>
                                    <table class="perm-multi-detail-table">
                                        <thead><tr><th>Scope</th><th>Stage/Item</th><th>Effective From</th><th>Expires</th>
                                            <th>Reason</th><th>Action</th></tr></thead>
                                        <tbody>
                                        <c:forEach var="g" items="${row.activeGrants}">
                                            <tr>
                                                <td>${g.scopeType}</td>
                                                <td>${g.stageLabel}</td>
                                                <td>${kcpc:istDate(g.effectiveFrom)}</td>
                                                <td>${empty g.effectiveUntil ? '-' : kcpc:istDate(g.effectiveUntil)}</td>
                                                <td><c:out value="${g.reason}"/></td>
                                                <td>
                                                    <form class="perm-toggle-form" method="post"
                                                          action="${pageContext.request.contextPath}/app/admin/permission-grants/${g.grantId}/revoke">
                                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                                        <input type="hidden" name="userId" value="${targetUser.id}"/>
                                                        <input type="hidden" name="reason" value="N/A"/>
                                                        <button type="submit" class="btn-outline-danger" style="width:auto;">Revoke</button>
                                                    </form>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                        </tbody>
                                    </table>
                                </td>
                            </tr>
                        </c:if>
                    </c:forEach>
                    </tbody>
                </table>
                </div>
                <p class="perm-checklist-empty" id="permChecklistEmpty" style="display:none;">No permissions match your search.</p>
                <div class="perm-mgmt-footer">
                    <span id="permMgmtShowingText">${fn:length(managementRows)} permissions</span>
                </div>
                <p class="note-box">Checking a permission grants it immediately (Scope = Global, Effective From = now,
                    Expires = none, Reason = N/A). Unchecking revokes it immediately - both are fully audited.</p>
    </div>
</main>

<dialog id="permDetailDialog">
    <button type="button" class="perm-detail-close" aria-label="Close">&#10005;</button>
    <div class="perm-detail-body">
        <p class="perm-detail-name" id="permDetailName"></p>
        <p class="perm-detail-code" id="permDetailCode"></p>
        <p class="perm-detail-section-title">What this permission allows</p>
        <p class="perm-detail-text" id="permDetailDescription"></p>
        <p class="perm-detail-section-title">Important</p>
        <p class="perm-detail-text">Granting or revoking an operational permission never changes this user's Business
            Role, Access Class, or organizational designation - it only changes their operational capability.</p>
    </div>
</dialog>

<script src="${pageContext.request.contextPath}/js/admin-shared.js" defer></script>
<script src="${pageContext.request.contextPath}/js/permission-checklist.js" defer></script>
</body>
</html>
