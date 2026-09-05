/**
 * kcpc-date-picker.js - the Idea Review & Planning -> Schedule calendars: Planned Live Date, Shoot
 * Date and Edit Date.
 *
 * Two jobs, in one popover:
 *   1. Pick a date. Past dates are disabled; today and future dates are selectable.
 *   2. Show where work is ALREADY planned, so a planner can see the load on a date before committing
 *      to it - the same information, and the same numbers, as the KPI Dashboard's Upcoming Channel
 *      Plan.
 *
 * SINGLE SOURCE OF TRUTH FOR THE COUNTS. Nothing here counts anything. Every channel count comes
 * pre-computed from UpcomingChannelPlanService - the one implementation the KPI Dashboard Overview
 * also uses - serialized into an embedded JSON block by the Planning screen's own controller. The
 * three calendars read three different blocks (see KINDS below) because the server grouped the same
 * aggregation on three different content_plans date columns; the RULE is identical for all of them
 * and lives entirely server-side: distinct Content ID per (date, Channel/Account), so Instagram +
 * YouTube + Facebook under one Channel/Account is 1, the same Content ID on two channels is 1 under
 * each, and platforms/publication targets/mapping rows are never what is counted.
 *
 * STRICTLY OPT-IN, one attribute per field: it only ever touches
 * `input[type="date"][data-kcpc-calendar]`, whose value ("live"/"shoot"/"edit") selects that field's
 * dataset and wording. Planning mode, stage selection, assignments, the auto-calculated date
 * defaults and every other field are never read or modified by this file.
 *
 * ADDITIVE BY DESIGN. Each native <input type="date"> stays exactly as it is in the DOM - same
 * name/id, same `min`, same `required`, same yyyy-MM-dd value - so form serialization, HTML
 * constraint validation, server-side validation and every existing listener keep working untouched.
 * This file only writes `input.value` and then re-dispatches the SAME `input`/`change` events the
 * browser itself would fire. That is what keeps the existing schedule logic intact in both
 * directions: reviews-workspace.js recalculates Shoot (Live - 5) / Edit (Live - 2) from its `change`
 * handler on #reviewsIdeaPlannedLiveDate, and re-validates from its `input` handler on
 * #reviewsIdeaShootDate / #reviewsIdeaEditDate when either is overridden by hand.
 *
 * PAST DATES are read straight off each field's own `min` attribute (server-rendered as ${today}) -
 * never a second, client-side idea of "today" that could drift from the server's Asia/Kolkata date.
 * A date outside [min, max] renders as a plain, non-interactive <span>, so it can never be clicked
 * or focused. A field with no `min` has nothing disabled - this file never invents a restriction the
 * form did not ask for.
 *
 * Uses document-level delegation (same reasoning as reports-kpi-calendar.js) so it keeps working
 * after an AJAX partial-fragment swap replaces the Planning form's container, with no re-wire hook.
 */
(function () {
    'use strict';

    var MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August',
        'September', 'October', 'November', 'December'];
    var SHORT_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var ATTR = 'data-kcpc-calendar';
    var POPOVER_ID = 'kcpcDatePickerPopover';

    /**
     * One entry per calendar. Each names the embedded JSON block it reads and the wording of its own
     * detail panel/legend - nothing else differs between them, because the counts themselves all
     * come from the SAME server-side aggregation (UpcomingChannelPlanService#groupBy), just grouped
     * on a different content_plans date column. A field selects its entry with
     * data-kcpc-calendar="live|shoot|edit"; a bare attribute means "live".
     */
    var KINDS = {
        live: {
            dataId: 'kcpcPlannedLiveDatePlanData',
            caption: 'Planned Content By Channel',
            legend: 'Has planned content',
            empty: 'No planned content available for this date.'
        },
        shoot: {
            dataId: 'kcpcPlannedShootPlanData',
            caption: 'Planned Shoots by Channel',
            legend: 'Has planned shoots',
            empty: 'No planned shoots available for this date.'
        },
        edit: {
            dataId: 'kcpcPlannedEditPlanData',
            caption: 'Planned Edits by Channel',
            legend: 'Has planned edits',
            empty: 'No planned edits available for this date.'
        }
    };

    function kindOf(input) {
        var name = input.getAttribute(ATTR);
        return KINDS[name] || KINDS.live;
    }

    // Local calendar-date math only, via the Date object's own local getters - never
    // toISOString()/UTC conversion, which silently shifts a local calendar date back one day in any
    // positive-UTC-offset timezone (Asia/Kolkata, +5:30, this app's own business timezone). Same
    // pattern as reports-kpi-calendar.js and reports-kpi-date-preset.js.
    function toLocalIsoDate(date) {
        var year = date.getFullYear();
        var month = String(date.getMonth() + 1).padStart(2, '0');
        var day = String(date.getDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    }

    function isIsoDate(value) {
        return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
    }

    /** "2026-09-05" -> "05 Sep 2026". Built from the ISO parts, never Date.toLocaleDateString, so
     *  the rendered date can never drift by a day or change shape with the browser's locale. */
    function formatDisplayDate(iso) {
        if (!isIsoDate(iso)) {
            return '';
        }
        var parts = iso.split('-');
        return parts[2] + ' ' + SHORT_MONTHS[parseInt(parts[1], 10) - 1] + ' ' + parts[0];
    }

    /**
     * The already-planned work for one calendar, keyed by date. Read from that calendar's own
     * embedded JSON block - the exact List<UpcomingPlanDateGroup> UpcomingChannelPlanService
     * produced. The DTO field is named plannedLiveDate for all three because all three are the same
     * DTO; for the Shoot/Edit calendars it carries the Planned Shoot/Edit Date the server grouped on.
     * A missing/malformed block is not an error: the calendar simply has nothing to highlight and
     * still works as a plain date picker.
     */
    function parsePlanData(dataId) {
        var el = document.getElementById(dataId);
        if (!el || !el.textContent || !el.textContent.trim()) {
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

    /**
     * The month grid as pure data - no DOM - so the disabled/selectable/planned rules are testable
     * on their own and each has exactly one definition. ISO yyyy-MM-dd strings compare correctly
     * with < and >, so the bounds need no Date parsing at all.
     *
     * Returns leading blanks as null, and each day as {day, iso, disabled, selected, today, planned}.
     */
    function buildMonthCells(year, month, options) {
        var opts = options || {};
        var min = isIsoDate(opts.min) ? opts.min : null;
        var max = isIsoDate(opts.max) ? opts.max : null;
        var selected = isIsoDate(opts.selected) ? opts.selected : null;
        var todayIso = isIsoDate(opts.today) ? opts.today : null;
        var byDate = opts.planned || {};

        var cells = [];
        var startWeekday = new Date(year, month, 1).getDay(); // 0 = Sunday, matches the Su..Sa header
        for (var lead = 0; lead < startWeekday; lead++) {
            cells.push(null);
        }
        var daysInMonth = new Date(year, month + 1, 0).getDate();
        for (var day = 1; day <= daysInMonth; day++) {
            var iso = toLocalIsoDate(new Date(year, month, day));
            var disabled = (min !== null && iso < min) || (max !== null && iso > max);
            cells.push({
                day: day,
                iso: iso,
                // The whole past-date rule, in one place: before `min` (today) or after `max`.
                disabled: disabled,
                selected: selected !== null && iso === selected,
                today: todayIso !== null && iso === todayIso,
                // A disabled date is never marked as planned: the highlight exists to guide a
                // choice, and a past date is not a choice. Matches "highlight all FUTURE dates
                // having planned content".
                planned: !disabled && Object.prototype.hasOwnProperty.call(byDate, iso)
                    && (byDate[iso] || []).length > 0
            });
        }
        return cells;
    }

    // ---------------------------------------------------------------- popover state (one at a time)

    var state = {input: null, year: null, month: null, popover: null, detailDate: null, byDate: {}, kind: KINDS.live};

    function closePicker() {
        if (state.popover && state.popover.parentNode) {
            state.popover.parentNode.removeChild(state.popover);
        }
        state.popover = null;
        state.input = null;
        state.year = null;
        state.month = null;
        state.detailDate = null;
        state.byDate = {};
        state.kind = KINDS.live;
    }

    function isOpen() {
        return !!state.popover;
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function buildPopoverShell() {
        var pop = document.createElement('div');
        pop.className = 'kcpc-datepicker';
        pop.id = POPOVER_ID;
        pop.setAttribute('role', 'dialog');
        pop.setAttribute('aria-label', 'Choose date');

        var nav = document.createElement('div');
        nav.className = 'kcpc-datepicker-nav';
        var prev = document.createElement('button');
        prev.type = 'button';
        prev.className = 'btn-outline kcpc-datepicker-prev';
        prev.setAttribute('aria-label', 'Previous month');
        prev.textContent = '‹';
        var label = document.createElement('span');
        label.className = 'kcpc-datepicker-month-label';
        var next = document.createElement('button');
        next.type = 'button';
        next.className = 'btn-outline kcpc-datepicker-next';
        next.setAttribute('aria-label', 'Next month');
        next.textContent = '›';
        nav.appendChild(prev);
        nav.appendChild(label);
        nav.appendChild(next);

        var weekdays = document.createElement('div');
        weekdays.className = 'kcpc-datepicker-weekdays';
        ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].forEach(function (name) {
            var d = document.createElement('span');
            d.textContent = name;
            weekdays.appendChild(d);
        });

        var grid = document.createElement('div');
        grid.className = 'kcpc-datepicker-grid';

        // Three states, spelled out - full-cell swatches, never dots (the cells they describe are
        // themselves whole-cell highlights).
        var legend = document.createElement('div');
        legend.className = 'kcpc-datepicker-legend';
        [['planned', state.kind.legend], ['free', 'No planned work'], ['past', 'Past date (not selectable)']]
            .forEach(function (pair) {
                var item = document.createElement('span');
                item.className = 'kcpc-datepicker-legend-item';
                var swatch = document.createElement('span');
                swatch.className = 'kcpc-datepicker-legend-swatch kcpc-datepicker-legend-swatch-' + pair[0];
                var text = document.createElement('span');
                text.textContent = pair[1];
                item.appendChild(swatch);
                item.appendChild(text);
                legend.appendChild(item);
            });

        var detail = document.createElement('div');
        detail.className = 'kcpc-datepicker-detail';

        pop.appendChild(nav);
        pop.appendChild(weekdays);
        pop.appendChild(grid);
        pop.appendChild(legend);
        pop.appendChild(detail);
        return pop;
    }

    function renderGrid() {
        if (!state.popover || !state.input) {
            return;
        }
        var grid = state.popover.querySelector('.kcpc-datepicker-grid');
        var label = state.popover.querySelector('.kcpc-datepicker-month-label');
        if (!grid || !label) {
            return;
        }
        label.textContent = MONTH_NAMES[state.month] + ' ' + state.year;
        grid.innerHTML = '';

        var cells = buildMonthCells(state.year, state.month, {
            min: state.input.getAttribute('min'),
            max: state.input.getAttribute('max'),
            selected: state.input.value,
            today: toLocalIsoDate(new Date()),
            planned: state.byDate
        });

        cells.forEach(function (cell) {
            if (cell === null) {
                var blank = document.createElement('span');
                blank.className = 'kcpc-datepicker-cell kcpc-datepicker-cell-empty';
                grid.appendChild(blank);
                return;
            }
            // Only a selectable date is ever a real <button> - a disabled (past) date renders as a
            // plain <span>, so it can never be clicked, focused or tabbed to. Exactly the pattern
            // reports-kpi-calendar.js uses for its own non-interactive days.
            var el = document.createElement(cell.disabled ? 'span' : 'button');
            var cls = 'kcpc-datepicker-cell';
            if (cell.disabled) {
                cls += ' kcpc-datepicker-cell-disabled';
                el.setAttribute('aria-disabled', 'true');
            } else {
                el.type = 'button';
            }
            if (cell.planned) {
                // Whole-cell background highlight, never a dot or any other indicator.
                cls += ' kcpc-datepicker-cell-planned';
                el.setAttribute('data-planned', 'true');
            }
            if (cell.today) {
                cls += ' kcpc-datepicker-cell-today';
            }
            if (cell.selected) {
                cls += ' kcpc-datepicker-cell-selected';
                el.setAttribute('aria-current', 'date');
            }
            el.className = cls;
            el.textContent = String(cell.day);
            el.setAttribute('data-date', cell.iso);
            grid.appendChild(el);
        });
    }

    /**
     * The detail panel: "Planned Content By Channel" for one date, rendered straight from the
     * server's own channel/count pairs. Nothing is recomputed, re-summed or re-deduplicated here -
     * ch.count IS the distinct-Content-ID count UpcomingChannelPlanService produced.
     */
    function renderDetail() {
        if (!state.popover) {
            return;
        }
        var detail = state.popover.querySelector('.kcpc-datepicker-detail');
        if (!detail) {
            return;
        }
        if (!state.detailDate) {
            detail.innerHTML = '<p class="kcpc-datepicker-detail-hint">Select a date to see what is already planned.</p>';
            return;
        }
        var html = '<h4 class="kcpc-datepicker-detail-date">' + escapeHtml(formatDisplayDate(state.detailDate)) + '</h4>';
        var channels = state.byDate[state.detailDate] || [];
        if (channels.length === 0) {
            detail.innerHTML = html + '<p class="kcpc-datepicker-detail-empty">' + escapeHtml(state.kind.empty) + '</p>';
            return;
        }
        html += '<p class="kcpc-datepicker-detail-caption">' + escapeHtml(state.kind.caption) + '</p>'
            + '<ul class="kcpc-datepicker-detail-list">';
        channels.forEach(function (ch) {
            html += '<li><span class="kcpc-datepicker-detail-channel">' + escapeHtml(ch.channelHandle) + '</span>'
                + '<span class="kcpc-datepicker-detail-count">' + escapeHtml(ch.count) + '</span></li>';
        });
        detail.innerHTML = html + '</ul>';
    }

    /** Anchored under the field, using fixed positioning so the popover is never clipped by a
     *  scrolling container or a modal body it happens to be rendered inside. */
    function positionPopover() {
        if (!state.popover || !state.input || typeof state.input.getBoundingClientRect !== 'function') {
            return;
        }
        var rect = state.input.getBoundingClientRect();
        var width = state.popover.offsetWidth || 300;
        var height = state.popover.offsetHeight || 380;
        var viewportW = window.innerWidth || 1024;
        var viewportH = window.innerHeight || 768;

        var left = Math.max(8, Math.min(rect.left, viewportW - width - 8));
        // Flip above the field when there is not enough room below it.
        var top = rect.bottom + 6;
        if (top + height > viewportH - 8 && rect.top - height - 6 > 8) {
            top = rect.top - height - 6;
        }
        state.popover.style.left = left + 'px';
        state.popover.style.top = top + 'px';
    }

    function openPicker(input) {
        closePicker();
        state.input = input;
        state.kind = kindOf(input);
        state.byDate = parsePlanData(state.kind.dataId);

        // Open on the month of the currently selected value; otherwise on `min` if today is already
        // past it (so the first thing shown is always a month with selectable dates); otherwise today.
        var anchorIso = isIsoDate(input.value) ? input.value : null;
        var min = input.getAttribute('min');
        var todayIso = toLocalIsoDate(new Date());
        if (anchorIso === null) {
            anchorIso = (isIsoDate(min) && todayIso < min) ? min : todayIso;
        }
        var parts = anchorIso.split('-');
        state.year = parseInt(parts[0], 10);
        state.month = parseInt(parts[1], 10) - 1;
        // A field that already holds a date opens with that date's planning detail already shown.
        state.detailDate = isIsoDate(input.value) ? input.value : null;

        state.popover = buildPopoverShell();
        document.body.appendChild(state.popover);
        renderGrid();
        renderDetail();
        positionPopover();
    }

    function changeMonth(delta) {
        if (!isOpen()) {
            return;
        }
        var next = new Date(state.year, state.month + delta, 1);
        state.year = next.getFullYear();
        state.month = next.getMonth();
        renderGrid();
        positionPopover();
    }

    /**
     * Write the chosen date back into the real field, then re-dispatch the exact events the browser
     * fires for a native pick. This is the whole regression-safety contract: reviews-workspace.js's
     * Shoot/Edit auto-calculation listens for `change` on #reviewsIdeaPlannedLiveDate, and other
     * code listens for `input` - both must see this the same as a native selection, so both are
     * dispatched, bubbling, in the browser's own order (input then change).
     */
    function commitDate(iso) {
        var input = state.input;
        if (!input) {
            return;
        }
        input.value = iso;
        dispatch(input, 'input');
        dispatch(input, 'change');
        closePicker();
        if (typeof input.focus === 'function') {
            input.focus();
        }
    }

    function dispatch(el, type) {
        var event;
        if (typeof window.Event === 'function') {
            event = new Event(type, {bubbles: true});
        } else if (document.createEvent) { // legacy fallback
            event = document.createEvent('Event');
            event.initEvent(type, true, false);
        } else {
            return;
        }
        el.dispatchEvent(event);
    }

    // ------------------------------------------------------------------------------- wiring

    document.addEventListener('mousedown', function (event) {
        var field = event.target.closest
            ? event.target.closest('input[type="date"][' + ATTR + ']')
            : null;
        if (field) {
            // Suppress the browser's own picker for this field only, so the two calendars can never
            // both be open. The field itself stays fully editable/typable by keyboard.
            event.preventDefault();
            if (isOpen() && state.input === field) {
                closePicker();
            } else {
                openPicker(field);
                if (typeof field.focus === 'function') {
                    field.focus();
                }
            }
            return;
        }
        // A click anywhere outside the open popover closes it.
        if (isOpen() && event.target.closest && !event.target.closest('#' + POPOVER_ID)) {
            closePicker();
        }
    });

    document.addEventListener('click', function (event) {
        if (!isOpen() || !event.target.closest) {
            return;
        }
        if (event.target.closest('.kcpc-datepicker-prev')) {
            changeMonth(-1);
            return;
        }
        if (event.target.closest('.kcpc-datepicker-next')) {
            changeMonth(1);
            return;
        }
        var cell = event.target.closest('button.kcpc-datepicker-cell');
        if (!cell || cell.classList.contains('kcpc-datepicker-cell-disabled')) {
            return;
        }
        var iso = cell.getAttribute('data-date');
        // A date that already has planned content shows its detail panel first, so the planner sees
        // the existing load before committing; a second click on the same date confirms it. A date
        // with nothing planned is picked straight away - there is nothing to review.
        if (cell.getAttribute('data-planned') === 'true' && state.detailDate !== iso) {
            state.detailDate = iso;
            renderGrid();
            renderDetail();
            positionPopover();
            return;
        }
        commitDate(iso);
    });

    document.addEventListener('keydown', function (event) {
        if (!isOpen()) {
            return;
        }
        if (event.key === 'Escape') {
            var input = state.input;
            closePicker();
            if (input && typeof input.focus === 'function') {
                input.focus();
            }
        }
    });

    // The popover is body-anchored, so it must follow the field if the page moves underneath it.
    window.addEventListener('resize', function () { positionPopover(); });
    window.addEventListener('scroll', function () { positionPopover(); }, true);

    // Exposed purely so the date/highlight rules can be unit-tested without a DOM (src/test/js/
    // kcpc-date-picker.test.js). Not part of any page's runtime contract.
    window.KcpcDatePicker = {
        buildMonthCells: buildMonthCells,
        toLocalIsoDate: toLocalIsoDate,
        formatDisplayDate: formatDisplayDate
    };
})();
