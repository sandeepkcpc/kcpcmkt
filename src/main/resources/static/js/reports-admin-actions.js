/**
 * Administrative Actions Date Range fix - the From/To fields already had the correct name
 * attributes (startDate/endDate) and the backend (AdminReportingService#administrativeActionRows)
 * already filters correctly on those exact params, but nothing ever submitted them: this form had
 * no Apply/Filter button, and reports-workspace.js's generic auto-submit "change" handler only
 * ever covers <select> elements, never input[type="date"]. This file adds the one missing piece -
 * From > To validation that runs before the existing generic submit handler - and nothing else;
 * a real <button type="submit"> in the JSP is what actually makes Filter submit the form, reusing
 * reports-workspace.js's own pre-existing 'submit' listener on this same region unchanged.
 *
 * Deliberately a SEPARATE file, not an extension of reports-workspace.js: that file is shared by
 * the KPI Dashboard/Delayed Deliverables/Audit History screens too, and this fix must stay isolated
 * to Administrative Actions (same reasoning reports-kpi-date-preset.js was already kept separate
 * from reports-workspace.js for the KPI Dashboard's own Date Range). Script-tag order in
 * reports-admin-actions.jsp places this file BEFORE reports-workspace.js specifically so this
 * listener registers first - same event, same node (#reportsAdminActionsDynamicRegion), so an
 * invalid range's stopImmediatePropagation() reaches reports-workspace.js's own 'submit' listener
 * before it ever fires, exactly like reports-kpi-date-preset.js already does for the KPI form.
 */
(function () {
    var region = document.getElementById('reportsAdminActionsDynamicRegion');
    if (!region) {
        return;
    }

    function showDateRangeError(message) {
        var box = document.getElementById('aaDateRangeError');
        if (box) {
            box.textContent = message;
            box.classList.remove('hidden');
        }
    }

    function clearDateRangeError() {
        var box = document.getElementById('aaDateRangeError');
        if (box) {
            box.textContent = '';
            box.classList.add('hidden');
        }
    }

    // Editing a date by hand clears any stale error from a previous invalid Filter attempt.
    region.addEventListener('input', function (event) {
        if (event.target.closest('#aaDateFrom') || event.target.closest('#aaDateTo')) {
            clearDateRangeError();
        }
    });

    // Pre-submit validation: From > To only - both sides are individually optional (the backend
    // already supports open-ended ranges: a blank From defaults to Instant.EPOCH, a blank To
    // defaults to "now"), and From = To is explicitly valid (a single calendar day). Only blocks
    // the specific invalid combination; every other Filter click proceeds exactly as before.
    region.addEventListener('submit', function (event) {
        var form = event.target.closest('#reportsAdminActionsFilterForm');
        if (!form) {
            return;
        }
        var fromField = document.getElementById('aaDateFrom');
        var toField = document.getElementById('aaDateTo');
        if (!fromField || !toField) {
            return;
        }
        // "YYYY-MM-DD" <input type="date"> values compare correctly as plain strings (fixed-width,
        // zero-padded, big-endian) - no Date parsing/UTC conversion needed for this comparison.
        if (fromField.value && toField.value && fromField.value > toField.value) {
            event.preventDefault();
            event.stopImmediatePropagation();
            showDateRangeError('From date cannot be after To date.');
            return;
        }
        clearDateRangeError();
    });
})();
