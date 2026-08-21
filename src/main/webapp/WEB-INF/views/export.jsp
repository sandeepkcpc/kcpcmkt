<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Data Export</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Data Export (Management)</h1>
    <div class="panel">
        <%-- Plain browser GET navigation straight to the REST download endpoint - no CSRF token
             needed (GET is a safe method) and no server-side MVC-to-REST self-call involved. --%>
        <form method="get" action="${pageContext.request.contextPath}/api/v1/exports">
            <label>Format
                <select name="format">
                    <option value="JSON">JSON (full table union)</option>
                    <option value="CSV">CSV (single table)</option>
                    <option value="XLSX">XLSX (multi-table workbook)</option>
                </select>
            </label>
            <label>Scope (leave all unchecked for the full governed union)</label>
            <div style="max-height:220px; overflow-y:auto; border:1px solid var(--kcpc-border); border-radius:6px; padding:0.5rem;">
                <c:forEach var="t" items="${governedTables}">
                    <label style="font-weight:400;"><input type="checkbox" name="tables" value="${t}" style="width:auto"> ${t}</label><br/>
                </c:forEach>
            </div>
            <p class="note-box">Synchronous streaming download. No persistent export job. CSV requires exactly one
                table selected; JSON/XLSX may include the full union.</p>
            <button type="submit">Generate Export</button>
        </form>
    </div>
</main>
</body>
</html>
