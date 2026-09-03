<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<%-- AJAX-only partial - ReportingMvcController#ownershipDrilldown always renders this (there is no
     non-AJAX variant; it is only ever opened from Overview's Current Work Ownership "Open" button). --%>
<%@ include file="fragments/reports-kpi-ownership-drilldown.jspf" %>
