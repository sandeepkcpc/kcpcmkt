<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="kcpc" uri="https://kcpc.internal/tags/functions" %>
<%-- ENG-087: AJAX-only partial - ReportingMvcController#workload returns this view (instead of
     "reports-workload") when the request carries the X-Requested-With: fetch header. Same model,
     same filter logic; no <html>/<head>/nav wrapper, so the raw response body IS exactly
     #teamWorkloadDynamicRegion's new content. Never rendered for a normal browser navigation. --%>
<%@ include file="fragments/team-workload-content.jspf" %>
