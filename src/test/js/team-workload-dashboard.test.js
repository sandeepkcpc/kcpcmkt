'use strict';
/**
 * Dependency-free test for team-workload-dashboard.js (TEAM -> WORKLOAD date-handling +
 * employee-wise UI update, spec Sections 15/19) - no jsdom/npm, mirroring this session's
 * established Node `vm` harness pattern (reports-admin-actions.test.js). Covers only the two
 * pieces of real logic this file owns: (1) the From > To date-range validation gate that blocks
 * both the explicit Filter submit and the auto-submit-on-change paths, and (2) the "Open" button's
 * single-panel-at-a-time stage-wise breakdown toggle (spec Section 10). The actual AJAX
 * fetch/response-swap plumbing is identical to pipeline-dashboard.js's already-established pattern
 * and is not re-tested here; whether the server-received query params are correct is proven
 * separately by TeamWorkloadDateHandlingTest.java (a real HTTP round trip).
 * Run with: node src/test/js/team-workload-dashboard.test.js
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
    querySelectorAll(selector) {
        const results = [];
        const walk = (node) => {
            node.children.forEach((child) => {
                if (child.matches && child.matches(selector)) { results.push(child); }
                walk(child);
            });
        };
        walk(this);
        return results;
    }
    querySelector(selector) {
        const all = this.querySelectorAll(selector);
        return all.length ? all[0] : null;
    }
    // Dispatches with bubbling, cancelable support (preventDefault) - enough to drive and observe
    // the real submit/change/click/input validation and toggle behavior under test.
    dispatch(type) {
        let defaultPrevented = false;
        const event = {
            type,
            target: this,
            preventDefault() { defaultPrevented = true; }
        };
        let node = this;
        while (node) {
            const handlers = node.listeners[type] || [];
            for (const h of handlers) {
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
    region.setAttribute('id', 'teamWorkloadDynamicRegion');
    document.appendChild(region);

    const form = new FakeElement('form');
    form.setAttribute('id', 'teamWorkloadFilterForm');
    region.appendChild(form);

    const businessRoleSelect = new FakeElement('select');
    businessRoleSelect.setAttribute('id', 'twBusinessRole');
    businessRoleSelect.setAttribute('name', 'businessRole');
    form.appendChild(businessRoleSelect);

    const employeeSelect = new FakeElement('select');
    employeeSelect.setAttribute('id', 'twEmployee');
    employeeSelect.setAttribute('name', 'employeeId');
    form.appendChild(employeeSelect);

    const fromField = new FakeElement('input');
    fromField.setAttribute('id', 'twDateFrom');
    fromField.setAttribute('name', 'dateFrom');
    fromField.setAttribute('type', 'date');
    form.appendChild(fromField);

    const toField = new FakeElement('input');
    toField.setAttribute('id', 'twDateTo');
    toField.setAttribute('name', 'dateTo');
    toField.setAttribute('type', 'date');
    form.appendChild(toField);

    const errorBox = new FakeElement('span');
    errorBox.setAttribute('id', 'twDateRangeError');
    errorBox.classList.add('hidden');
    form.appendChild(errorBox);

    const filterBtn = new FakeElement('button');
    filterBtn.setAttribute('id', 'twFilterBtn');
    filterBtn.setAttribute('type', 'submit');
    form.appendChild(filterBtn);

    const clearLink = new FakeElement('a');
    clearLink.classList.add('team-workload-clear');
    clearLink.setAttribute('href', '/app/reports/workload');
    region.appendChild(clearLink);

    return { document, region, form, businessRoleSelect, employeeSelect, fromField, toField, errorBox, filterBtn, clearLink };
}

function addOpenBreakdownPair(region, employeeId) {
    const openBtn = new FakeElement('button');
    openBtn.classList.add('team-workload-open-btn');
    openBtn.setAttribute('data-employee-id', employeeId);
    openBtn.setAttribute('aria-expanded', 'false');
    region.appendChild(openBtn);

    const panel = new FakeElement('div');
    panel.classList.add('team-workload-breakdown');
    panel.classList.add('hidden');
    panel.setAttribute('id', 'workloadBreakdown-' + employeeId);
    panel.scrollIntoView = function () { /* no-op in this harness */ };
    region.appendChild(panel);

    return { openBtn, panel };
}

function loadScriptAgainst(document, window, history, fetchStub) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'team-workload-dashboard.js'), 'utf8');
    const sandbox = {
        document, window, history, console,
        fetch: fetchStub,
        AbortController,
        URLSearchParams,
        FormData: function () { this.entries = []; }
    };
    sandbox.window.setTimeout = setTimeout;
    sandbox.window.clearTimeout = clearTimeout;
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'team-workload-dashboard.js' });
    return sandbox;
}

function buildWindow() {
    return {
        addEventListener() { /* resize/popstate listeners - not exercised here */ },
        matchMedia() { return { matches: false }; },
        location: { search: '', href: 'http://localhost/app/reports/workload' }
    };
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

// --- From/To validation gate on the explicit Filter submit (spec Section 15) -------------------
// This form is AJAX-driven (like pipeline-dashboard.js), so event.preventDefault() is called
// unconditionally on every submit of this form - it never falls through to a real page navigation
// either way. The actual pass/fail signal for "was the range accepted" is whether the AJAX
// fetch fired, which is what these tests spy on via a fake fetch instead of defaultPrevented.
run('Clicking Filter with a valid range (From < To) proceeds to fetch, with no error shown', () => {
    const dom = buildDom();
    let fetchCalls = 0;
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => { fetchCalls++; return new Promise(() => {}); });
    dom.fromField.value = '2026-08-01';
    dom.toField.value = '2026-08-31';
    dom.form.dispatch('submit');
    assert.strictEqual(fetchCalls, 1);
    assert.ok(dom.errorBox.classList.contains('hidden'));
});

run('From = To (a single calendar day) is explicitly valid and proceeds to fetch', () => {
    const dom = buildDom();
    let fetchCalls = 0;
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => { fetchCalls++; return new Promise(() => {}); });
    dom.fromField.value = '2026-09-01';
    dom.toField.value = '2026-09-01';
    dom.form.dispatch('submit');
    assert.strictEqual(fetchCalls, 1);
});

run('From > To blocks the submit (no fetch fires) and shows a clear validation message, never a silent empty result', () => {
    const dom = buildDom();
    let fetchCalls = 0;
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => { fetchCalls++; return new Promise(() => {}); });
    dom.fromField.value = '2026-08-31';
    dom.toField.value = '2026-08-01';
    const result = dom.form.dispatch('submit');
    assert.strictEqual(result.defaultPrevented, true);
    assert.strictEqual(fetchCalls, 0, 'an inverted range must never reach the network');
    assert.ok(!dom.errorBox.classList.contains('hidden'));
    assert.strictEqual(dom.errorBox.textContent, 'From date cannot be after To date.');
});

run('Either side left blank (open-ended range) is valid and proceeds to fetch', () => {
    const dom = buildDom();
    let fetchCalls = 0;
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => { fetchCalls++; return new Promise(() => {}); });
    dom.fromField.value = '';
    dom.toField.value = '2026-08-31';
    dom.form.dispatch('submit');
    assert.strictEqual(fetchCalls, 1);
    dom.fromField.value = '2026-08-01';
    dom.toField.value = '';
    dom.form.dispatch('submit');
    assert.strictEqual(fetchCalls, 2);
});

run('Editing a date field after an invalid Filter attempt clears the error live', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => new Promise(() => {}));
    dom.fromField.value = '2026-08-31';
    dom.toField.value = '2026-08-01';
    dom.form.dispatch('submit');
    assert.ok(!dom.errorBox.classList.contains('hidden'), 'sanity: error shown first');

    dom.toField.value = '2026-09-05';
    dom.toField.dispatch('input');
    assert.ok(dom.errorBox.classList.contains('hidden'), 'error should clear once a field is edited');
});

// --- The same gate also guards the auto-submit-on-change path (Business Role/Employee/Stage/
// Delayed Only/date fields all re-submit on change without a separate Filter click) - spec
// Section 15's "must all work together", never bypassable by changing a filter instead of clicking
// Filter. Proven here by spying on fetch: an invalid range must never even reach the network. -----
run('Changing a date field into an invalid range never triggers a fetch (auto-submit is gated too)', () => {
    const dom = buildDom();
    let fetchCalls = 0;
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => { fetchCalls++; return new Promise(() => {}); });
    dom.fromField.value = '2026-08-31';
    dom.toField.value = '2026-08-01';
    dom.toField.dispatch('change');
    assert.strictEqual(fetchCalls, 0, 'an inverted range must never auto-submit');
    assert.ok(!dom.errorBox.classList.contains('hidden'));
});

run('Changing a filter field with a valid range does trigger a fetch (existing auto-submit behavior preserved)', () => {
    const dom = buildDom();
    let fetchCalls = 0;
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => { fetchCalls++; return new Promise(() => {}); });
    dom.fromField.value = '2026-08-01';
    dom.toField.value = '2026-08-31';
    dom.toField.dispatch('change');
    assert.strictEqual(fetchCalls, 1);
});

// --- "Open" stage-wise breakdown toggle (spec Section 10): at most one panel visible at a time --
run('Clicking Open expands that employee\'s breakdown panel and marks aria-expanded=true', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => new Promise(() => {}));
    const emp1 = addOpenBreakdownPair(dom.region, 'emp-1');

    emp1.openBtn.dispatch('click');

    assert.ok(!emp1.panel.classList.contains('hidden'));
    assert.strictEqual(emp1.openBtn.getAttribute('aria-expanded'), 'true');
});

run('Opening a second employee\'s breakdown closes the first (only one panel open at a time)', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => new Promise(() => {}));
    const emp1 = addOpenBreakdownPair(dom.region, 'emp-1');
    const emp2 = addOpenBreakdownPair(dom.region, 'emp-2');

    emp1.openBtn.dispatch('click');
    emp2.openBtn.dispatch('click');

    assert.ok(emp1.panel.classList.contains('hidden'), 'first panel must close when a second is opened');
    assert.strictEqual(emp1.openBtn.getAttribute('aria-expanded'), 'false');
    assert.ok(!emp2.panel.classList.contains('hidden'));
    assert.strictEqual(emp2.openBtn.getAttribute('aria-expanded'), 'true');
});

run('Clicking Open on an already-open employee closes it (toggle, not a one-way switch)', () => {
    const dom = buildDom();
    loadScriptAgainst(dom.document, buildWindow(), { pushState() {} }, () => new Promise(() => {}));
    const emp1 = addOpenBreakdownPair(dom.region, 'emp-1');

    emp1.openBtn.dispatch('click');
    assert.ok(!emp1.panel.classList.contains('hidden'), 'sanity: opened first');
    emp1.openBtn.dispatch('click');

    assert.ok(emp1.panel.classList.contains('hidden'), 'a second click on the same Open button must close it');
    assert.strictEqual(emp1.openBtn.getAttribute('aria-expanded'), 'false');
});

if (process.exitCode) {
    process.exit(process.exitCode);
}
