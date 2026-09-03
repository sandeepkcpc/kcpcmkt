'use strict';
/**
 * Dependency-free test for reviews-workspace.js's Ideas-tab Approve Schedule inline validation -
 * no jsdom/npm package, just enough of a DOM shim (id lookup, bubbling input/change events, a
 * tiny descendant-selector matcher) to run the REAL script file under Node's built-in `vm` module,
 * mirroring my-work-tabs.test.js's established harness pattern. Run with:
 *   node src/test/js/reviews-workspace-schedule-validation.test.js
 *
 * Covers the fix: typing a date directly into the Shoot/Edit Date field (overriding the
 * Live-Date-calculated default) must show/clear the inline "before today" error and toggle the
 * Confirm button immediately, not only when the Planned Live Date itself changes. Rule under test
 * (matches IdeaService#approve's own Standard-mode guard): date < today = error, date >= today = valid,
 * only for whichever of Shoot/Edit is actually part of the currently-selected Stages, and never for
 * Urgent Planning Mode (that guard is Standard-mode only, by design).
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

function parseCompound(str) {
    const compound = { tag: null, id: null, classes: [], attrs: {}, checked: false };
    const re = /(#[\w-]+)|(\.[\w-]+)|(\[[^\]]+\])|(:checked)|([\w-]+)/g;
    let m;
    while ((m = re.exec(str))) {
        if (m[1]) { compound.id = m[1].slice(1); }
        else if (m[2]) { compound.classes.push(m[2].slice(1)); }
        else if (m[3]) {
            const inner = m[3].slice(1, -1);
            const eq = inner.indexOf('=');
            if (eq === -1) {
                compound.attrs[inner] = true;
            } else {
                const name = inner.slice(0, eq).trim();
                const val = inner.slice(eq + 1).trim().replace(/^"(.*)"$/, '$1').replace(/^'(.*)'$/, '$1');
                compound.attrs[name] = val;
            }
        } else if (m[4]) { compound.checked = true; }
        else if (m[5]) { compound.tag = m[5]; }
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
    if (c.checked && !el.checked) { return false; }
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
        this.checked = false;
        this.disabled = false;
        this.textContent = '';
        this.dataset = {};
    }
    setAttribute(name, value) {
        this.attrs[name] = value;
        if (name === 'id') { this.id = value; }
    }
    getAttribute(name) {
        if (name === 'id') { return this.id || null; }
        return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null;
    }
    appendChild(child) { child.parent = this; this.children.push(child); return child; }
    className(...classes) { classes.forEach((c) => this.classList.add(c)); return this; }
    addEventListener(type, handler) { (this.listeners[type] = this.listeners[type] || []).push(handler); }
    dispatch(type) {
        const event = { type, target: this };
        let node = this;
        while (node) {
            (node.listeners[type] || []).forEach((h) => h(Object.assign({}, event, { currentTarget: node })));
            node = node.parent;
        }
    }
    click() { this.dispatch('click'); }
    input() { this.dispatch('input'); }
    change() { this.dispatch('change'); }
    matches(selector) { return matchesCompound(this, parseCompound(selector)); }
    querySelectorAll(selector) {
        const compounds = selector.trim().split(/\s+/).map(parseCompound);
        const last = compounds[compounds.length - 1];
        const out = [];
        const walk = (node) => {
            node.children.forEach((child) => {
                if (matchesCompound(child, last)) {
                    let ok = true;
                    let anc = child.parent;
                    let idx = compounds.length - 2;
                    while (idx >= 0) {
                        if (!anc) { ok = false; break; }
                        if (matchesCompound(anc, compounds[idx])) { idx--; }
                        anc = anc.parent;
                    }
                    if (ok && idx < 0) { out.push(child); }
                }
                walk(child);
            });
        };
        walk(this);
        return out;
    }
    querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
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
}

function isoDateOffset(days) {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function buildScheduleDom(stages, planningMode) {
    const document = new FakeElement('document');
    document.getElementById = document.getElementById.bind(document);
    document.addEventListener = FakeElement.prototype.addEventListener.bind(document);

    const region = new FakeElement('div');
    region.setAttribute('id', 'reviewsDynamicRegion');
    document.appendChild(region);

    const planningModeSelect = new FakeElement('select');
    planningModeSelect.setAttribute('id', 'reviewsIdeaPlanningMode');
    planningModeSelect.value = planningMode || 'STANDARD';
    region.appendChild(planningModeSelect);

    const shootDate = new FakeElement('input');
    shootDate.setAttribute('id', 'reviewsIdeaShootDate');
    region.appendChild(shootDate);
    const shootDateError = new FakeElement('span');
    shootDateError.setAttribute('id', 'reviewsIdeaShootDateError');
    shootDateError.classList.add('hidden');
    region.appendChild(shootDateError);

    const editDate = new FakeElement('input');
    editDate.setAttribute('id', 'reviewsIdeaEditDate');
    region.appendChild(editDate);
    const editDateError = new FakeElement('span');
    editDateError.setAttribute('id', 'reviewsIdeaEditDateError');
    editDateError.classList.add('hidden');
    region.appendChild(editDateError);

    const confirmBtn = new FakeElement('button');
    confirmBtn.setAttribute('id', 'reviewsIdeaConfirmBtn');
    region.appendChild(confirmBtn);

    const stagesPicker = new FakeElement('div');
    stagesPicker.setAttribute('id', 'reviewsIdeaStagesPicker');
    region.appendChild(stagesPicker);
    ['SHOOT', 'EDIT', 'PUBLISHING'].forEach((stage) => {
        const cb = new FakeElement('input');
        cb.setAttribute('type', 'checkbox');
        cb.value = stage;
        cb.checked = stages.indexOf(stage) !== -1;
        stagesPicker.appendChild(cb);
    });

    return { document, region, planningModeSelect, shootDate, shootDateError, editDate, editDateError, confirmBtn };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'reviews-workspace.js'), 'utf8');
    const sandbox = {
        document,
        console,
        window: { addEventListener() {}, location: { href: '' } }
    };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'reviews-workspace.js' });
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

// Direct Edit (Edit+Publishing, Shoot skipped): typing a past Edit Date directly into the field
// (not via Live Date recalculation) must show the inline error immediately.
run('Edit+Publishing, Standard: typing a past Edit Date shows the inline error and disables Confirm', () => {
    const dom = buildScheduleDom(['EDIT', 'PUBLISHING'], 'STANDARD');
    loadScriptAgainst(dom.document);

    dom.editDate.value = isoDateOffset(-1);
    dom.editDate.input();

    assert.ok(!dom.editDateError.classList.contains('hidden'), 'Edit Date error should be visible');
    assert.ok(dom.editDateError.textContent.length > 0, 'Edit Date error should have a message');
    assert.ok(dom.confirmBtn.disabled, 'Confirm button should be disabled');
});

run('Edit+Publishing, Standard: Edit Date = today is valid (no error, Confirm enabled)', () => {
    const dom = buildScheduleDom(['EDIT', 'PUBLISHING'], 'STANDARD');
    loadScriptAgainst(dom.document);

    dom.editDate.value = isoDateOffset(0);
    dom.editDate.input();

    assert.ok(dom.editDateError.classList.contains('hidden'), 'Edit Date error should be hidden for today');
    assert.ok(!dom.confirmBtn.disabled, 'Confirm button should be enabled for today');
});

run('Edit+Publishing, Standard: Edit Date in the future is valid', () => {
    const dom = buildScheduleDom(['EDIT', 'PUBLISHING'], 'STANDARD');
    loadScriptAgainst(dom.document);

    dom.editDate.value = isoDateOffset(1);
    dom.editDate.input();

    assert.ok(dom.editDateError.classList.contains('hidden'));
    assert.ok(!dom.confirmBtn.disabled);
});

run('Edit+Publishing, Standard: fixing a past Edit Date to a future one clears the error live', () => {
    const dom = buildScheduleDom(['EDIT', 'PUBLISHING'], 'STANDARD');
    loadScriptAgainst(dom.document);

    dom.editDate.value = isoDateOffset(-2);
    dom.editDate.input();
    assert.ok(!dom.editDateError.classList.contains('hidden'), 'sanity: past date should first show the error');

    dom.editDate.value = isoDateOffset(3);
    dom.editDate.input();
    assert.ok(dom.editDateError.classList.contains('hidden'), 'error should clear once a future date is typed');
    assert.ok(!dom.confirmBtn.disabled);
});

// Full pipeline (Shoot+Edit+Publishing): both fields are independently validated, and each error
// is stage-scoped to its own field only.
run('Shoot+Edit+Publishing, Standard: a past Shoot Date shows only the Shoot error, not Edit', () => {
    const dom = buildScheduleDom(['SHOOT', 'EDIT', 'PUBLISHING'], 'STANDARD');
    loadScriptAgainst(dom.document);

    dom.editDate.value = isoDateOffset(5);
    dom.editDate.input();
    dom.shootDate.value = isoDateOffset(-1);
    dom.shootDate.input();

    assert.ok(!dom.shootDateError.classList.contains('hidden'), 'Shoot Date error should be visible');
    assert.ok(dom.editDateError.classList.contains('hidden'), 'Edit Date error should stay hidden - Edit Date is valid');
    assert.ok(dom.confirmBtn.disabled);
});

// Publishing-only: Shoot/Edit dates are never validated at all, even if something in the (hidden)
// fields happens to hold a past-looking value.
run('Publishing-only, Standard: past-looking Edit Date is never validated (not part of this pipeline)', () => {
    const dom = buildScheduleDom(['PUBLISHING'], 'STANDARD');
    loadScriptAgainst(dom.document);

    dom.editDate.value = isoDateOffset(-10);
    dom.editDate.input();

    assert.ok(dom.editDateError.classList.contains('hidden'), 'Publishing-only must never validate Edit Date');
    assert.ok(!dom.confirmBtn.disabled);
});

// Urgent Planning Mode: the past-date guard is Standard-mode only by design - no inline error ever.
run('Edit+Publishing, Urgent: a past Edit Date never shows an inline error', () => {
    const dom = buildScheduleDom(['EDIT', 'PUBLISHING'], 'URGENT');
    loadScriptAgainst(dom.document);

    dom.editDate.value = isoDateOffset(-1);
    dom.editDate.input();

    assert.ok(dom.editDateError.classList.contains('hidden'), 'Urgent mode must never show the Standard-only guard error');
    assert.ok(!dom.confirmBtn.disabled);
});

if (process.exitCode === 1) {
    console.error('\nSome reviews-workspace.js schedule-validation tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll reviews-workspace.js schedule-validation tests passed.');
}
