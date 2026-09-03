/**
 * KPI Dashboard Date Range Filter Enhancement - the Preset select (Today/Yesterday/Last 7 Days/
 * Last 30 Days/This Month/Last Month/Custom) computes and previews From/To client-side, plus a
 * pre-submit From/To validation. Presets are resolved to concrete startDate/endDate values ENTIRELY
 * here, before the form ever submits - ReportingMvcController never receives or needs a "preset"
 * parameter, so the existing startDate/endDate contract (DashboardContext/rangeStart/rangeEnd) is
 * completely unchanged; this file is presentation-only, never a second date-range system.
 *
 * Delegated on #reportsKpiDynamicRegion (never on the Preset select/date inputs/form directly) so
 * this keeps working after every AJAX partial-fragment swap (reports-workspace.js's load()
 * replaces the region's innerHTML on every filter/tab change) without needing a re-wire hook -
 * same reasoning reports-kpi-ownership-drilldown.js already documents for its own delegation.
 *
 * Script-load order matters: this file's <script> tag is placed BEFORE reports-workspace.js's in
 * reports-kpi-console.jsp specifically so this listener registers first - a Preset <select> change
 * is caught by BOTH this file (computes/fills From/To) and reports-workspace.js's existing generic
 * "selects auto-submit on change" handler (unchanged, reused as-is per spec's own "unless the
 * existing UI architecture already intentionally auto-applies" allowance); registration order is
 * event-listener firing order for the same event on the same node, so this always finishes writing
 * the freshly-calculated dates into the fields before that handler reads them via FormData.
 */
(function () {
    var region = document.getElementById('reportsKpiDynamicRegion');
    if (!region) {
        return;
    }

    // Local calendar-date math only, via the Date object's own local getters - never
    // toISOString()/UTC conversion, which silently shifts a local calendar date back one day for
    // any positive-UTC-offset timezone (Asia/Kolkata, +5:30, this app's own business timezone -
    // the exact bug already found and fixed this session in reviews-workspace.js's
    // updateReviewsIdeaScheduleDefaults(); this mirrors that same toLocalIsoDate() pattern).
    function toLocalIsoDate(date) {
        var year = date.getFullYear();
        var month = String(date.getMonth() + 1).padStart(2, '0');
        var day = String(date.getDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    }

    function addDays(date, days) {
        var d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
        d.setDate(d.getDate() + days);
        return d;
    }

    // Mirrors ReportingMvcController#matchingDatePreset's own boundary definitions exactly (both
    // sides must agree, or the dropdown's server-rendered "selected" option could disagree with
    // what this preview would compute for the same preset).
    function presetRange(preset) {
        var today = new Date();
        switch (preset) {
            case 'today':
                return {from: today, to: today};
            case 'yesterday':
                var yesterday = addDays(today, -1);
                return {from: yesterday, to: yesterday};
            case 'last7':
                return {from: addDays(today, -6), to: today}; // 7-calendar-day inclusive window ending today
            case 'last30':
                return {from: addDays(today, -29), to: today}; // 30-calendar-day inclusive window ending today
            case 'thisMonth':
                return {from: new Date(today.getFullYear(), today.getMonth(), 1), to: today};
            case 'lastMonth':
                return {
                    from: new Date(today.getFullYear(), today.getMonth() - 1, 1),
                    to: new Date(today.getFullYear(), today.getMonth(), 0)
                };
            default:
                return null; // 'custom' - no computed range, fields stay user-editable as-is
        }
    }

    function showDateRangeError(message) {
        var box = document.getElementById('kpiDateRangeError');
        if (box) {
            box.textContent = message;
            box.classList.remove('hidden');
        }
    }

    function clearDateRangeError() {
        var box = document.getElementById('kpiDateRangeError');
        if (box) {
            box.textContent = '';
            box.classList.add('hidden');
        }
    }

    // Preset change: compute + display From/To immediately ("user can see exactly which dates
    // will be applied" - never requires typing when a preset is chosen), and toggle whether the
    // fields are directly editable. Never touches the form's own submission - a <select> change
    // auto-submitting is reports-workspace.js's existing, unmodified, app-wide behavior for every
    // filter form's <select> elements, reused here as-is per the spec's explicit allowance.
    region.addEventListener('change', function (event) {
        var presetSelect = event.target.closest('#kpiDatePreset');
        if (!presetSelect) {
            return;
        }
        var fromField = document.getElementById('kpiDateFrom');
        var toField = document.getElementById('kpiDateTo');
        if (!fromField || !toField) {
            return;
        }
        var range = presetRange(presetSelect.value);
        if (range) {
            fromField.value = toLocalIsoDate(range.from);
            toField.value = toLocalIsoDate(range.to);
            fromField.readOnly = true;
            toField.readOnly = true;
        } else {
            // Custom: nothing computed for the user to submit yet - unlike every real preset
            // branch above, which deliberately lets reports-workspace.js's generic auto-submit
            // handler fire right after (that's correct there, since fresh dates are already
            // written in by this point). stopImmediatePropagation() here stops that same handler
            // from firing for Custom specifically, so selecting it only unlocks the fields - it
            // never submits the still-stale previous preset's dates, which would otherwise round-
            // trip to the server and have ReportingMvcController#matchingDatePreset snap the
            // dropdown right back to that previous preset before the user can type anything.
            fromField.readOnly = false;
            toField.readOnly = false;
            clearDateRangeError();
            event.stopImmediatePropagation();
            return;
        }
        clearDateRangeError();
    });

    // Editing a Custom date by hand clears any stale error from a previous invalid attempt -
    // Apply re-validates the corrected values on the next submit regardless.
    region.addEventListener('input', function (event) {
        if (event.target.closest('#kpiDateFrom') || event.target.closest('#kpiDateTo')) {
            clearDateRangeError();
        }
    });

    // Pre-submit validation: From/To missing, or From > To - never let an invalid range reach the
    // server as a real filter (defense in depth: ReportingMvcController's own inverted-range guard
    // is the final, non-bypassable layer, exactly like every other date validation in this app).
    // stopImmediatePropagation() on failure prevents reports-workspace.js's own submit listener
    // (registered after this one - see the script-tag ordering note above) from ever firing, so an
    // invalid range never triggers the AJAX reload at all.
    region.addEventListener('submit', function (event) {
        var form = event.target.closest('#reportsKpiFilterForm');
        if (!form) {
            return;
        }
        var fromField = document.getElementById('kpiDateFrom');
        var toField = document.getElementById('kpiDateTo');
        if (!fromField || !toField) {
            return;
        }
        if (!fromField.value) {
            event.preventDefault();
            event.stopImmediatePropagation();
            showDateRangeError('From date is required.');
            return;
        }
        if (!toField.value) {
            event.preventDefault();
            event.stopImmediatePropagation();
            showDateRangeError('To date is required.');
            return;
        }
        // "YYYY-MM-DD" <input type="date"> values compare correctly as plain strings (fixed-width,
        // zero-padded, big-endian) - no Date parsing needed, so there is no timezone conversion
        // step here at all for the comparison itself.
        if (fromField.value > toField.value) {
            event.preventDefault();
            event.stopImmediatePropagation();
            showDateRangeError('From date cannot be after To date.');
            return;
        }
        clearDateRangeError();
    });
})();
