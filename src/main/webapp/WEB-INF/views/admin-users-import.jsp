<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Administration ▸ Import Users</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
    <link rel="icon" type="image/x-icon" href="<c:url value='/images/favicon.ico'/>">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <%@ include file="fragments/admin-tabs.jspf" %>
    <div class="admin-users-header-row">
        <h1>Import Users</h1>
        <a class="btn-outline" href="${pageContext.request.contextPath}/app/admin/users">Back to Users</a>
    </div>
    <c:if test="${not empty successMessage}"><div class="alert-success">${successMessage}</div></c:if>
    <c:if test="${not empty errorMessage}"><div class="alert-error">${errorMessage}</div></c:if>

    <div class="panel">
        <h2>Step 1 — Upload CSV</h2>
        <p class="note-box">
            Columns required: <strong>full_name, email, business_role</strong>. Optional:
            <strong>account_status</strong> (ACTIVE/INACTIVE, defaults to ACTIVE if left blank).
            Access Class is never a CSV input — it is always resolved from Business Role.
            Download the template and full guide from
            <code>docs/import-templates/KCPC_USER_IMPORT_TEMPLATE.csv</code> and
            <code>docs/USER_CSV_IMPORT_GUIDE.md</code> in the project repository.
        </p>
        <form method="post" action="${pageContext.request.contextPath}/app/admin/users/import/preview"
              enctype="multipart/form-data">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <label>CSV File * <input type="file" name="file" accept=".csv,text/csv" required></label>
            <div class="btn-row"><button type="submit">Validate &amp; Preview</button></div>
        </form>
    </div>
</main>
<script src="<c:url value='/js/admin-shared.js'/>" defer></script>
</body>
</html>
