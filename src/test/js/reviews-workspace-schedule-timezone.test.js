'use strict';
/**
 * Regression test for the toISOString()-in-a-positive-UTC-offset-timezone bug in
 * updateReviewsIdeaScheduleDefaults() (reviews-workspace.js): Date.prototype.toISOString()
 * converts to UTC, so for any browser/server running in a timezone AHEAD of UTC - Asia/Kolkata
 * (+5:30), this app's own IdeaService#BUSINESS_ZONE - a local-midnight Date instant rolled back to
 * the PREVIOUS calendar day once formatted, silently displaying (and, if left untouched,
 * submitting) a Shoot/Edit Date one day earlier than the Live Date - 5/2 days formula actually
 * computed. Sets process.env.TZ = 'Asia/Kolkata' BEFORE any Date is constructed so Node's own
 * local-time getters reproduce the same environment a real IST browser/user sees.
 *
 * Run with: node src/test/js/reviews-workspace-schedule-timezone.test.js
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
    }
    setAttribute(name, value) { this.attrs[name] = value; if (name === 'id') { this.id = value; } }
    getAttribute(name) {
        if (name === 'id') { return this.id || null; }
        return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null;
    }
    appendChild(child) { child.parent = this; this.children.push(child); return child; }
    addEventListener(type, handler) { (this.listeners[type] = this.listeners[type] || []).push(handler); }
    dispatch(type) {
        const event = { type, target: this };
        let node = this;
        while (node) {
            (node.listeners[type] || []).forEach((h) => h(Object.assign({}, event, { currentTarget: node })));
            node = node.parent;
        }
    }
    change() { this.dispatch('change'); }
    closest(selector) {
        let node = this;
        while (node) {
            if (node.matches && node.matches(selector)) { return node; }
            node = node.parent;
        }
        return null;
    }
    matches(selector) {
        return matchesCompound(this, parseCompound(selector));
    }
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

function buildDom(stages) {
    const document = new FakeElement('document');
    document.getElementById = document.getElementById.bind(document);
    document.addEventListener = FakeElement.prototype.addEventListener.bind(document);

    const region = new FakeElement('div');
    region.setAttribute('id', 'reviewsDynamicRegion');
    document.appendChild(region);

    function field(id) {
        const el = new FakeElement('input');
        el.setAttribute('id', id);
        region.appendChild(el);
        return el;
    }
    function errorSpan(id) {
        const el = new FakeElement('span');
        el.setAttribute('id', id);
        el.classList.add('hidden');
        region.appendChild(el);
        return el;
    }

    const liveDate = field('reviewsIdeaPlannedLiveDate');
    const planningMode = field('reviewsIdeaPlanningMode');
    planningMode.value = 'STANDARD';
    const shootDate = field('reviewsIdeaShootDate');
    const editDate = field('reviewsIdeaEditDate');
    errorSpan('reviewsIdeaShootDateError');
    errorSpan('reviewsIdeaEditDateError');
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

    return { document, liveDate, planningMode, shootDate, editDate, confirmBtn };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'reviews-workspace.js'), 'utf8');
    const sandbox = { document, console, window: { addEventListener() {}, location: { href: '' } } };
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

run('IST (+5:30): Live Date - 5/2 days displays the TRUE calendar date, not one day earlier', () => {
    const dom = buildDom(['SHOOT', 'EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);

    dom.liveDate.value = '2026-09-05';
    dom.liveDate.change();

    // Sep 5 - 5 days = Aug 31; Sep 5 - 2 days = Sep 3. The pre-fix toISOString() UTC conversion
    // rolled both back one extra day (Aug 30 / Sep 2) under Asia/Kolkata's +5:30 offset.
    assert.strictEqual(dom.shootDate.value, '2026-08-31', 'Shoot Date must be the true Live-5d result, not shifted a day earlier');
    assert.strictEqual(dom.editDate.value, '2026-09-03', 'Edit Date must be the true Live-2d result, not shifted a day earlier');
});

if (process.exitCode === 1) {
    console.error('\nSome reviews-workspace.js timezone tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll reviews-workspace.js timezone tests passed.');
}
