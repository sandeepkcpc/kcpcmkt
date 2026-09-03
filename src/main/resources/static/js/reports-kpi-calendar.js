/**
 * Reports -> KPI Dashboard -> Overview -> Upcoming Channel Plan -> calendar icon. Purely a
 * different PRESENTATION of the exact same data the existing date-grouped list already renders -
 * ${overview.upcomingChannelPlan}, embedded as JSON (#kpiUpcomingPlanData, see
 * ReportingMvcController#buildUpcomingChannelPlanJson) rather than fetched separately, so there is
 * no second query and no way for the calendar to ever disagree with the list. A date is
 * "highlighted" purely because it is present in this already-filtered data - nothing here
 * re-derives or duplicates the "outstanding" definition (the exit-on-actual-publication rule,
 * NON_TERMINAL_EXCLUSIONS, etc. all already happened server-side in
 * KpiDashboardService#upcomingChannelPlan before this JSON was built). Current-state, same as the
 * list itself - that data was never date-ranged to begin with, so the calendar isn't either; it
 * simply reads whatever is already in the JSON block regardless of the KPI Dashboard's selected
 * Date Range.
 *
 * Uses document-level delegation (same reasoning as reports-kpi-ownership-drilldown.js) so this
 * keeps working after #reportsKpiDynamicRegion is replaced by an AJAX swap - the trigger button,
 * modal, and JSON data block only ever exist in the DOM while Overview is the active view anyway.
 */
(function () {
    // Local calendar-date math only, via the Date object's own local getters - never
    // toISOString()/UTC conversion (the same bug class already found and fixed twice this session,
    // in reviews-workspace.js and reports-kpi-date-preset.js).
    function toLocalIsoDate(date) {
        var year = date.getFullYear();
        var month = String(date.getMonth() + 1).padStart(2, '0');
        var day = String(date.getDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    }

    function parsePlanData() {
        var el = document.getElementById('kpiUpcomingPlanData');
        if (!el || !el.textContent.trim()) {
            return {};
        }
        var groups;
        try {
            groups = JSON.parse(el.textContent);
        } catch (e) {
            return {};
        }
        var byDate = {};
        (groups || []).forEach(function (g) {
            if (g && g.plannedLiveDate) {
                byDate[g.plannedLiveDate] = g.channels || [];
            }
        });
        return byDate;
    }

    var MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August',
        'September', 'October', 'November', 'December'];

    var state = {year: null, month: null, selectedDate: null}; // month is 0-11

    function renderGrid(byDate) {
        var grid = document.getElementById('kpiCalendarGrid');
        var monthLabelEl = document.getElementById('kpiCalendarMonthLabel');
        if (!grid || !monthLabelEl || state.year == null) {
            return;
        }
        monthLabelEl.textContent = MONTH_NAMES[state.month] + ' ' + state.year;
        grid.innerHTML = '';

        var firstOfMonth = new Date(state.year, state.month, 1);
        var startWeekday = firstOfMonth.getDay(); // 0 = Sunday, matches the Su..Sa header
        var daysInMonth = new Date(state.year, state.month + 1, 0).getDate();

        for (var lead = 0; lead < startWeekday; lead++) {
            var blank = document.createElement('span');
            blank.className = 'kpi-calendar-cell kpi-calendar-cell-empty';
            grid.appendChild(blank);
        }
        for (var day = 1; day <= daysInMonth; day++) {
            var iso = toLocalIsoDate(new Date(state.year, state.month, day));
            var planned = !!byDate[iso];
            // Only a planned date is ever interactive - an unplanned date renders as a plain
            // <span>, never a <button>, so it can never look clickable or receive focus.
            var cell = document.createElement(planned ? 'button' : 'span');
            if (planned) {
                cell.type = 'button';
            }
            var cls = 'kpi-calendar-cell';
            if (planned) {
                cls += ' kpi-calendar-cell-planned';
            }
            if (state.selectedDate === iso) {
                cls += ' kpi-calendar-cell-selected';
            }
            cell.className = cls;
            cell.textContent = String(day);
            cell.setAttribute('data-date', iso);
            grid.appendChild(cell);
        }
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function renderDetail(iso, byDate) {
        var detail = document.getElementById('kpiCalendarDetail');
        if (!detail) {
            return;
        }
        var channels = iso ? byDate[iso] : null;
        if (!channels || channels.length === 0) {
            detail.innerHTML = '<p class="muted">Select a highlighted date to see its planning details.</p>';
            return;
        }
        var dateObj = new Date(iso + 'T00:00:00');
        var formatted = dateObj.toLocaleDateString('en-GB', {day: '2-digit', month: 'short', year: 'numeric'});
        var html = '<h4 class="kpi-calendar-detail-date">' + escapeHtml(formatted) + '</h4>'
            + '<ul class="kpi-calendar-detail-list">';
        var allContentIds = [];
        channels.forEach(function (ch) {
            html += '<li><span class="kpi-calendar-detail-channel">' + escapeHtml(ch.channelHandle) + '</span>'
                + '<span class="kpi-calendar-detail-count">' + escapeHtml(ch.count) + '</span></li>';
            if (ch.contentIds) {
                allContentIds = allContentIds.concat(ch.contentIds);
            }
        });
        html += '</ul>';
        // Content Details: only shown when the existing data already carries Content IDs (from
        // KpiDashboardService#upcomingChannelPlan's own already-iterated mapping rows - never a
        // second query, never invented here) - an older/unmigrated response with no contentIds
        // field on any channel simply omits this section rather than showing an empty one.
        //
        // Presentation-only dedup: one Content ID can legitimately back several outstanding
        // mapping rows (multiple Planned Outputs and/or multiple Publication Targets under one
        // Channel), so allContentIds itself is left exactly as received - raw, undeduplicated,
        // matching ch.count above 1:1 - and is never used for anything else. uniqueContentIds is
        // a separate, display-only view of it: Set() preserves first-seen insertion order, so
        // Array.from() here needs no extra sort. This count is deliberately NOT the same number
        // as the Planned column above (ch.count, untouched) - it answers "how many distinct
        // pieces of content", not "how many outstanding output x target commitments".
        var uniqueContentIds = Array.from(new Set(allContentIds));
        if (uniqueContentIds.length > 0) {
            html += '<details class="kpi-calendar-content-details">'
                + '<summary>Content Details (' + uniqueContentIds.length + ')</summary>'
                + '<ul class="kpi-calendar-content-list">'
                + uniqueContentIds.map(function (id) {
                    return '<li><span class="kpi-calendar-content-id">' + escapeHtml(id) + '</span>'
                        + '<span class="status-pill status-pending">Planned</span></li>';
                }).join('')
                + '</ul></details>';
        }
        detail.innerHTML = html;
    }

    function openCalendar() {
        var overlay = document.getElementById('kpiUpcomingCalendarOverlay');
        if (!overlay) {
            return;
        }
        var today = new Date();
        state.year = today.getFullYear();
        state.month = today.getMonth();
        state.selectedDate = null;
        var byDate = parsePlanData();
        renderGrid(byDate);
        renderDetail(null, byDate);
        overlay.classList.remove('hidden');
    }

    function closeCalendar() {
        var overlay = document.getElementById('kpiUpcomingCalendarOverlay');
        if (overlay) {
            overlay.classList.add('hidden');
        }
    }

    function changeMonth(delta) {
        if (state.year == null) {
            return;
        }
        var next = new Date(state.year, state.month + delta, 1);
        state.year = next.getFullYear();
        state.month = next.getMonth();
        renderGrid(parsePlanData());
    }

    document.addEventListener('click', function (event) {
        if (event.target.closest('#kpiUpcomingCalendarOpen')) {
            openCalendar();
            return;
        }
        if (event.target.closest('#kpiUpcomingCalendarClose')) {
            closeCalendar();
            return;
        }
        if (event.target.id === 'kpiUpcomingCalendarOverlay') {
            closeCalendar(); // clicking the backdrop itself closes it
            return;
        }
        if (event.target.closest('.kpi-calendar-prev')) {
            changeMonth(-1);
            return;
        }
        if (event.target.closest('.kpi-calendar-next')) {
            changeMonth(1);
            return;
        }
        var cell = event.target.closest('.kpi-calendar-cell-planned');
        if (cell) {
            var byDate = parsePlanData();
            state.selectedDate = cell.getAttribute('data-date');
            renderGrid(byDate);
            renderDetail(state.selectedDate, byDate);
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeCalendar();
        }
    });
})();
