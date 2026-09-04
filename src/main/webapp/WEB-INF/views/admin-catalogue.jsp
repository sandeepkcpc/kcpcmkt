<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Publishing Catalogue</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <link rel="icon" type="image/x-icon" href="${pageContext.request.contextPath}/images/favicon.ico">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="page-header-row">
        <div>
            <h1>Publishing Catalogue</h1>
        </div>
        <span class="muted" style="font-size:0.82rem;white-space:nowrap;">&#8505; All times are shown in IST</span>
    </div>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="catalogue-master-row">
        <div class="panel platforms-card">
            <div class="card-head-row">
                <h2>Platforms</h2>
                <button type="button" class="btn-outline" data-toggle-panel="createPlatformPanel">+ Create Platform</button>
            </div>
            <div class="catalogue-table-scroll">
                <table class="data-table admin-table">
                    <thead><tr><th>Platform</th><th>Active</th><th></th></tr></thead>
                    <tbody>
                    <c:forEach var="p" items="${platforms}">
                        <c:set var="platformIconFor" value="${p.platformName}"/>
                        <%@ include file="fragments/platform-icon-src.jspf" %>
                        <tr>
                            <td>
                                <div class="catalogue-platform-cell">
                                    <img class="catalogue-platform-icon" src="${platformIconSrc}" alt="" width="18" height="18"/>
                                    ${p.platformName}
                                </div>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${p.active}"><span class="status-pill status-active">Active</span></c:when>
                                    <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <details>
                                    <summary class="icon-btn">&#9998;</summary>
                                    <div class="catalogue-edit-panel">
                                        <form class="action-form" method="post"
                                              action="${pageContext.request.contextPath}/app/admin/catalogue/platforms/${p.id}">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <label>Rename <input type="text" name="platformName" placeholder="${p.platformName}"></label>
                                            <label class="checkbox-inline" style="display:flex;align-items:center;gap:0.4rem;">
                                                <input type="checkbox" name="isActive" value="true" style="width:auto;margin-top:0;" ${p.active ? 'checked' : ''}> Active
                                            </label>
                                            <label>Reason * <input type="text" name="catalogueReason" required></label>
                                            <div class="btn-row"><button type="submit">Save</button></div>
                                        </form>
                                    </div>
                                </details>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
            <div class="catalogue-create-body hidden" id="createPlatformPanel">
                <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/admin/catalogue/platforms">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Platform Name * <input type="text" name="platformName" placeholder="Enter platform name" required></label>
                    <label>Reason * <input type="text" name="catalogueReason" placeholder="Enter reason" required></label>
                    <div class="btn-row"><button type="submit">Create Platform</button></div>
                </form>
            </div>
        </div>

        <div class="panel channels-card">
            <div class="card-head-row">
                <h2>Channels (Accounts)</h2>
                <button type="button" class="btn-outline" data-toggle-panel="createChannelPanel">+ Create Channel</button>
            </div>
            <div class="catalogue-table-scroll">
                <table class="data-table admin-table">
                    <thead><tr><th>Channel Handle</th><th>Active</th><th></th></tr></thead>
                    <tbody>
                    <c:forEach var="c" items="${channels}">
                        <tr>
                            <td>${c.channelHandle}</td>
                            <td>
                                <c:choose>
                                    <c:when test="${c.active}"><span class="status-pill status-active">Active</span></c:when>
                                    <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <details>
                                    <summary class="icon-btn">&#9998;</summary>
                                    <div class="catalogue-edit-panel">
                                        <form class="action-form" method="post"
                                              action="${pageContext.request.contextPath}/app/admin/catalogue/channels/${c.id}">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                            <label>Rename <input type="text" name="channelHandle" placeholder="${c.channelHandle}"></label>
                                            <label class="checkbox-inline" style="display:flex;align-items:center;gap:0.4rem;">
                                                <input type="checkbox" name="isActive" value="true" style="width:auto;margin-top:0;" ${c.active ? 'checked' : ''}> Active
                                            </label>
                                            <label>Reason * <input type="text" name="catalogueReason" required></label>
                                            <div class="btn-row"><button type="submit">Save</button></div>
                                        </form>
                                    </div>
                                </details>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
            <div class="catalogue-create-body hidden" id="createChannelPanel">
                <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/admin/catalogue/channels">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <label>Channel Handle * <input type="text" name="channelHandle" placeholder="Enter channel handle" required></label>
                    <label>Reason * <input type="text" name="catalogueReason" placeholder="Enter reason" required></label>
                    <div class="btn-row"><button type="submit">Create Channel</button></div>
                </form>
            </div>
        </div>
    </div>

    <div class="panel publication-targets-card">
        <div class="card-head-row">
            <h2>Publication Targets (Platform &times; Channel)</h2>
            <button type="button" class="btn-outline" data-toggle-panel="createTargetPanel">+ Create Target</button>
        </div>
        <table class="data-table admin-table">
                <thead><tr><th>Platform</th><th>Channel</th><th>Target Name</th><th>Active</th><th></th></tr></thead>
                <tbody>
                <c:forEach var="t" items="${targets}">
                    <c:set var="platformIconFor" value="${t.platform.platformName}"/>
                    <%@ include file="fragments/platform-icon-src.jspf" %>
                    <tr>
                        <td>
                            <div class="catalogue-platform-cell">
                                <img class="catalogue-platform-icon" src="${platformIconSrc}" alt="" width="18" height="18"/>
                                ${t.platform.platformName}
                            </div>
                        </td>
                        <td>${t.channel.channelHandle}</td>
                        <td>${t.targetName}</td>
                        <td>
                            <c:choose>
                                <c:when test="${t.active}"><span class="status-pill status-active">Active</span></c:when>
                                <c:otherwise><span class="status-pill status-inactive">Deactivated</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <details>
                                <summary class="icon-btn">&#9998;</summary>
                                <div class="catalogue-edit-panel">
                                    <form class="action-form" method="post"
                                          action="${pageContext.request.contextPath}/app/admin/catalogue/targets/${t.id}">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                        <input type="hidden" name="isActive" value="${!t.active}"/>
                                        <label>Reason * <input type="text" name="catalogueReason" required></label>
                                        <div class="btn-row">
                                            <button type="submit" class="${t.active ? 'btn-outline-danger' : 'btn-outline-success'}" style="width:auto;">
                                                ${t.active ? 'Deactivate' : 'Activate'}
                                            </button>
                                        </div>
                                    </form>
                                </div>
                            </details>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        <div class="catalogue-create-body hidden" id="createTargetPanel">
            <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/admin/catalogue/targets">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Platform
                    <select name="platformId">
                        <c:forEach var="p" items="${platforms}"><option value="${p.id}">${p.platformName}</option></c:forEach>
                    </select>
                </label>
                <label>Channel
                    <select name="channelId">
                        <c:forEach var="c" items="${channels}"><option value="${c.id}">${c.channelHandle}</option></c:forEach>
                    </select>
                </label>
                <label>Target Name * <input type="text" name="targetName" placeholder="Enter target name" required></label>
                <label>Reason * <input type="text" name="catalogueReason" placeholder="Enter reason" required></label>
                <div class="btn-row"><button type="submit">Create Target</button></div>
            </form>
        </div>
    </div>

    <p class="note-box" style="margin-top:1.25rem;">Deactivating a platform, channel, or target will prevent it from being used for new publications.</p>
</main>
<script src="${pageContext.request.contextPath}/js/admin-shared.js" defer></script>
</body>
</html>
