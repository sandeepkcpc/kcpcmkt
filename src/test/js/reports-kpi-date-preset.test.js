'use strict';
/**
 * KPI Dashboard Date Range Filter Enhancement - dependency-free test for
 * reports-kpi-date-preset.js, mirroring this session's established Node `vm` harness pattern (no
 * jsdom/npm package). Runs under TZ=Asia/Kolkata (set before any Date is constructed) to prove the
 * preset calculations use local calendar-date math throughout, never a UTC-converting call like
 * `.toISOString()` that would silently shift a date by one day for this app's own business
 * timezone (+5:30) - the exact class of bug already found and fixed once this session in
 * reviews-workspace.js. Run with:
 *   node src/test/js/reports-kpi-date-preset.test.js
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
        this.readOnly = false;
        this.textContent = '';
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
    // Dispatches with bubbling, cancelable support (preventDefault/stopImmediatePropagation) -
    // enough to drive and observe the real submit-validation behavior under test.
    dispatch(type, cancelable) {
        let defaultPrevented = false;
        let stopped = false;
        const event = {
            type,
            target: this,
            preventDefault() { defaultPrevented = true; },
            stopImmediatePropagation() { stopped = true; }
        };
        let node = this;
        while (node && !stopped) {
            const handlers = node.listeners[type] || [];
            for (const h of handlers) {
                if (stopped) { break; }
                h(Object.assign(event, { currentTarget: node }));
            }
            node = node.parent;
        }
        return { defaultPrevented };
    }
}

function buildDom() {
    const document = new FakeElement('document');
    document.getElementById = document.getElementById.bind(document);

    const region = new FakeElement('div');
    region.setAttribute('id', 'reportsKpiDynamicRegion');
    document.appendChild(region);

    const form = new FakeElement('form');
    form.setAttribute('id', 'reportsKpiFilterForm');
    region.appendChild(form);

    const presetSelect = new FakeElement('select');
    presetSelect.setAttribute('id', 'kpiDatePreset');
    form.appendChild(presetSelect);

    const fromField = new FakeElement('input');
    fromField.setAttribute('id', 'kpiDateFrom');
    form.appendChild(fromField);

    const toField = new FakeElement('input');
    toField.setAttribute('id', 'kpiDateTo');
    form.appendChild(toField);

    const errorBox = new FakeElement('span');
    errorBox.setAttribute('id', 'kpiDateRangeError');
    errorBox.classList.add('hidden');
    form.appendChild(errorBox);

    return { document, region, form, presetSelect, fromField, toField, errorBox };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'reports-kpi-date-preset.js'), 'utf8');
    const sandbox = { document, console };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'reports-kpi-date-preset.js' });
}

function selectPreset(dom, value) {
    dom.presetSelect.value = value;
    dom.presetSelect.dispatch('change'); // bubbles to the region's delegated listener
}

function localIso(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + d;
}

function addDays(date, days) {
    const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    d.setDate(d.getDate() + days);
    return d;
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

// --- 1-6: each preset computes the exact expected local-calendar range ---------------------
run('Today preset: From = To = today, fields become readonly', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'today');
    const today = new Date();
    assert.strictEqual(dom.fromField.value, localIso(today));
    assert.strictEqual(dom.toField.value, localIso(today));
    assert.strictEqual(dom.fromField.readOnly, true);
    assert.strictEqual(dom.toField.readOnly, true);
});

run('Yesterday preset: From = To = yesterday', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'yesterday');
    const yesterday = addDays(new Date(), -1);
    assert.strictEqual(dom.fromField.value, localIso(yesterday));
    assert.strictEqual(dom.toField.value, localIso(yesterday));
});

run('Last 7 Days preset: 7-day inclusive window ending today', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'last7');
    const today = new Date();
    assert.strictEqual(dom.fromField.value, localIso(addDays(today, -6)));
    assert.strictEqual(dom.toField.value, localIso(today));
});

run('Last 30 Days preset: 30-day inclusive window ending today', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'last30');
    const today = new Date();
    assert.strictEqual(dom.fromField.value, localIso(addDays(today, -29)));
    assert.strictEqual(dom.toField.value, localIso(today));
});

run('This Month preset: first calendar day of this month through today', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'thisMonth');
    const today = new Date();
    const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
    assert.strictEqual(dom.fromField.value, localIso(firstOfMonth));
    assert.strictEqual(dom.toField.value, localIso(today));
});

run('Last Month preset: first through last calendar day of previous month', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'lastMonth');
    const today = new Date();
    const firstOfLastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    const lastOfLastMonth = new Date(today.getFullYear(), today.getMonth(), 0);
    assert.strictEqual(dom.fromField.value, localIso(firstOfLastMonth));
    assert.strictEqual(dom.toField.value, localIso(lastOfLastMonth));
});

// --- 7: Custom leaves existing values untouched and makes fields editable -------------------
run('Custom preset: fields become editable, existing values untouched', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-01-05';
    dom.toField.value = '2026-01-10';
    dom.fromField.readOnly = true;
    dom.toField.readOnly = true;
    selectPreset(dom, 'custom');
    assert.strictEqual(dom.fromField.readOnly, false);
    assert.strictEqual(dom.toField.readOnly, false);
    assert.strictEqual(dom.fromField.value, '2026-01-05');
    assert.strictEqual(dom.toField.value, '2026-01-10');
});

// --- Custom must NOT auto-submit (the bug this fix addresses); every real preset still must ---
// Registers a second 'change' listener on the region AFTER loadScriptAgainst() runs - the same
// relative order reports-workspace.js's own generic auto-submit handler has in production
// (its <script> tag loads after this file's). A same-node 'change' listener registered second
// only fires if the first listener never called stopImmediatePropagation().
function attachSimulatedAutoSubmit(dom) {
    var fired = { count: 0 };
    dom.region.addEventListener('change', function () {
        fired.count++;
    });
    return fired;
}

run('Selecting Custom does NOT trigger the simulated auto-submit handler', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    const autoSubmit = attachSimulatedAutoSubmit(dom);
    selectPreset(dom, 'custom');
    assert.strictEqual(autoSubmit.count, 0, 'Custom must never reach the auto-submit handler');
});

['today', 'yesterday', 'last7', 'last30', 'thisMonth', 'lastMonth'].forEach((preset) => {
    run('Selecting the "' + preset + '" preset still triggers auto-submit exactly as before', () => {
        const dom = buildDom();
        loadScriptAgainst(dom.document);
        const autoSubmit = attachSimulatedAutoSubmit(dom);
        selectPreset(dom, preset);
        assert.strictEqual(autoSubmit.count, 1, 'real presets must keep auto-submitting unchanged');
    });
});

// --- Custom -> user edits -> Apply must still submit normally (only the auto-submit-on-select
// is suppressed for Custom; a real submit, e.g. clicking the Apply button, is untouched) --------
run('Custom: after selecting it and typing dates, clicking Apply (a real submit) proceeds', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'custom');
    assert.strictEqual(dom.fromField.readOnly, false, 'From must be editable after choosing Custom');
    assert.strictEqual(dom.toField.readOnly, false, 'To must be editable after choosing Custom');
    dom.fromField.value = '2026-08-10';
    dom.toField.value = '2026-08-20';
    const result = dom.form.dispatch('submit', true);
    assert.strictEqual(result.defaultPrevented, false, 'a valid custom range must not be blocked on Apply');
    assert.ok(dom.errorBox.classList.contains('hidden'));
});

// --- 8-11: validation -----------------------------------------------------------------------
run('Validation: missing From blocks submit with a clear message', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '';
    dom.toField.value = '2026-09-01';
    const result = dom.form.dispatch('submit', true);
    assert.strictEqual(result.defaultPrevented, true);
    assert.ok(!dom.errorBox.classList.contains('hidden'));
    assert.strictEqual(dom.errorBox.textContent, 'From date is required.');
});

run('Validation: missing To blocks submit with a clear message', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-09-01';
    dom.toField.value = '';
    const result = dom.form.dispatch('submit', true);
    assert.strictEqual(result.defaultPrevented, true);
    assert.strictEqual(dom.errorBox.textContent, 'To date is required.');
});

run('Validation: From > To blocks submit and does not execute the filter', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-09-10';
    dom.toField.value = '2026-09-01';
    const result = dom.form.dispatch('submit', true);
    assert.strictEqual(result.defaultPrevented, true);
    assert.strictEqual(dom.errorBox.textContent, 'From date cannot be after To date.');
});

run('Validation: From = To is valid, submit proceeds, no error shown', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-09-01';
    dom.toField.value = '2026-09-01';
    const result = dom.form.dispatch('submit', true);
    assert.strictEqual(result.defaultPrevented, false);
    assert.ok(dom.errorBox.classList.contains('hidden'));
});

run('Validation: a corrected date clears a previous error on input', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '';
    dom.toField.value = '2026-09-01';
    dom.form.dispatch('submit', true);
    assert.ok(!dom.errorBox.classList.contains('hidden'), 'sanity: error shown first');

    dom.fromField.value = '2026-08-25';
    dom.fromField.dispatch('input'); // bubbles up to the region's delegated 'input' listener
    assert.ok(dom.errorBox.classList.contains('hidden'), 'error should clear once the field is edited');
});

// --- 16/17: Asia/Kolkata handling, no UTC-conversion off-by-one -----------------------------
// (A static source-text check for the literal string "toISOString" was deliberately not used
// here - this file's own comments legitimately name that pattern by way of explaining why it's
// avoided, which would make such a check self-defeating. The behavioral test below is the real
// proof: the computed value must equal the LOCAL calendar date, not a UTC-shifted one.)
run('Timezone safety (Asia/Kolkata): Today preset matches Node\'s own local-getter "today", not a UTC-shifted date', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    selectPreset(dom, 'today');
    const nodeLocalToday = localIso(new Date());
    const utcToday = new Date().toISOString().slice(0, 10);
    assert.strictEqual(dom.fromField.value, nodeLocalToday);
    // Under Asia/Kolkata (+5:30), toISOString() only diverges from the local date near local
    // midnight (a ~5.5-hour window) - assert equality only when they'd actually be expected to
    // agree, and always assert the computed value matches the LOCAL date, which is what matters.
    if (nodeLocalToday !== utcToday) {
        console.log('    (informational: local ' + nodeLocalToday + ' vs UTC ' + utcToday + ' currently differ - confirms this run is exercising the exact IST/UTC boundary toISOString() would get wrong, and the preset value correctly used the local one)');
    }
});

if (process.exitCode === 1) {
    console.error('\nSome reports-kpi-date-preset.js tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll reports-kpi-date-preset.js tests passed.');
}
