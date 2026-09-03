'use strict';
/**
 * Dependency-free test for reports-admin-actions.js (Administrative Actions Date Range fix) - no
 * jsdom/npm package, mirroring this session's established Node `vm` harness pattern
 * (reports-kpi-date-preset.test.js). This file only carries the From > To validation gate on
 * submit; whether the request itself actually reaches the server with startDate/endDate is a
 * server-received-the-right-params concern, proven separately by
 * AdminActionsDateRangeFilterTest.java (a real HTTP round trip) - these tests only exercise the
 * PRESENTATION validation this file adds. Run with:
 *   node src/test/js/reports-admin-actions.test.js
 */
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
    matches(selector) {
        if (selector.charAt(0) === '#') { return this.id === selector.slice(1); }
        if (selector.charAt(0) === '.') { return this.classList.contains(selector.slice(1)); }
        return false;
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
    // Dispatches with bubbling, cancelable support (preventDefault/stopImmediatePropagation) -
    // enough to drive and observe the real submit-validation behavior under test.
    dispatch(type) {
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
    region.setAttribute('id', 'reportsAdminActionsDynamicRegion');
    document.appendChild(region);

    const form = new FakeElement('form');
    form.setAttribute('id', 'reportsAdminActionsFilterForm');
    region.appendChild(form);

    const fromField = new FakeElement('input');
    fromField.setAttribute('id', 'aaDateFrom');
    fromField.setAttribute('name', 'startDate');
    fromField.setAttribute('type', 'date');
    form.appendChild(fromField);

    const toField = new FakeElement('input');
    toField.setAttribute('id', 'aaDateTo');
    toField.setAttribute('name', 'endDate');
    toField.setAttribute('type', 'date');
    form.appendChild(toField);

    const errorBox = new FakeElement('span');
    errorBox.setAttribute('id', 'aaDateRangeError');
    errorBox.classList.add('hidden');
    form.appendChild(errorBox);

    const filterBtn = new FakeElement('button');
    filterBtn.setAttribute('id', 'aaFilterBtn');
    filterBtn.setAttribute('type', 'submit');
    form.appendChild(filterBtn);

    const clearLink = new FakeElement('a');
    clearLink.classList.add('reports-clear');
    form.appendChild(clearLink);

    return { document, region, form, fromField, toField, errorBox, filterBtn, clearLink };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'reports-admin-actions.js'), 'utf8');
    const sandbox = { document, console };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'reports-admin-actions.js' });
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

// --- 1/3/4: Filter button and the existing startDate/endDate field names are present -----------
run('Filter button exists, and From/To keep the existing startDate/endDate name attributes', () => {
    const dom = buildDom();
    assert.strictEqual(dom.filterBtn.getAttribute('type'), 'submit');
    assert.strictEqual(dom.fromField.getAttribute('name'), 'startDate');
    assert.strictEqual(dom.toField.getAttribute('name'), 'endDate');
});

// --- 2: a valid submit (Filter click) is never blocked by this file ----------------------------
run('Clicking Filter with valid dates does not block the form submit', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-08-01';
    dom.toField.value = '2026-08-31';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, false);
    assert.ok(dom.errorBox.classList.contains('hidden'));
});

// --- 5/6: From = To and From < To are both valid ------------------------------------------------
run('From = To is valid and does not block submission', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-09-01';
    dom.toField.value = '2026-09-01';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, false);
});

run('From < To is valid and does not block submission', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-08-01';
    dom.toField.value = '2026-08-31';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, false);
});

// --- 7: From > To blocks submission and shows a clear validation message -----------------------
run('From > To blocks submission and shows "From date cannot be after To date."', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-08-31';
    dom.toField.value = '2026-08-01';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, true);
    assert.ok(!dom.errorBox.classList.contains('hidden'));
    assert.strictEqual(dom.errorBox.textContent, 'From date cannot be after To date.');
});

run('Editing a date after an invalid Filter attempt clears the error', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-08-31';
    dom.toField.value = '2026-08-01';
    dom.form.dispatch('submit');
    assert.ok(!dom.errorBox.classList.contains('hidden'), 'sanity: error shown first');

    dom.toField.value = '2026-09-05';
    dom.toField.dispatch('input');
    assert.ok(dom.errorBox.classList.contains('hidden'), 'error should clear once a field is edited');
});

// --- 8/9: an open-ended range (one side blank) is allowed - the backend already supports it -----
run('Blank From (open-ended range) is allowed', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '';
    dom.toField.value = '2026-08-31';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, false);
});

run('Blank To (open-ended range) is allowed', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '2026-08-01';
    dom.toField.value = '';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, false);
});

run('Both From and To blank is allowed (no filter applied)', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    dom.fromField.value = '';
    dom.toField.value = '';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, false);
});

// --- 10: Clear behavior is untouched - this file attaches no click handling at all --------------
run('Clicking Clear is never intercepted by this file', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document);
    const result = dom.clearLink.dispatch('click');
    assert.strictEqual(result.defaultPrevented, false,
        'reports-admin-actions.js must not attach any click handling - Clear stays a plain navigation link');
});

if (process.exitCode === 1) {
    console.error('\nSome reports-admin-actions.js tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll reports-admin-actions.js tests passed.');
}
