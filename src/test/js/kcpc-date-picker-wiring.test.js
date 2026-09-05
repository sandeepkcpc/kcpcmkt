'use strict';
/**
 * Integration test for kcpc-date-picker.js against a real (fake) DOM: the detail panel, the
 * highlight rendering, the empty state, and - most importantly - the ONE regression-critical
 * contract, that picking a date looks exactly like a native pick to everything already listening.
 *
 * Idea Review & Planning -> Schedule computes Shoot Date (Live - 5) and Edit Date (Live - 2) from a
 * DELEGATED `change` listener on #reviewsIdeaPlannedLiveDate (reviews-workspace.js's
 * `region.addEventListener('change', ...)` -> updateReviewsIdeaScheduleDefaults()). The picker never
 * calls that code and must never know it exists - it sets input.value and re-dispatches the
 * browser's own `input` + `change` events, bubbling. If that dispatch regressed, the auto-calculated
 * dates would silently stop updating, which is precisely what this file catches.
 *   node src/test/js/kcpc-date-picker-wiring.test.js
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
}

class FakeElement {
    constructor(tag) {
        this.tag = String(tag).toLowerCase();
        this.attrs = {};
        this.children = [];
        this.parent = null;
        this.listeners = {};
        this.classList = new FakeClassList();
        this.style = {};
        this.value = '';
        this.type = '';
        this._text = '';
        this._html = '';
        this.offsetWidth = 300;
        this.offsetHeight = 380;
    }
    setAttribute(n, v) { this.attrs[n] = String(v); }
    getAttribute(n) { return Object.prototype.hasOwnProperty.call(this.attrs, n) ? this.attrs[n] : null; }
    get id() { return this.attrs.id || ''; }
    set id(v) { this.attrs.id = v; }
    get className() { return Array.from(this.classList.set).join(' '); }
    set className(v) {
        this.classList = new FakeClassList();
        String(v).split(/\s+/).filter(Boolean).forEach((c) => this.classList.add(c));
    }
    get textContent() { return this._text; }
    set textContent(v) {
        this._text = String(v == null ? '' : v);
        // escapeHtml() writes text then reads innerHTML back - mirror that escaping.
        this._html = this._text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    get innerHTML() { return this._html; }
    set innerHTML(v) { this._html = v; if (v === '') { this.children = []; } }
    appendChild(c) { c.parent = this; this.children.push(c); return c; }
    removeChild(c) { this.children = this.children.filter((x) => x !== c); c.parent = null; return c; }
    get parentNode() { return this.parent; }
    addEventListener(t, h) { (this.listeners[t] = this.listeners[t] || []).push(h); }
    focus() {}
    getBoundingClientRect() { return {left: 100, top: 200, bottom: 224, right: 300, width: 200, height: 24}; }
    matches(sel) {
        if (sel.startsWith('#')) { return this.id === sel.slice(1); }
        if (sel.startsWith('.')) { return this.classList.contains(sel.slice(1)); }
        if (sel === 'input[type="date"][data-kcpc-calendar]') {
            return this.tag === 'input' && this.type === 'date' && this.getAttribute('data-kcpc-calendar') !== null;
        }
        if (sel === '.kcpc-datepicker-legend-item') { return this.classList.contains('kcpc-datepicker-legend-item'); }
        if (sel === 'button.kcpc-datepicker-cell') {
            return this.tag === 'button' && this.classList.contains('kcpc-datepicker-cell');
        }
        return false;
    }
    closest(sel) { let n = this; while (n) { if (n.matches && n.matches(sel)) { return n; } n = n.parent; } return null; }
    querySelector(sel) {
        let found = null;
        const walk = (n) => n.children.forEach((c) => {
            if (found) { return; }
            if (c.matches(sel)) { found = c; return; }
            walk(c);
        });
        walk(this);
        return found;
    }
    querySelectorAll(sel) {
        const out = [];
        const walk = (n) => n.children.forEach((c) => { if (c.matches(sel)) { out.push(c); } walk(c); });
        walk(this);
        return out;
    }
    /** Real bubbling: fire on the target, then every ancestor. */
    dispatchEvent(ev) {
        ev.target = ev.target || this;
        let n = this;
        while (n) {
            (n.listeners[ev.type] || []).forEach((h) => h(ev));
            if (!ev.bubbles) { break; }
            n = n.parent;
        }
        return true;
    }
}

const TODAY = (() => {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
})();
/** A date guaranteed to be in the current month and after today, or null near month-end. */
function futureInThisMonth(offset) {
    const d = new Date();
    const cand = new Date(d.getFullYear(), d.getMonth(), d.getDate() + offset);
    return cand.getMonth() === d.getMonth()
        ? cand.getFullYear() + '-' + String(cand.getMonth() + 1).padStart(2, '0') + '-' + String(cand.getDate()).padStart(2, '0')
        : null;
}

function buildEnv(planGroups, blocksById) {
    const document = new FakeElement('document');
    document.body = new FakeElement('body');
    document.appendChild(document.body);
    document.createElement = (tag) => new FakeElement(tag);

    const blocks = blocksById || (planGroups ? {kcpcPlannedLiveDatePlanData: planGroups} : {});
    Object.keys(blocks).forEach((id) => {
        const data = new FakeElement('script');
        data.id = id;
        data.textContent = JSON.stringify(blocks[id]);
        document.body.appendChild(data);
    });
    // getElementById over the whole tree.
    document.getElementById = (id) => {
        let found = null;
        const walk = (n) => n.children.forEach((c) => { if (found) { return; } if (c.id === id) { found = c; return; } walk(c); });
        walk(document);
        return found;
    };

    const sandbox = {
        document, console,
        Event: class { constructor(type, opts) { this.type = type; this.bubbles = !!(opts && opts.bubbles); } }
    };
    sandbox.window = sandbox;
    sandbox.window.addEventListener = () => {};
    sandbox.window.innerWidth = 1280;
    sandbox.window.innerHeight = 800;
    sandbox.window.Event = sandbox.Event;

    const src = fs.readFileSync(
        path.join(__dirname, '../../main/resources/static/js/kcpc-date-picker.js'), 'utf8');
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox);
    return document;
}

/** The Planning form's Schedule section, shaped like the real one: the field lives inside an
 *  AJAX-swappable region, and the auto-calculation listener is DELEGATED on that region. */
function buildPlanningForm(document) {
    const region = new FakeElement('div');
    region.id = 'reviewsIdeaPlanningFields';
    const label = new FakeElement('label');
    const liveDate = new FakeElement('input');
    liveDate.type = 'date';
    liveDate.id = 'reviewsIdeaPlannedLiveDate';
    liveDate.setAttribute('min', TODAY);
    liveDate.setAttribute('data-kcpc-calendar', 'live');
    label.appendChild(liveDate);
    region.appendChild(label);

    const shootDate = new FakeElement('input');
    shootDate.type = 'date';
    shootDate.id = 'reviewsIdeaShootDate';
    shootDate.setAttribute('min', TODAY);
    shootDate.setAttribute('data-kcpc-calendar', 'shoot');
    const editDate = new FakeElement('input');
    editDate.type = 'date';
    editDate.id = 'reviewsIdeaEditDate';
    editDate.setAttribute('min', TODAY);
    editDate.setAttribute('data-kcpc-calendar', 'edit');
    const l2 = new FakeElement('label'); l2.appendChild(shootDate); region.appendChild(l2);
    const l3 = new FakeElement('label'); l3.appendChild(editDate); region.appendChild(l3);

    document.body.appendChild(region);

    // Stand-in for reviews-workspace.js's updateReviewsIdeaScheduleDefaults(): the Standard-mode
    // rule the Planning screen already applies (Shoot = Live - 5, Edit = Live - 2).
    const calls = [], inputEvents = [];
    const shoot = {value: ''}, edit = {value: ''};
    const iso = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
        + '-' + String(d.getDate()).padStart(2, '0');
    region.addEventListener('change', (e) => {
        if (e.target.id !== 'reviewsIdeaPlannedLiveDate') { return; }
        calls.push(e.target.value);
        const live = new Date(e.target.value + 'T00:00:00');
        shoot.value = iso(new Date(live.getFullYear(), live.getMonth(), live.getDate() - 5));
        edit.value = iso(new Date(live.getFullYear(), live.getMonth(), live.getDate() - 2));
    });
    region.addEventListener('input', (e) => {
        if (e.target.id === 'reviewsIdeaPlannedLiveDate') { inputEvents.push(e.target.value); }
    });
    // reviews-workspace.js also re-validates on a MANUAL edit of Shoot/Edit Date (its `input`
    // handler on those two ids) - the picker must keep that firing too.
    const manualEdits = [];
    region.addEventListener('input', (e) => {
        if (e.target.id === 'reviewsIdeaShootDate' || e.target.id === 'reviewsIdeaEditDate') {
            manualEdits.push(e.target.id + '=' + e.target.value);
        }
    });
    return {region, liveDate, shootDate, editDate, shoot, edit, calls, inputEvents, manualEdits};
}

const fireDoc = (document, type, extra) =>
    (document.listeners[type] || []).forEach((h) => h(Object.assign({type, preventDefault() {}}, extra)));
const openOn = (document, field) => fireDoc(document, 'mousedown', {target: field});
const clickOn = (document, target) => fireDoc(document, 'click', {target});
const popover = (d) => d.body.children.filter((c) => c.id === 'kcpcDatePickerPopover')[0] || null;
const dayCells = (d) => {
    const p = popover(d);
    return p ? p.querySelectorAll('.kcpc-datepicker-cell').filter((c) => c.getAttribute('data-date')) : [];
};
const detailHtml = (d) => popover(d).querySelector('.kcpc-datepicker-detail').innerHTML;

function run(name, fn) {
    try { fn(); console.log('PASS - ' + name); }
    catch (e) { console.error('FAIL - ' + name); console.error(e.stack || e); process.exitCode = 1; }
}

const PLANNED_DAY = futureInThisMonth(3);
const PLAN_GROUPS = PLANNED_DAY ? [{
    plannedLiveDate: PLANNED_DAY,
    channels: [
        {channelHandle: 'kcpcbandhani', count: 4, contentIds: ['C-1', 'C-2', 'C-3', 'C-4']},
        {channelHandle: 'kcpcsikar', count: 2, contentIds: ['C-1', 'C-5']}
    ]
}] : [];

// --- opening + the past-date rule ----------------------------------------------------------------
run('Clicking the Planned Live Date field opens the calendar popover', () => {
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    assert.strictEqual(popover(d), null, 'sanity: starts closed');
    openOn(d, form.liveDate);
    assert.ok(popover(d), 'popover must be appended to the body');
    assert.ok(dayCells(d).length >= 28, 'a full month of day cells must render');
});

run('Past dates render as non-interactive spans; today and future as real buttons', () => {
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    const cells = dayCells(d);
    const past = cells.filter((c) => c.getAttribute('data-date') < TODAY);
    const todayCell = cells.filter((c) => c.getAttribute('data-date') === TODAY)[0];
    assert.strictEqual(todayCell.tag, 'button', 'today must be selectable');
    assert.ok(past.every((c) => c.tag === 'span'), 'every past date must be a non-interactive <span>');
    assert.ok(past.every((c) => c.classList.contains('kcpc-datepicker-cell-disabled')));
    assert.ok(cells.filter((c) => c.getAttribute('data-date') > TODAY).every((c) => c.tag === 'button'));
});

run('A disabled past date is not a button, so it can never be committed', () => {
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    const past = dayCells(d).filter((c) => c.getAttribute('data-date') < TODAY)[0];
    if (!past) { return; } // today is the 1st - nothing to assert
    clickOn(d, past);
    assert.strictEqual(form.liveDate.value, '', 'a past date must never reach the field');
    assert.strictEqual(form.calls.length, 0, 'no change event may fire for a rejected date');
});

// --- highlighting ---------------------------------------------------------------------------------
run('A future date with planned content is highlighted on the whole cell, with no dot markup', () => {
    if (!PLANNED_DAY) { return; }
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    const cell = dayCells(d).filter((c) => c.getAttribute('data-date') === PLANNED_DAY)[0];
    assert.ok(cell.classList.contains('kcpc-datepicker-cell-planned'),
        'the planned date must carry the whole-cell highlight class');
    assert.strictEqual(cell.children.length, 0,
        'a highlighted cell must contain no dot/indicator child element');
    const other = dayCells(d).filter((c) => c.getAttribute('data-date') > TODAY
        && c.getAttribute('data-date') !== PLANNED_DAY)[0];
    assert.ok(!other.classList.contains('kcpc-datepicker-cell-planned'),
        'a date with nothing planned must not be highlighted');
});

// --- detail panel ---------------------------------------------------------------------------------
run('Clicking a highlighted date shows Planned Content By Channel with the server counts', () => {
    if (!PLANNED_DAY) { return; }
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    clickOn(d, dayCells(d).filter((c) => c.getAttribute('data-date') === PLANNED_DAY)[0]);

    const html = detailHtml(d);
    assert.ok(html.indexOf('Planned Content By Channel') !== -1);
    assert.ok(html.indexOf('kcpcbandhani') !== -1);
    assert.ok(html.indexOf('>4<') !== -1, 'kcpcbandhani count 4 must be rendered verbatim');
    assert.ok(html.indexOf('kcpcsikar') !== -1);
    assert.ok(html.indexOf('>2<') !== -1, 'kcpcsikar count 2 must be rendered verbatim');
    // First click reviews, it must NOT silently commit the date.
    assert.strictEqual(form.liveDate.value, '', 'the first click on a planned date only opens the detail');
    assert.strictEqual(form.calls.length, 0);
});

run('The detail heading is formatted as "05 Sep 2026"', () => {
    if (!PLANNED_DAY) { return; }
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    clickOn(d, dayCells(d).filter((c) => c.getAttribute('data-date') === PLANNED_DAY)[0]);
    const parts = PLANNED_DAY.split('-');
    const shortMonth = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][parseInt(parts[1], 10) - 1];
    assert.ok(detailHtml(d).indexOf(parts[2] + ' ' + shortMonth + ' ' + parts[0]) !== -1);
});

run('Empty state: a selectable date with nothing planned says so', () => {
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    // Select a future date that has no planned content, via the field, then reopen to see detail.
    const free = dayCells(d).filter((c) => c.tag === 'button'
        && c.getAttribute('data-date') > TODAY && c.getAttribute('data-date') !== PLANNED_DAY)[0];
    clickOn(d, free);                       // nothing planned -> commits straight away
    assert.strictEqual(form.liveDate.value, free.getAttribute('data-date'));
    openOn(d, form.liveDate);               // reopen: the field's own date is now the detail date
    assert.ok(detailHtml(d).indexOf('No planned content available for this date.') !== -1,
        'the empty-state message must be shown for a date with nothing planned');
});

// --- THE contract: the delegated auto-calculation must still fire --------------------------------
run('Picking a date fires bubbling input+change, so Shoot/Edit auto-calculation still runs', () => {
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    const target = dayCells(d).filter((c) => c.tag === 'button'
        && c.getAttribute('data-date') > TODAY && c.getAttribute('data-date') !== PLANNED_DAY).pop();
    const iso = target.getAttribute('data-date');
    clickOn(d, target);

    assert.strictEqual(form.liveDate.value, iso, 'the real field value must be written');
    assert.deepStrictEqual(Array.from(form.calls), [iso],
        'the delegated change listener must fire exactly once, with the picked value');
    assert.deepStrictEqual(Array.from(form.inputEvents), [iso], 'input must fire too');

    const live = new Date(iso + 'T00:00:00');
    const expect = (delta) => {
        const x = new Date(live.getFullYear(), live.getMonth(), live.getDate() - delta);
        return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
    };
    assert.strictEqual(form.shoot.value, expect(5), 'Shoot Date = Live - 5 must still be computed');
    assert.strictEqual(form.edit.value, expect(2), 'Edit Date = Live - 2 must still be computed');
    assert.strictEqual(popover(d), null, 'the popover closes after a pick');
});

run('A second click on a reviewed planned date commits it, with auto-calculation intact', () => {
    if (!PLANNED_DAY) { return; }
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    const cellOf = () => dayCells(d).filter((c) => c.getAttribute('data-date') === PLANNED_DAY)[0];
    clickOn(d, cellOf());   // 1st: review
    clickOn(d, cellOf());   // 2nd: commit (grid re-rendered, so re-fetch the cell)
    assert.strictEqual(form.liveDate.value, PLANNED_DAY);
    assert.deepStrictEqual(Array.from(form.calls), [PLANNED_DAY]);
    assert.notStrictEqual(form.shoot.value, '', 'Shoot Date must still be auto-calculated');
});

// --- the field itself stays untouched ------------------------------------------------------------
run('The native field keeps its type, id and min - nothing about it is rewritten', () => {
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    assert.strictEqual(form.liveDate.type, 'date');
    assert.strictEqual(form.liveDate.id, 'reviewsIdeaPlannedLiveDate');
    assert.strictEqual(form.liveDate.getAttribute('min'), TODAY);
});

run('Missing plan data is not an error - the picker still works, with no highlights', () => {
    const d = buildEnv(null);   // no #kcpcPlannedLiveDatePlanData block at all
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    assert.ok(popover(d), 'the calendar must still open');
    assert.ok(dayCells(d).every((c) => !c.classList.contains('kcpc-datepicker-cell-planned')));
    const target = dayCells(d).filter((c) => c.tag === 'button' && c.getAttribute('data-date') > TODAY)[0];
    clickOn(d, target);
    assert.strictEqual(form.liveDate.value, target.getAttribute('data-date'), 'picking still works');
});

run('Escape closes the calendar without writing a value', () => {
    const d = buildEnv(PLAN_GROUPS);
    const form = buildPlanningForm(d);
    openOn(d, form.liveDate);
    fireDoc(d, 'keydown', {key: 'Escape'});
    assert.strictEqual(popover(d), null);
    assert.strictEqual(form.liveDate.value, '');
    assert.strictEqual(form.calls.length, 0);
});


// ================================================================== Shoot Date / Edit Date calendars
// Same component, same rules, different dataset and wording per field. The counts are never derived
// here - each calendar renders whatever UpcomingChannelPlanService grouped on that date column.

const SHOOT_DAY = futureInThisMonth(4);
const EDIT_DAY = futureInThisMonth(6);
const SHOOT_GROUPS = SHOOT_DAY ? [{
    plannedLiveDate: SHOOT_DAY,   // the DTO field name is shared; here it carries the Shoot Date
    channels: [
        {channelHandle: 'kcpcbandhani', count: 3, contentIds: ['C-1', 'C-2', 'C-3']},
        {channelHandle: 'kcpcsikar', count: 1, contentIds: ['C-1']}
    ]
}] : [];
const EDIT_GROUPS = EDIT_DAY ? [{
    plannedLiveDate: EDIT_DAY,
    channels: [
        {channelHandle: 'kcpcbandhani', count: 2, contentIds: ['C-4', 'C-5']},
        {channelHandle: 'kcpcsikar', count: 1, contentIds: ['C-4']}
    ]
}] : [];
const ALL_BLOCKS = {
    kcpcPlannedLiveDatePlanData: PLAN_GROUPS,
    kcpcPlannedShootPlanData: SHOOT_GROUPS,
    kcpcPlannedEditPlanData: EDIT_GROUPS
};

run('Shoot Date: future planned shoot dates are highlighted on the whole cell', () => {
    if (!SHOOT_DAY) { return; }
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.shootDate);
    const cell = dayCells(d).filter((c) => c.getAttribute('data-date') === SHOOT_DAY)[0];
    assert.ok(cell.classList.contains('kcpc-datepicker-cell-planned'),
        'a date with planned shoots must be highlighted');
    assert.strictEqual(cell.children.length, 0, 'no dot/indicator child element');
    // The LIVE calendar's own planned day must not leak into the Shoot calendar.
    if (PLANNED_DAY && PLANNED_DAY !== SHOOT_DAY) {
        const live = dayCells(d).filter((c) => c.getAttribute('data-date') === PLANNED_DAY)[0];
        assert.ok(!live.classList.contains('kcpc-datepicker-cell-planned'),
            'the Shoot calendar must read the shoot dataset, not the live one');
    }
});

run('Shoot Date: past dates are disabled and cannot be selected', () => {
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.shootDate);
    const past = dayCells(d).filter((c) => c.getAttribute('data-date') < TODAY);
    assert.ok(past.every((c) => c.tag === 'span' && c.classList.contains('kcpc-datepicker-cell-disabled')));
    if (past.length) {
        clickOn(d, past[0]);
        assert.strictEqual(form.shootDate.value, '', 'a past date must never reach the Shoot field');
    }
    assert.strictEqual(dayCells(d).filter((c) => c.getAttribute('data-date') === TODAY)[0].tag, 'button',
        'today must be selectable');
});

run('Shoot Date: detail panel shows "Planned Shoots by Channel" with the server counts', () => {
    if (!SHOOT_DAY) { return; }
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.shootDate);
    clickOn(d, dayCells(d).filter((c) => c.getAttribute('data-date') === SHOOT_DAY)[0]);
    const html = detailHtml(d);
    assert.ok(html.indexOf('Planned Shoots by Channel') !== -1);
    assert.ok(html.indexOf('kcpcbandhani') !== -1 && html.indexOf('>3<') !== -1,
        'kcpcbandhani = 3 (C-1 on Instagram+YouTube+Facebook counts once, server-side)');
    assert.ok(html.indexOf('kcpcsikar') !== -1 && html.indexOf('>1<') !== -1,
        'the same C-1 on a second channel counts separately, once');
});

run('Shoot Date: empty state says "No planned shoots available for this date."', () => {
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.shootDate);
    const free = dayCells(d).filter((c) => c.tag === 'button'
        && c.getAttribute('data-date') > TODAY && c.getAttribute('data-date') !== SHOOT_DAY)[0];
    clickOn(d, free);
    openOn(d, form.shootDate);
    assert.ok(detailHtml(d).indexOf('No planned shoots available for this date.') !== -1);
});

run('Edit Date: highlights its own dataset and says "Planned Edits by Channel"', () => {
    if (!EDIT_DAY) { return; }
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.editDate);
    const cell = dayCells(d).filter((c) => c.getAttribute('data-date') === EDIT_DAY)[0];
    assert.ok(cell.classList.contains('kcpc-datepicker-cell-planned'));
    clickOn(d, dayCells(d).filter((c) => c.getAttribute('data-date') === EDIT_DAY)[0]);
    const html = detailHtml(d);
    assert.ok(html.indexOf('Planned Edits by Channel') !== -1);
    assert.ok(html.indexOf('>2<') !== -1, 'kcpcbandhani = 2');
    if (SHOOT_DAY && SHOOT_DAY !== EDIT_DAY) {
        const shootCell = dayCells(d).filter((c) => c.getAttribute('data-date') === SHOOT_DAY)[0];
        assert.ok(!shootCell.classList.contains('kcpc-datepicker-cell-planned'),
            'the Edit calendar must not highlight shoot dates');
    }
});

run('Edit Date: empty state says "No planned edits available for this date."', () => {
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.editDate);
    const free = dayCells(d).filter((c) => c.tag === 'button'
        && c.getAttribute('data-date') > TODAY && c.getAttribute('data-date') !== EDIT_DAY)[0];
    clickOn(d, free);
    openOn(d, form.editDate);
    assert.ok(detailHtml(d).indexOf('No planned edits available for this date.') !== -1);
});

run('Each field opens its own calendar - the three datasets never cross', () => {
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    const plannedDatesFor = (field) => {
        openOn(d, field);
        const out = dayCells(d).filter((c) => c.classList.contains('kcpc-datepicker-cell-planned'))
            .map((c) => c.getAttribute('data-date'));
        fireDoc(d, 'keydown', {key: 'Escape'});
        return out;
    };
    const live = plannedDatesFor(form.liveDate);
    const shoot = plannedDatesFor(form.shootDate);
    const edit = plannedDatesFor(form.editDate);
    if (PLANNED_DAY) { assert.deepStrictEqual(Array.from(live), [PLANNED_DAY]); }
    if (SHOOT_DAY) { assert.deepStrictEqual(Array.from(shoot), [SHOOT_DAY]); }
    if (EDIT_DAY) { assert.deepStrictEqual(Array.from(edit), [EDIT_DAY]); }
});

run('The legend names all three states, with swatches rather than dots', () => {
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.shootDate);
    const items = popover(d).querySelectorAll('.kcpc-datepicker-legend-item');
    assert.strictEqual(items.length, 3, 'planned / no planned work / past date');
    const text = items.map((i) => i.children.map((c) => c.textContent).join('')).join('|');
    assert.ok(text.indexOf('Has planned shoots') !== -1, 'legend wording follows the field');
    assert.ok(text.indexOf('No planned work') !== -1);
    assert.ok(text.indexOf('Past date (not selectable)') !== -1);
    assert.strictEqual(popover(d).querySelectorAll('.kcpc-datepicker-legend-dot').length, 0,
        'no dot markup anywhere in the legend');
});

// --- requirement 5: the existing schedule logic must survive, in BOTH directions ----------------
run('Picking a Shoot Date fires input, so the manual-override re-validation still runs', () => {
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.shootDate);
    const target = dayCells(d).filter((c) => c.tag === 'button'
        && c.getAttribute('data-date') > TODAY && c.getAttribute('data-date') !== SHOOT_DAY)[0];
    const iso = target.getAttribute('data-date');
    clickOn(d, target);
    assert.strictEqual(form.shootDate.value, iso);
    assert.ok(form.manualEdits.indexOf('reviewsIdeaShootDate=' + iso) !== -1,
        'reviews-workspace.js re-validates a manually set Shoot Date from its `input` handler');
    assert.strictEqual(form.calls.length, 0,
        'picking a Shoot Date must NOT trigger the Live-Date-driven auto-calculation');
});

run('Picking an Edit Date never overwrites the Live or Shoot fields', () => {
    const d = buildEnv(null, ALL_BLOCKS);
    const form = buildPlanningForm(d);
    openOn(d, form.editDate);
    const target = dayCells(d).filter((c) => c.tag === 'button'
        && c.getAttribute('data-date') > TODAY && c.getAttribute('data-date') !== EDIT_DAY)[0];
    clickOn(d, target);
    assert.strictEqual(form.editDate.value, target.getAttribute('data-date'));
    assert.strictEqual(form.liveDate.value, '', 'Planned Live Date must be untouched');
    assert.strictEqual(form.shootDate.value, '', 'Shoot Date must be untouched');
});

if (process.exitCode) { console.error('\nkcpc-date-picker wiring tests FAILED.'); process.exit(1); }
console.log('\nAll kcpc-date-picker wiring tests passed.');
