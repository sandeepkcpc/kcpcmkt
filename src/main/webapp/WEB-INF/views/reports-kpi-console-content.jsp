<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<%-- AJAX-only partial - ReportingMvcController#kpiConsole returns this view (instead of
     "reports-kpi-console") for X-Requested-With: fetch requests. --%>
<%@ include file="fragments/reports-kpi-console-content.jspf" %>
