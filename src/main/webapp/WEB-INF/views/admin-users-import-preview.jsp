<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Import Users ▸ Preview</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <link rel="icon" type="image/x-icon" href="${pageContext.request.contextPath}/images/favicon.ico">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="admin-users-header-row">
        <h1>Import Users — Preview</h1>
        <a class="btn-outline" href="${pageContext.request.contextPath}/app/admin/users/import">Upload a Different File</a>
    </div>

    <c:choose>
        <c:when test="${result.hasFileLevelErrors}">
            <div class="panel">
                <h2>File could not be read</h2>
                <c:forEach var="err" items="${result.fileLevelErrors}">
                    <div class="alert-error">${err}</div>
                </c:forEach>
                <p class="note-box">Fix the file and upload again. Nothing has been imported.</p>
            </div>
        </c:when>
        <c:otherwise>
            <div class="panel">
                <h2>Summary — ${result.filename}</h2>
                <div class="admin-inline-form">
                    <div><strong>Total Rows</strong><br>${result.totalRows}</div>
                    <div><strong>Valid Rows</strong><br>${result.validCount}</div>
                    <div><strong>Invalid Rows</strong><br>${result.invalidCount}</div>
                    <div><strong>Duplicate in File</strong><br>${result.duplicateInFileCount}</div>
                    <div><strong>Existing Users (Duplicate)</strong><br>${result.duplicateInDbCount}</div>
                </div>
                <p class="note-box">
                    New valid users will be imported. Rows matching an existing account email are
                    skipped automatically — existing users are never overwritten. Invalid rows are
                    rejected and must be fixed in the source file before re-upload.
                </p>
            </div>

            <c:if test="${result.invalidCount > 0}">
                <div class="panel">
                    <h2>Row Errors</h2>
                    <c:forEach var="row" items="${result.rows}">
                        <c:if test="${not row.valid}">
                            <c:forEach var="err" items="${row.errors}">
                                <div class="alert-error">Row ${row.rowNumber} — ${err}</div>
                            </c:forEach>
                        </c:if>
                    </c:forEach>
                </div>
            </c:if>

            <div class="panel">
                <h2>Row Detail</h2>
                <table class="data-table admin-table">
                    <thead>
                        <tr>
                            <th>Row</th><th>Full Name</th><th>Email</th><th>Business Role</th>
                            <th>Access Class</th><th>Account Status</th><th>Result</th>
                        </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="row" items="${result.rows}">
                        <tr>
                            <td>${row.rowNumber}</td>
                            <td>${row.fullName}</td>
                            <td class="admin-email-cell">${row.email}</td>
                            <td>${row.businessRoleName}</td>
                            <td class="admin-access-class">${row.resolvedAccessClass}</td>
                            <td>${row.resolvedActive ? 'ACTIVE' : 'INACTIVE'}</td>
                            <td>
                                <c:choose>
                                    <c:when test="${row.valid}"><span class="status-pill status-active">New — will import</span></c:when>
                                    <c:when test="${row.duplicateInDb}"><span class="status-pill status-neutral">Existing — will skip</span></c:when>
                                    <c:otherwise><span class="status-pill status-rejected">Invalid — will reject</span></c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>

            <c:if test="${result.validCount > 0}">
                <div class="panel">
                    <h2>Step 2 — Confirm Import</h2>
                    <p class="note-box">
                        This will create ${result.validCount} new user(s). Each new user receives a
                        randomly generated password shown once on the next screen — no email is sent
                        by this app. This is re-validated at the moment of import, so anything that
                        changed since this preview (e.g. another admin creating a conflicting email)
                        will be caught and safely skipped rather than causing a duplicate.
                    </p>
                    <form method="post" action="${pageContext.request.contextPath}/app/admin/users/import/confirm">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <div class="btn-row"><button type="submit">Import ${result.validCount} User(s)</button></div>
                    </form>
                </div>
            </c:if>
        </c:otherwise>
    </c:choose>
</main>
<script src="${pageContext.request.contextPath}/js/admin-shared.js" defer></script>
</body>
</html>
