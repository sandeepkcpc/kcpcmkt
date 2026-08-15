<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Publishing Catalogue</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<header class="app-header">
    <span class="brand">KCPC Bandhani</span>
    <a class="header-link" href="${pageContext.request.contextPath}/app/pipeline">Pipeline</a>
</header>
<main class="app-main">
    <h1>Publishing Catalogue (Perm #17)</h1>
    <p class="muted">Two masters: Platforms &middot; Company Channels. A Publication Target is a Platform &times; Company Channel pairing.</p>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <h2>Platforms</h2>
        <table class="data-table">
            <thead><tr><th>Name</th><th>Active</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="p" items="${platforms}">
                <tr>
                    <td>${p.platformName}</td>
                    <td>${p.active ? 'Yes' : 'No'}</td>
                    <td>
                        <form class="action-form" method="post"
                              action="${pageContext.request.contextPath}/app/admin/catalogue/platforms/${p.id}">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Rename <input type="text" name="platformName" placeholder="${p.platformName}"></label>
                            <label>Active <input type="checkbox" name="isActive" value="true" style="width:auto" ${p.active ? 'checked' : ''}></label>
                            <label>Reason * <input type="text" name="catalogueReason" required></label>
                            <button type="submit">Save</button>
                        </form>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <details>
            <summary>+ Platform</summary>
            <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/admin/catalogue/platforms">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Platform Name * <input type="text" name="platformName" required></label>
                <label>Reason * <input type="text" name="catalogueReason" required></label>
                <button type="submit">Create Platform</button>
            </form>
        </details>
    </div>

    <div class="panel">
        <h2>Channels (Accounts)</h2>
        <table class="data-table">
            <thead><tr><th>Handle</th><th>Active</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="c" items="${channels}">
                <tr>
                    <td>${c.channelHandle}</td>
                    <td>${c.active ? 'Yes' : 'No'}</td>
                    <td>
                        <form class="action-form" method="post"
                              action="${pageContext.request.contextPath}/app/admin/catalogue/channels/${c.id}">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <label>Rename <input type="text" name="channelHandle" placeholder="${c.channelHandle}"></label>
                            <label>Active <input type="checkbox" name="isActive" value="true" style="width:auto" ${c.active ? 'checked' : ''}></label>
                            <label>Reason * <input type="text" name="catalogueReason" required></label>
                            <button type="submit">Save</button>
                        </form>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <details>
            <summary>+ Channel</summary>
            <form class="action-form" method="post" action="${pageContext.request.contextPath}/app/admin/catalogue/channels">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <label>Channel Handle * <input type="text" name="channelHandle" required></label>
                <label>Reason * <input type="text" name="catalogueReason" required></label>
                <button type="submit">Create Channel</button>
            </form>
        </details>
    </div>

    <div class="panel">
        <h2>Publication Targets (Platform &times; Channel)</h2>
        <table class="data-table">
            <thead><tr><th>Platform</th><th>Channel</th><th>Name</th><th>Active</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="t" items="${targets}">
                <tr>
                    <td>${t.platform.platformName}</td>
                    <td>${t.channel.channelHandle}</td>
                    <td>${t.targetName}</td>
                    <td>${t.active ? 'Yes' : 'No'}</td>
                    <td>
                        <form class="action-form" method="post"
                              action="${pageContext.request.contextPath}/app/admin/catalogue/targets/${t.id}">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <input type="hidden" name="isActive" value="${!t.active}"/>
                            <label>Reason * <input type="text" name="catalogueReason" required></label>
                            <button type="submit">${t.active ? 'Deactivate' : 'Activate'}</button>
                        </form>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <details>
            <summary>+ Target</summary>
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
                <label>Target Name * <input type="text" name="targetName" required></label>
                <label>Reason * <input type="text" name="catalogueReason" required></label>
                <button type="submit">Create Target</button>
            </form>
        </details>
    </div>
</main>
</body>
</html>
