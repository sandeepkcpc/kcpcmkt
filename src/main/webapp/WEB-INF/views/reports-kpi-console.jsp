<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — KPI Console</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main app-main-wide">
    <h1>KPI Console — KPI-001 &hellip; KPI-030</h1>
    <p class="muted">Computed on demand from operational tables. No persistent KPI tables.</p>

    <c:forEach var="group" items="${grouped}">
        <div class="panel">
            <h2>${group.key}</h2>
            <div class="kpi-grid">
                <c:forEach var="kpi" items="${group.value}">
                    <div class="kpi-tile">
                        <div class="kpi-value ${kpi.displayValue == 'N/A' ? 'kpi-na' : ''}">${kpi.displayValue}</div>
                        <div class="kpi-label">${kpi.code} &middot; ${kpi.label}</div>
                        <c:if test="${not empty kpi.breakdown}">
                            <ul style="margin:0.5rem 0 0; padding-left:1.1rem; font-size:0.78rem;">
                                <c:forEach var="b" items="${kpi.breakdown}">
                                    <li>${b.key}: ${b.value}</li>
                                </c:forEach>
                            </ul>
                        </c:if>
                    </div>
                </c:forEach>
            </div>
        </div>
    </c:forEach>
</main>
</body>
</html>
