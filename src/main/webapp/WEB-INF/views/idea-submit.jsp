<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>KCPC Bandhani — Submit Idea</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
</head>
<body>
<jsp:include page="fragments/nav.jsp" />
<main class="app-main">
    <h1>Submit an Idea</h1>
    <p class="muted">Suggest a new content idea for review.</p>

    <c:if test="${not empty errorMessage}">
        <div class="alert-error">${errorMessage}</div>
    </c:if>

    <div class="idea-submit-card">
        <form method="post" action="${pageContext.request.contextPath}/app/ideas" class="form-card idea-submit-form" id="idea-submit-form" novalidate>
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>

            <label for="title">Idea Title <span class="required-mark">*</span></label>
            <input type="text" id="title" name="title" required maxlength="120"
                   placeholder="Enter a short and clear idea title"
                   value="${fn:escapeXml(title)}"
                   class="${errorField == 'title' ? 'input-error' : ''}">
            <c:if test="${errorField == 'title'}"><div class="field-error">${errorMessage}</div></c:if>
            <div class="field-hint-row">
                <span class="field-hint">Keep the title short and specific.</span>
                <span class="char-counter" data-counter-for="title">0 / 120</span>
            </div>

            <label for="referenceLink">Reference Link <span class="optional-mark">(Optional)</span></label>
            <div class="input-with-icon">
                <span class="input-icon">&#128279;</span>
                <input type="text" id="referenceLink" name="referenceLink"
                       placeholder="Paste reference link (e.g. Drive link, YouTube link, Website etc.)"
                       value="${fn:escapeXml(referenceLink)}"
                       class="${errorField == 'referenceLink' ? 'input-error' : ''}">
            </div>
            <c:if test="${errorField == 'referenceLink'}"><div class="field-error">${errorMessage}</div></c:if>
            <div class="field-hint-row">
                <span class="field-hint">Add any reference link that supports your idea.</span>
            </div>

            <label for="additionalNote">Additional Note <span class="optional-mark">(Optional)</span></label>
            <input type="text" id="additionalNote" name="additionalNote"
                   placeholder="Add any additional note"
                   value="${fn:escapeXml(additionalNote)}">
            <div class="field-hint-row">
                <span class="field-hint">Any extra information that might help in understanding your idea.</span>
            </div>

            <label for="notesRemarks">Idea Description / Details <span class="optional-mark">(Optional)</span></label>
            <textarea id="notesRemarks" name="notesRemarks" rows="4" maxlength="500"
                      placeholder="Describe your idea in more detail (what, why, how, target audience, key points etc.)"><c:out value="${notesRemarks}"/></textarea>
            <div class="field-hint-row">
                <span class="char-counter" data-counter-for="notesRemarks">0 / 500</span>
            </div>

            <div class="idea-submit-actions">
                <button type="reset" class="btn-outline" id="idea-submit-reset">Reset</button>
                <button type="submit" id="idea-submit-btn">&#9992; Submit Idea</button>
            </div>
        </form>
    </div>

    <p class="idea-submit-footer-hint">&#9432; Your idea will be visible in My Ideas after submission.</p>
</main>
<script src="${pageContext.request.contextPath}/js/idea-submit.js"></script>
</body>
</html>
