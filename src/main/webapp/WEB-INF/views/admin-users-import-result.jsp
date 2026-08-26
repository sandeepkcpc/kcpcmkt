<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Import Users ▸ Result</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="admin-users-header-row">
        <h1>Import Users — Result</h1>
        <a class="btn-outline" href="${pageContext.request.contextPath}/app/admin/users">Back to Users</a>
    </div>

    <div class="panel">
        <h2>Summary — ${result.filename}</h2>
        <div class="admin-inline-form">
            <div><strong>Total Rows</strong><br>${result.totalRows}</div>
            <div><strong>Imported</strong><br>${result.importedCount}</div>
            <div><strong>Skipped Existing</strong><br>${result.skippedExistingCount}</div>
            <div><strong>Failed</strong><br>${result.failedCount}</div>
            <div><strong>Rejected (Invalid)</strong><br>${result.invalidCount}</div>
        </div>
    </div>

    <c:if test="${result.importedCount > 0}">
        <div class="panel">
            <h2>Generated Passwords — shown once</h2>
            <p class="note-box">
                These passwords are shown only on this screen and are never stored in plain text or
                logged anywhere. Copy them now and hand them to each employee out-of-band; this
                screen cannot be reopened.
            </p>
            <table class="data-table admin-table">
                <thead><tr><th>Full Name</th><th>Email</th><th>Initial Password</th></tr></thead>
                <tbody>
                <c:forEach var="row" items="${result.rows}">
                    <c:if test="${row.outcome == 'IMPORTED'}">
                        <tr>
                            <td>${row.fullName}</td>
                            <td class="admin-email-cell">${row.email}</td>
                            <td><code>${row.generatedPassword}</code></td>
                        </tr>
                    </c:if>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </c:if>

    <c:if test="${result.skippedExistingCount > 0 or result.failedCount > 0}">
        <div class="panel">
            <h2>Skipped / Failed Rows</h2>
            <table class="data-table admin-table">
                <thead><tr><th>Row</th><th>Full Name</th><th>Email</th><th>Outcome</th><th>Reason</th></tr></thead>
                <tbody>
                <c:forEach var="row" items="${result.rows}">
                    <c:if test="${row.outcome == 'SKIPPED_EXISTING' or row.outcome == 'FAILED'}">
                        <tr>
                            <td>${row.rowNumber}</td>
                            <td>${row.fullName}</td>
                            <td class="admin-email-cell">${row.email}</td>
                            <td>
                                <c:choose>
                                    <c:when test="${row.outcome == 'SKIPPED_EXISTING'}"><span class="status-pill status-neutral">Skipped — already exists</span></c:when>
                                    <c:otherwise><span class="status-pill status-rejected">Failed</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td>${row.failureReason}</td>
                        </tr>
                    </c:if>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </c:if>

    <c:if test="${result.invalidCount > 0}">
        <div class="panel">
            <h2>Rejected Rows</h2>
            <c:forEach var="row" items="${result.rows}">
                <c:if test="${not row.valid}">
                    <c:forEach var="err" items="${row.errors}">
                        <div class="alert-error">Row ${row.rowNumber} — ${err}</div>
                    </c:forEach>
                </c:if>
            </c:forEach>
        </div>
    </c:if>
</main>
<script src="${pageContext.request.contextPath}/js/admin-shared.js" defer></script>
</body>
</html>
