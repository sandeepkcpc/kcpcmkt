'use strict';
/**
 * Dependency-free test for reports-kpi-calendar.js (Upcoming Channel Plan calendar UX) - no
 * jsdom/npm package, mirroring this session's established Node `vm` harness pattern
 * (reports-kpi-date-preset.test.js). Runs under TZ=Asia/Kolkata so any local-date math in the
 * script (e.g. "today" when the calendar first opens) is exercised under this app's own business
 * timezone. Run with:
 *   node src/test/js/reports-kpi-calendar.test.js
 *
 * The calendar reads its data from a #kpiUpcomingPlanData JSON block, the exact same
 * ${overview.upcomingChannelPlan} the existing list already renders (see
 * ReportingMvcController#buildUpcomingChannelPlanJson) - these tests only exercise the
 * PRESENTATION (highlighting/navigation/detail-panel), never re-derive "outstanding"/exit-rule
 * business logic, which stays entirely server-side and is covered by the Java KPI tests instead.
 */
process.env.TZ = 'Asia/Kolkata';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

class FakeClassList {
    constructor() { this.set = new Set(); }
    add(c) { this.set.add(c); }
    remove(c) { this.set.delete(c); }
    contains(c) { return this.set.has(c); }
    toggle(c, force) {
        if (force === undefined) { force = !this.set.has(c); }
        if (force) { this.set.add(c); } else { this.set.delete(c); }
    }
}

function parseCompound(str) {
    const compound = { tag: null, id: null, classes: [], attrs: {} };
    const re = /(#[\w-]+)|(\.[\w-]+)|(\[[^\]]+\])|([\w-]+)/g;
    let m;
    while ((m = re.exec(str))) {
        if (m[1]) { compound.id = m[1].slice(1); }
        else if (m[2]) { compound.classes.push(m[2].slice(1)); }
        else if (m[3]) {
            const inner = m[3].slice(1, -1);
            const eq = inner.indexOf('=');
            if (eq === -1) { compound.attrs[inner] = true; }
            else {
                const name = inner.slice(0, eq).trim();
                const val = inner.slice(eq + 1).trim().replace(/^"(.*)"$/, '$1').replace(/^'(.*)'$/, '$1');
                compound.attrs[name] = val;
            }
        } else if (m[4]) { compound.tag = m[4]; }
    }
    return compound;
}

function matchesCompound(el, c) {
    if (c.tag && el.tag !== c.tag) { return false; }
    if (c.id && el.id !== c.id) { return false; }
    if (c.classes.some((cl) => !el.classList.contains(cl))) { return false; }
    for (const k in c.attrs) {
        const want = c.attrs[k];
        const got = el.getAttribute(k);
        if (want === true) { if (got === null) { return false; } }
        else if (String(got) !== String(want)) { return false; }
    }
    return true;
}

class FakeElement {
    constructor(tag) {
        this.tag = tag;
        this.id = '';
        this.attrs = {};
        this.children = [];
        this.parent = null;
        this.classList = new FakeClassList();
        this.listeners = {};
        this.value = '';
        this.type = '';
        this._text = '';
        this._html = '';
    }
    setAttribute(name, value) { this.attrs[name] = value; if (name === 'id') { this.id = value; } }
    getAttribute(name) {
        if (name === 'id') { return this.id || null; }
        return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null;
    }
    appendChild(child) { child.parent = this; this.children.push(child); return child; }
    addEventListener(type, handler) { (this.listeners[type] = this.listeners[type] || []).push(handler); }
    closest(selector) {
        let node = this;
        while (node) {
            if (node.matches && node.matches(selector)) { return node; }
            node = node.parent;
        }
        return null;
    }
    matches(selector) { return matchesCompound(this, parseCompound(selector)); }
    get textContent() { return this._text; }
    set textContent(v) {
        this._text = v;
        this.children = [];
        // The script's escapeHtml() relies on the real DOM's textContent->innerHTML escaping
        // trick (write as text, read back as markup) - mirror just enough of that here (&/</>)
        // for this shim, since real browsers do full entity-escaping automatically.
        this._html = String(v == null ? '' : v)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    get className() { return Array.from(this.classList.set).join(' '); }
    set className(v) {
        this.classList = new FakeClassList();
        String(v).split(/\s+/).filter(Boolean).forEach((c) => this.classList.add(c));
    }
    get innerHTML() { return this._html; }
    set innerHTML(v) {
        this._html = v;
        if (v === '') { this.children = []; } // the only innerHTML-clearing shape this script uses
    }
    querySelectorAll(selector) {
        const compounds = selector.trim().split(/\s+/).map(parseCompound);
        const last = compounds[compounds.length - 1];
        const out = [];
        const walk = (node) => {
            node.children.forEach((child) => {
                if (matchesCompound(child, last)) { out.push(child); }
                walk(child);
            });
        };
        walk(this);
        return out;
    }
    getElementById(id) {
        const found = { el: null };
        const walk = (node) => {
            if (found.el) { return; }
            node.children.forEach((child) => {
                if (found.el) { return; }
                if (child.id === id) { found.el = child; return; }
                walk(child);
            });
        };
        walk(this);
        return found.el;
    }
    dispatch(type) {
        const event = { type, target: this };
        let node = this;
        while (node) {
            (node.listeners[type] || []).forEach((h) => h(Object.assign({}, event, { currentTarget: node })));
            node = node.parent;
        }
    }
}

function buildDom(planData) {
    const document = new FakeElement('document');
    document.getElementById = document.getElementById.bind(document);
    document.addEventListener = FakeElement.prototype.addEventListener.bind(document);
    document.createElement = function (tag) { return new FakeElement(tag); };

    function el(tag, id, classes) {
        const e = new FakeElement(tag);
        if (id) { e.setAttribute('id', id); }
        (classes || []).forEach((c) => e.classList.add(c));
        document.appendChild(e);
        return e;
    }

    const openBtn = el('button', 'kpiUpcomingCalendarOpen');
    const overlay = el('div', 'kpiUpcomingCalendarOverlay');
    overlay.classList.add('hidden');
    const closeBtn = new FakeElement('button');
    closeBtn.setAttribute('id', 'kpiUpcomingCalendarClose');
    overlay.appendChild(closeBtn);
    const prevBtn = new FakeElement('button');
    prevBtn.classList.add('kpi-calendar-prev');
    overlay.appendChild(prevBtn);
    const monthLabel = new FakeElement('span');
    monthLabel.setAttribute('id', 'kpiCalendarMonthLabel');
    overlay.appendChild(monthLabel);
    const nextBtn = new FakeElement('button');
    nextBtn.classList.add('kpi-calendar-next');
    overlay.appendChild(nextBtn);
    const grid = new FakeElement('div');
    grid.setAttribute('id', 'kpiCalendarGrid');
    overlay.appendChild(grid);
    const detail = new FakeElement('div');
    detail.setAttribute('id', 'kpiCalendarDetail');
    overlay.appendChild(detail);

    const dataScript = el('script', 'kpiUpcomingPlanData');
    dataScript.textContent = JSON.stringify(planData);

    return { document, openBtn, overlay, closeBtn, prevBtn, nextBtn, monthLabel, grid, detail };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'reports-kpi-calendar.js'), 'utf8');
    const sandbox = { document, console };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'reports-kpi-calendar.js' });
}

function localIso(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + d;
}

function plannedCells(dom) {
    return dom.grid.children.filter((c) => c.classList.contains('kpi-calendar-cell-planned'));
}

function countOccurrences(haystack, needle) {
    return haystack.split(needle).length - 1;
}

function run(name, fn) {
    try {
        fn();
        console.log('PASS - ' + name);
    } catch (e) {
        console.error('FAIL - ' + name);
        console.error(e.stack || e);
        process.exitCode = 1;
    }
}

// --- 1: calendar icon opens the calendar, and it starts on the current month -----------------
run('Clicking the calendar icon opens the calendar on the current month', () => {
    const today = new Date();
    const dom = buildDom([]);
    loadScriptAgainst(dom.document);
    assert.ok(dom.overlay.classList.contains('hidden'), 'sanity: starts closed');

    dom.openBtn.dispatch('click');

    assert.ok(!dom.overlay.classList.contains('hidden'), 'calendar should open');
    assert.ok(dom.monthLabel.textContent.length > 0, 'month label should be populated');
    assert.ok(dom.monthLabel.textContent.indexOf(String(today.getFullYear())) !== -1,
        'should default to the current year');
});

// --- 3/4: planned dates are highlighted, unplanned dates are not ------------------------------
run('A date present in the plan data is highlighted; one absent is not', () => {
    const today = new Date();
    const plannedDay = today.getDate() === 28 ? 27 : 28; // stays inside the current month either way
    const plannedIso = localIso(new Date(today.getFullYear(), today.getMonth(), plannedDay));
    const dom = buildDom([{ plannedLiveDate: plannedIso, channels: [{ channelHandle: 'kcpcbandhani', count: 2 }] }]);
    loadScriptAgainst(dom.document);

    dom.openBtn.dispatch('click');

    const planned = dom.grid.children.filter((c) => c.getAttribute('data-date') === plannedIso)[0];
    assert.ok(planned, 'the planned day cell should exist');
    assert.strictEqual(planned.tag, 'button', 'a planned date must be a real, interactive <button>');
    assert.ok(planned.classList.contains('kpi-calendar-cell-planned'));

    var otherDay = plannedDay === 28 ? 5 : 6;
    const unplannedIso = localIso(new Date(today.getFullYear(), today.getMonth(), otherDay));
    const unplanned = dom.grid.children.filter((c) => c.getAttribute('data-date') === unplannedIso)[0];
    assert.ok(unplanned, 'an unplanned day cell should still render');
    assert.strictEqual(unplanned.tag, 'span', 'an unplanned date must never be a <button> (non-interactive)');
    assert.ok(!unplanned.classList.contains('kpi-calendar-cell-planned'));
});

// --- 5/6: clicking a highlighted date shows its details, including multiple channels ----------
run('Clicking a highlighted date shows Channel/Account + Count, including multiple channels', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [
            { channelHandle: 'kcpcbandhani', count: 4 },
            { channelHandle: 'kcpc_sikar', count: 1 }
        ]
    }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');

    const cell = plannedCells(dom)[0];
    assert.ok(cell, 'sanity: exactly one planned cell exists');
    cell.dispatch('click');

    // The click handler re-renders the whole grid (fresh cell objects) - re-fetch by date rather
    // than reusing the pre-click reference, exactly as a real re-rendered DOM would require too.
    const reselected = dom.grid.children.filter((c) => c.getAttribute('data-date') === iso)[0];
    assert.ok(reselected.classList.contains('kpi-calendar-cell-selected'), 'clicked date should show the stronger selected state');
    assert.ok(dom.detail.innerHTML.indexOf('kcpcbandhani') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('4') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('kcpc_sikar') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('1') !== -1);
});

// --- Content Details: only rendered when the JSON data actually carries contentIds -------------
run('Clicking a date with contentIds shows an expandable Content Details section', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [
            { channelHandle: 'kcpcbandhani', count: 2, contentIds: ['C-0926-0001', 'C-0926-0002'] },
            { channelHandle: 'kcpc_sikar', count: 1, contentIds: ['C-0926-0003'] }
        ]
    }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');

    assert.ok(dom.detail.innerHTML.indexOf('Content Details (3)') !== -1,
        'total count across all channels should be shown in the summary');
    assert.ok(dom.detail.innerHTML.indexOf('C-0926-0001') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('C-0926-0002') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('C-0926-0003') !== -1);
});

// --- Cross-channel dedup: the one duplication the server CANNOT remove for us. Each channel's own
// contentIds is already distinct server-side (LinkedHashSet in upcomingChannelPlan()), but the same
// Content ID planned on two different Channel/Accounts legitimately appears once under each - the
// date-level Content Details summary must collapse those into one entry. ---------------------------
run('One Content ID on two channels renders once in Content Details, with both counts kept at 1', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [
            { channelHandle: 'kcpcbandhani', count: 1, contentIds: ['C-0926-0001'] },
            { channelHandle: 'kcpc_sikar', count: 1, contentIds: ['C-0926-0001'] }
        ]
    }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');

    assert.ok(dom.detail.innerHTML.indexOf('Content Details (1)') !== -1,
        'one distinct piece of content is planned that day, across both channels');
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, 'kpi-calendar-content-id'), 1,
        'the ID must appear exactly once in the rendered Content Details list');
    // Each channel's own Planned count is printed exactly as received - the date-level dedup above
    // must never reach back into the per-channel numbers.
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, '<span class="kpi-calendar-detail-count">1</span>'), 2,
        'both channels must still show their own count of 1');
});

// --- Defensive: a legacy/unmigrated payload that still carries duplicated contentIds within one
// channel (the retired mapping-row shape) must still render a unique list, and must still print the
// count exactly as received rather than silently "correcting" it. ---------------------------------
run('A legacy payload with duplicates inside one channel still renders uniquely, count untouched', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [{
            channelHandle: 'kcpcbandhani', count: 4,
            contentIds: ['C-0926-0001', 'C-0926-0001', 'C-0926-0001', 'C-0926-0001']
        }]
    }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');

    assert.ok(dom.detail.innerHTML.indexOf('Content Details (1)') !== -1,
        'summary count must be the UNIQUE count, not the raw 4');
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, 'kpi-calendar-content-id'), 1,
        'the ID must appear exactly once in the rendered Content Details list');
    // ch.count is rendered verbatim; this file never recomputes it.
    assert.ok(dom.detail.innerHTML.indexOf('<span class="kpi-calendar-detail-count">4</span>') !== -1,
        'Planned count must be printed as received - the JS never recomputes it');
});

run('Mixed duplicate/unique Content IDs across channels render only the unique ones', () => {
    const today = new Date();
    const iso = localIso(today);
    // C-0926-0002 is planned on BOTH channels - the realistic cross-channel duplicate.
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [
            { channelHandle: 'kcpcbandhani', count: 2, contentIds: ['C-0926-0001', 'C-0926-0002'] },
            { channelHandle: 'kcpc_sikar', count: 2, contentIds: ['C-0926-0002', 'C-0926-0003'] }
        ]
    }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');

    assert.ok(dom.detail.innerHTML.indexOf('Content Details (3)') !== -1);
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, 'C-0926-0001'), 1);
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, 'C-0926-0002'), 1,
        'the ID planned on both channels must still appear exactly once');
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, 'C-0926-0003'), 1);
    // Each channel's own Planned count is printed as received and never reduced by the dedup.
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, '<span class="kpi-calendar-detail-count">2</span>'), 2);
});

run('First-seen order is preserved after dedup, not alphabetical/sorted', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [
            { channelHandle: 'kcpcbandhani', count: 2, contentIds: ['C-0926-0003', 'C-0926-0001'] },
            { channelHandle: 'kcpc_sikar', count: 2, contentIds: ['C-0926-0003', 'C-0926-0002'] }
        ]
    }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');

    var html = dom.detail.innerHTML;
    var idx3 = html.indexOf('C-0926-0003');
    var idx1 = html.indexOf('C-0926-0001');
    var idx2 = html.indexOf('C-0926-0002');
    assert.ok(idx3 !== -1 && idx1 !== -1 && idx2 !== -1, 'sanity: all three unique IDs are present');
    assert.ok(idx3 < idx1 && idx1 < idx2,
        'order must be first-seen (0003, 0001, 0002), never alphabetical/sorted');
});

run('Content Details summary count always equals the number of unique <li> entries rendered', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [
            { channelHandle: 'kcpcbandhani', count: 3, contentIds: ['C-0926-0001', 'C-0926-0003', 'C-0926-0004'] },
            { channelHandle: 'kcpc_sikar', count: 2, contentIds: ['C-0926-0002', 'C-0926-0001'] }
        ]
    }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');

    // Per-channel totals sum to 5 (3 + 2), but C-0926-0001 is planned on both channels, so the
    // date-level unique count is 4 - dedup applies across the whole date's allContentIds. The
    // summary is deliberately allowed to be lower than the sum of the per-channel counts.
    assert.ok(dom.detail.innerHTML.indexOf('Content Details (4)') !== -1);
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, 'kpi-calendar-content-id'), 4);
    // Both channels' own Planned counts stay exactly as received.
    assert.ok(dom.detail.innerHTML.indexOf('<span class="kpi-calendar-detail-count">3</span>') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('<span class="kpi-calendar-detail-count">2</span>') !== -1);
});

run('The main Upcoming Channel Plan list (outside the calendar modal) is never touched', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [{ channelHandle: 'kcpcbandhani', count: 2, contentIds: ['C-0926-0001', 'C-0926-0002'] }]
    }]);
    // Simulate the separate, pre-existing list markup that lives outside the calendar modal -
    // reports-kpi-calendar.js must never read or write it.
    var mainList = new FakeElement('div');
    mainList.setAttribute('id', 'mainUpcomingPlanList');
    mainList.innerHTML = 'SENTINEL-UNCHANGED';
    dom.document.appendChild(mainList);

    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');
    dom.closeBtn.dispatch('click');

    assert.strictEqual(mainList.innerHTML, 'SENTINEL-UNCHANGED');
});

run('Empty/null contentIds mixed with real ones stays safe and dedups only what is present', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{
        plannedLiveDate: iso,
        channels: [
            { channelHandle: 'kcpcbandhani', count: 1, contentIds: ['C-0926-0001'] },
            { channelHandle: 'kcpc_sikar', count: 1, contentIds: [] },
            { channelHandle: 'kcpclegacy', count: 1 } // contentIds field entirely absent
        ]
    }]);
    loadScriptAgainst(dom.document);
    assert.doesNotThrow(() => {
        dom.openBtn.dispatch('click');
        plannedCells(dom)[0].dispatch('click');
    });

    assert.ok(dom.detail.innerHTML.indexOf('Content Details (1)') !== -1);
    assert.strictEqual(countOccurrences(dom.detail.innerHTML, 'kpi-calendar-content-id'), 1);
});

run('A date with no contentIds in the data shows no Content Details section', () => {
    const today = new Date();
    const iso = localIso(today);
    const dom = buildDom([{ plannedLiveDate: iso, channels: [{ channelHandle: 'kcpcbandhani', count: 2 }] }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    plannedCells(dom)[0].dispatch('click');

    assert.ok(dom.detail.innerHTML.indexOf('Content Details') === -1,
        'no Content Details section should render when the data has no contentIds at all');
});

// --- Clicking a second planned date replaces the first date's details, not appends to them -----
run('Clicking another planned date replaces the previously selected date\'s details', () => {
    const today = new Date();
    // 10th/20th are always valid, distinct dates in any month, regardless of what day "today" is.
    const isoA = localIso(new Date(today.getFullYear(), today.getMonth(), 10));
    const isoB = localIso(new Date(today.getFullYear(), today.getMonth(), 20));
    const dom = buildDom([
        { plannedLiveDate: isoA, channels: [{ channelHandle: 'channelA', count: 1 }] },
        { plannedLiveDate: isoB, channels: [{ channelHandle: 'channelB', count: 1 }] }
    ]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');

    dom.grid.children.filter((c) => c.getAttribute('data-date') === isoA)[0].dispatch('click');
    assert.ok(dom.detail.innerHTML.indexOf('channelA') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('channelB') === -1);

    dom.grid.children.filter((c) => c.getAttribute('data-date') === isoB)[0].dispatch('click');
    assert.ok(dom.detail.innerHTML.indexOf('channelB') !== -1);
    assert.ok(dom.detail.innerHTML.indexOf('channelA') === -1, 'the previous date\'s details must be replaced, not appended to');
});

// --- Previous-month navigation exercised standalone (not merely as Next's inverse) -------------
run('Previous month navigation, exercised on its own, moves back correctly and re-highlights', () => {
    const today = new Date();
    const prevMonth = new Date(today.getFullYear(), today.getMonth() - 1, 12);
    const iso = localIso(prevMonth);
    const dom = buildDom([{ plannedLiveDate: iso, channels: [{ channelHandle: 'kcpcbandhani', count: 1 }] }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');

    assert.strictEqual(
        dom.grid.children.filter((c) => c.getAttribute('data-date') === iso).length, 0,
        'sanity: not visible on the current month\'s grid'
    );

    dom.prevBtn.dispatch('click');

    assert.ok(dom.monthLabel.textContent.indexOf(String(prevMonth.getFullYear())) !== -1);
    const found = dom.grid.children.filter((c) => c.getAttribute('data-date') === iso)[0];
    assert.ok(found, 'the planned date should now be present on the previous month\'s grid');
    assert.ok(found.classList.contains('kpi-calendar-cell-planned'));
});

// --- 7: month navigation ------------------------------------------------------------------------
run('Next/previous month navigation updates the month label correctly', () => {
    const today = new Date();
    const dom = buildDom([]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');
    const startLabel = dom.monthLabel.textContent;

    dom.nextBtn.dispatch('click');
    const nextMonthDate = new Date(today.getFullYear(), today.getMonth() + 1, 1);
    assert.ok(dom.monthLabel.textContent.indexOf(String(nextMonthDate.getFullYear())) !== -1);
    assert.notStrictEqual(dom.monthLabel.textContent, startLabel, 'label should change after Next');

    dom.prevBtn.dispatch('click');
    assert.strictEqual(dom.monthLabel.textContent, startLabel, 'Previous should return to the original month');
});

// --- 8: a planned date in a different month is reachable and highlighted after navigating -----
run('A planned date in next month is reached and highlighted after clicking Next', () => {
    const today = new Date();
    const nextMonth = new Date(today.getFullYear(), today.getMonth() + 1, 10);
    const iso = localIso(nextMonth);
    const dom = buildDom([{ plannedLiveDate: iso, channels: [{ channelHandle: 'kcpcbandhani', count: 3 }] }]);
    loadScriptAgainst(dom.document);
    dom.openBtn.dispatch('click');

    assert.strictEqual(
        dom.grid.children.filter((c) => c.getAttribute('data-date') === iso).length, 0,
        'sanity: not visible on the current month\'s grid yet'
    );

    dom.nextBtn.dispatch('click');

    const found = dom.grid.children.filter((c) => c.getAttribute('data-date') === iso)[0];
    assert.ok(found, 'the planned date should now be present on next month\'s grid');
    assert.ok(found.classList.contains('kpi-calendar-cell-planned'));
});

// --- Closing: Close button, backdrop click, and Escape all close the calendar -----------------
run('Close button, backdrop click, and Escape all close the calendar', () => {
    const dom = buildDom([]);
    loadScriptAgainst(dom.document);

    dom.openBtn.dispatch('click');
    assert.ok(!dom.overlay.classList.contains('hidden'));
    dom.closeBtn.dispatch('click');
    assert.ok(dom.overlay.classList.contains('hidden'));

    dom.openBtn.dispatch('click');
    dom.overlay.dispatch('click'); // clicking the backdrop element itself
    assert.ok(dom.overlay.classList.contains('hidden'), 'backdrop click should close it');

    dom.openBtn.dispatch('click');
    // This shim's dispatch() only carries {type, target} - fire the keydown listeners directly
    // with a real 'key' field, which is what the script's Escape check actually reads.
    (dom.document.listeners.keydown || []).forEach((h) => h({ key: 'Escape' }));
    assert.ok(dom.overlay.classList.contains('hidden'), 'Escape should close it');
});

if (process.exitCode === 1) {
    console.error('\nSome reports-kpi-calendar.js tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll reports-kpi-calendar.js tests passed.');
}
