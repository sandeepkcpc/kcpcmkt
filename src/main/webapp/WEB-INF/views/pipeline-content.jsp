<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%-- ENG-081: AJAX-only partial - LandingMvcController#pipeline returns this view (instead of
     "pipeline") when the request carries the X-Requested-With: fetch header. Same model, same
     query/filter/sort/pagination logic; this view differs only in that it has no <html>/<head>/
     nav wrapper, so the raw response body IS exactly #pipelineDynamicRegion's new content -
     pipeline-dashboard.js drops it straight into innerHTML with no parsing needed. Never rendered
     for a normal browser navigation (no header), which always gets the full pipeline.jsp. --%>
<%@ include file="fragments/pipeline-content.jspf" %>
