<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Notifications</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <div class="page-header-row">
        <div>
            <h1>Notifications</h1>
            <p class="muted">Every notification you have ever received, newest first. Reading one here (or from the
                header) marks it read - nothing is ever deleted just because it was read.</p>
        </div>
        <c:if test="${hasUnread}">
            <div class="page-header-row-actions">
                <form method="post" action="${pageContext.request.contextPath}/app/notifications/mark-all-read">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    <button type="submit" class="btn-outline">Mark all as read</button>
                </form>
            </div>
        </c:if>
    </div>

    <div class="panel">
        <c:if test="${empty notifications}">
            <p class="muted">No notifications yet.</p>
        </c:if>
        <c:forEach var="n" items="${notifications}">
            <%-- Plain EL concatenation, deliberately not <c:url> (see fragments/nav.jsp's own
                 identical choice - encodeURL() can inject ";jsessionid=..." and break byte-identical
                 response assumptions elsewhere; this app never uses URL-based session tracking). --%>
            <c:choose>
                <c:when test="${not empty n.targetTab}">
                    <c:set var="notifRowHref" value="${pageContext.request.contextPath}/app/deliverables/${n.contentPlan.id}?tab=${n.targetTab}"/>
                </c:when>
                <c:otherwise>
                    <c:set var="notifRowHref" value="${pageContext.request.contextPath}/app/deliverables/${n.contentPlan.id}"/>
                </c:otherwise>
            </c:choose>
            <div class="notification-row ${n.unread ? 'notification-row-unread' : ''}">
                <span class="notification-row-dot" aria-hidden="true"></span>
                <div class="notification-row-body">
                    <a class="notification-row-title" href="${notifRowHref}">
                        <c:out value="${n.title}"/>
                    </a>
                    <p class="notification-row-message"><c:out value="${n.message}"/></p>
                    <p class="muted notification-row-time">${kcpc:ist(n.createdAt)}</p>
                </div>
                <c:if test="${n.unread}">
                    <form method="post" action="${pageContext.request.contextPath}/app/notifications/${n.id}/read">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <button type="submit" class="notification-row-mark-read">Mark as read</button>
                    </form>
                </c:if>
            </div>
        </c:forEach>
    </div>
</main>
</body>
</html>
