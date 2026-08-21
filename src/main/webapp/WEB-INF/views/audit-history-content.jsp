<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%-- AJAX-only partial - ReportingMvcController#auditHistory returns this view for X-Requested-With: fetch. --%>
<%@ include file="fragments/audit-history-content.jspf" %>
