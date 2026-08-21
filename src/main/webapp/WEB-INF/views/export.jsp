<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Export</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide reports-page">
    <%@ include file="fragments/reports-tabs.jspf" %>

    <div class="page-header-row">
        <div>
            <h1>Export</h1>
            <p class="muted">Export platform data into your preferred format.</p>
        </div>
        <%@ include file="fragments/reports-asof-badge.jspf" %>
    </div>

    <%-- Plain browser GET navigation straight to the REST download endpoint (unchanged contract:
         format + repeated tables= params) - no CSRF token needed (GET is a safe method) and no
         server-side MVC-to-REST self-call involved. Friendly grouping/labels below are
         presentation-only; every checkbox's submitted value is still the exact raw table name
         MultiFormatExportService#governedTables already returns and #export already accepts -
         backend authorization/table-resolution is completely unchanged. --%>
    <form method="get" action="${pageContext.request.contextPath}/api/v1/exports" id="exportForm">
        <div class="panel export-format-panel">
            <label class="export-format-label" for="exportFormat">Export Format</label>
            <select name="format" id="exportFormat" class="export-format-select">
                <option value="JSON">JSON</option>
                <option value="CSV">CSV</option>
                <option value="XLSX">XLSX</option>
            </select>
        </div>

        <div class="panel export-scope-panel">
            <div class="export-scope-head">
                <h2>Scope</h2>
                <span class="export-selected-count" id="exportSelectedCount">0 scopes selected</span>
            </div>
            <div class="export-scope-groups">
                <c:forEach var="group" items="${scopeGroups}">
                    <div class="export-scope-group">
                        <div class="export-scope-group-head">
                            <label class="export-select-all">
                                <input type="checkbox" class="export-select-all-checkbox" data-group="${fn:escapeXml(group.key)}">
                                <strong><c:out value="${group.key}"/></strong>
                            </label>
                        </div>
                        <div class="export-scope-checklist">
                            <c:forEach var="t" items="${group.value}">
                                <label class="export-scope-item">
                                    <input type="checkbox" name="tables" value="${t}" class="export-scope-checkbox" data-group="${fn:escapeXml(group.key)}">
                                    <c:out value="${tableLabels[t]}"/>
                                </label>
                            </c:forEach>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </div>

        <div class="note-box reports-info-banner export-note">
            Exports are generated synchronously and streamed as a download once ready. CSV exports require exactly one scope selected.
        </div>

        <div class="export-actions">
            <span class="export-summary" id="exportSummary">Select a format and at least one scope to export.</span>
            <button type="submit" class="btn-primary" id="exportSubmitBtn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                Generate Export
            </button>
        </div>
    </form>
</main>
<script src="${pageContext.request.contextPath}/js/reports-workspace.js" defer></script>
</body>
</html>
