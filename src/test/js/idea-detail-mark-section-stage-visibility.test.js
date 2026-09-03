'use strict';
/**
 * Same ENG-096 coverage as mark-section-stage-visibility.test.js, but for idea-detail.js - the
 * separate, non-AJAX native-<form> Approve entry point (/app/ideas/{id}), which mirrors
 * reviews-workspace.js's Stages -> visibility wiring independently (updateStagesFields() there
 * listens directly on the Stages picker element, not delegated through a region). Run with:
 *   node src/test/js/idea-detail-mark-section-stage-visibility.test.js
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
    matches(selector) { return matchesCompound(this, parseCompound(selector)); }
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

function buildDom(checkedStages) {
    const document = new FakeElement('document');
    document.getElementById = document.getElementById.bind(document);
    document.addEventListener = FakeElement.prototype.addEventListener.bind(document);
    document.querySelector = FakeElement.prototype.querySelector.bind(document);

    const form = new FakeElement('form');
    form.setAttribute('id', 'idea-review-form');
    document.appendChild(form);

    const stagesPicker = new FakeElement('div');
    stagesPicker.setAttribute('id', 'idea-review-stages-picker');
    form.appendChild(stagesPicker);
    const stageCheckboxes = {};
    ['SHOOT', 'EDIT', 'PUBLISHING'].forEach((stage) => {
        const cb = new FakeElement('input');
        cb.setAttribute('type', 'checkbox');
        cb.setAttribute('name', 'stages');
        cb.value = stage;
        cb.checked = checkedStages.indexOf(stage) !== -1;
        stagesPicker.appendChild(cb);
        stageCheckboxes[stage] = cb;
    });

    const teamMarksRow = new FakeElement('div');
    teamMarksRow.setAttribute('id', 'idea-review-team-marks-row');
    form.appendChild(teamMarksRow);
    const cameramanMarkField = new FakeElement('div');
    cameramanMarkField.setAttribute('id', 'idea-review-cameraman-mark-field');
    teamMarksRow.appendChild(cameramanMarkField);
    const editorMarkField = new FakeElement('div');
    editorMarkField.setAttribute('id', 'idea-review-editor-mark-field');
    teamMarksRow.appendChild(editorMarkField);
    const modelMarkField = new FakeElement('div');
    modelMarkField.setAttribute('id', 'idea-review-model-mark-field');
    teamMarksRow.appendChild(modelMarkField);

    return { document, form, stagesPicker, stageCheckboxes, teamMarksRow, cameramanMarkField, editorMarkField, modelMarkField };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'idea-detail.js'), 'utf8');
    const sandbox = { document, console, window: { addEventListener() {}, location: { href: '' } } };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'idea-detail.js' });
}

function setStages(dom, checkedStages) {
    ['SHOOT', 'EDIT', 'PUBLISHING'].forEach((stage) => {
        dom.stageCheckboxes[stage].checked = checkedStages.indexOf(stage) !== -1;
    });
    dom.stagesPicker.dispatch('kcpc:stages-changed');
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

run('A: Shoot+Edit+Publishing shows Camera + Editor + Model marks', () => {
    const dom = buildDom(['SHOOT', 'EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['SHOOT', 'EDIT', 'PUBLISHING']);

    assert.ok(!dom.teamMarksRow.classList.contains('hidden'));
    assert.ok(!dom.cameramanMarkField.classList.contains('hidden'));
    assert.ok(!dom.editorMarkField.classList.contains('hidden'));
    assert.ok(!dom.modelMarkField.classList.contains('hidden'));
});

run('B: Edit+Publishing shows only Editor Mark, hides Camera + Model', () => {
    const dom = buildDom(['EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['EDIT', 'PUBLISHING']);

    assert.ok(!dom.teamMarksRow.classList.contains('hidden'));
    assert.ok(dom.cameramanMarkField.classList.contains('hidden'));
    assert.ok(!dom.editorMarkField.classList.contains('hidden'));
    assert.ok(dom.modelMarkField.classList.contains('hidden'));
});

run('C: Publishing-only hides the entire Team Marks row', () => {
    const dom = buildDom(['PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['PUBLISHING']);

    assert.ok(dom.teamMarksRow.classList.contains('hidden'));
});

run('D: switching Full pipeline -> Edit+Publishing hides Camera/Model live', () => {
    const dom = buildDom(['SHOOT', 'EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['SHOOT', 'EDIT', 'PUBLISHING']);

    setStages(dom, ['EDIT', 'PUBLISHING']);
    assert.ok(dom.cameramanMarkField.classList.contains('hidden'));
    assert.ok(dom.modelMarkField.classList.contains('hidden'));
    assert.ok(!dom.editorMarkField.classList.contains('hidden'));
});

run('E: switching Edit+Publishing -> Publishing-only hides Editor too', () => {
    const dom = buildDom(['EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['EDIT', 'PUBLISHING']);
    assert.ok(!dom.teamMarksRow.classList.contains('hidden'));

    setStages(dom, ['PUBLISHING']);
    assert.ok(dom.teamMarksRow.classList.contains('hidden'));
});

if (process.exitCode === 1) {
    console.error('\nSome idea-detail.js mark-section tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll idea-detail.js mark-section tests passed.');
}
