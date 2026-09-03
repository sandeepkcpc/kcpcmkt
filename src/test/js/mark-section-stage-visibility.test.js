'use strict';
/**
 * Dependency-free test for reviews-workspace.js's ENG-096 Team Marks stage-visibility fix - no
 * jsdom/npm package, just enough of a DOM shim to run the REAL script file under Node's built-in
 * `vm` module, mirroring reviews-workspace-schedule-validation.test.js's harness pattern. Run with:
 *   node src/test/js/mark-section-stage-visibility.test.js
 *
 * Covers the rule: Cameraperson Mark and Model Mark only apply when Shoot is part of the selected
 * Stages; Editor Mark (and the whole Team Marks section) only applies when Publishing isn't the
 * sole stage. Reacts live to the same 'kcpc:stages-changed' event stages-picker.js dispatches on
 * the region, exactly like the pre-existing Shoot/Editor/Publisher assignment sections.
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

function buildDom(checkedStages) {
    const document = new FakeElement('document');
    document.getElementById = document.getElementById.bind(document);
    document.addEventListener = FakeElement.prototype.addEventListener.bind(document);

    const region = new FakeElement('div');
    region.setAttribute('id', 'reviewsDynamicRegion');
    document.appendChild(region);

    const stagesPicker = new FakeElement('div');
    stagesPicker.setAttribute('id', 'reviewsIdeaStagesPicker');
    region.appendChild(stagesPicker);
    const stageCheckboxes = {};
    ['SHOOT', 'EDIT', 'PUBLISHING'].forEach((stage) => {
        const cb = new FakeElement('input');
        cb.setAttribute('type', 'checkbox');
        cb.value = stage;
        cb.checked = checkedStages.indexOf(stage) !== -1;
        stagesPicker.appendChild(cb);
        stageCheckboxes[stage] = cb;
    });

    const teamMarksSection = new FakeElement('div');
    teamMarksSection.setAttribute('id', 'reviewsIdeaTeamMarksSection');
    region.appendChild(teamMarksSection);
    const cameramanMarkLabel = new FakeElement('label');
    cameramanMarkLabel.setAttribute('id', 'reviewsIdeaCameramanMarkLabel');
    teamMarksSection.appendChild(cameramanMarkLabel);
    const editorMarkLabel = new FakeElement('label');
    editorMarkLabel.setAttribute('id', 'reviewsIdeaEditorMarkLabel');
    teamMarksSection.appendChild(editorMarkLabel);
    const modelMarkLabel = new FakeElement('label');
    modelMarkLabel.setAttribute('id', 'reviewsIdeaModelMarkLabel');
    teamMarksSection.appendChild(modelMarkLabel);

    return { document, region, stageCheckboxes, teamMarksSection, cameramanMarkLabel, editorMarkLabel, modelMarkLabel };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'reviews-workspace.js'), 'utf8');
    const sandbox = { document, console, window: { addEventListener() {}, location: { href: '' } } };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'reviews-workspace.js' });
}

function setStages(dom, checkedStages) {
    ['SHOOT', 'EDIT', 'PUBLISHING'].forEach((stage) => {
        dom.stageCheckboxes[stage].checked = checkedStages.indexOf(stage) !== -1;
    });
    dom.region.dispatch('kcpc:stages-changed');
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

// A: Shoot + Edit + Publishing -> all 3 mark fields visible.
run('A: Shoot+Edit+Publishing shows Camera + Editor + Model marks', () => {
    const dom = buildDom(['SHOOT', 'EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['SHOOT', 'EDIT', 'PUBLISHING']);

    assert.ok(!dom.teamMarksSection.classList.contains('hidden'), 'Team Marks section should be visible');
    assert.ok(!dom.cameramanMarkLabel.classList.contains('hidden'), 'Cameraperson Mark should be visible');
    assert.ok(!dom.editorMarkLabel.classList.contains('hidden'), 'Editor Mark should be visible');
    assert.ok(!dom.modelMarkLabel.classList.contains('hidden'), 'Model Mark should be visible');
});

// B: Edit + Publishing -> only Editor Mark visible, Camera/Model hidden.
run('B: Edit+Publishing shows only Editor Mark, hides Camera + Model', () => {
    const dom = buildDom(['EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['EDIT', 'PUBLISHING']);

    assert.ok(!dom.teamMarksSection.classList.contains('hidden'), 'Team Marks section should stay visible (Editor Mark applies)');
    assert.ok(dom.cameramanMarkLabel.classList.contains('hidden'), 'Cameraperson Mark should be hidden');
    assert.ok(!dom.editorMarkLabel.classList.contains('hidden'), 'Editor Mark should be visible');
    assert.ok(dom.modelMarkLabel.classList.contains('hidden'), 'Model Mark should be hidden');
});

// C: Publishing only -> no mark fields/section at all.
run('C: Publishing-only hides the entire Team Marks section', () => {
    const dom = buildDom(['PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['PUBLISHING']);

    assert.ok(dom.teamMarksSection.classList.contains('hidden'), 'Team Marks section should be hidden entirely');
});

// D: dynamic switch, full pipeline -> Edit+Publishing - Camera/Model disappear live.
run('D: switching Full pipeline -> Edit+Publishing hides Camera/Model live', () => {
    const dom = buildDom(['SHOOT', 'EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['SHOOT', 'EDIT', 'PUBLISHING']);
    assert.ok(!dom.cameramanMarkLabel.classList.contains('hidden'), 'sanity: Camera visible before switching');
    assert.ok(!dom.modelMarkLabel.classList.contains('hidden'), 'sanity: Model visible before switching');

    setStages(dom, ['EDIT', 'PUBLISHING']);
    assert.ok(dom.cameramanMarkLabel.classList.contains('hidden'), 'Cameraperson Mark should disappear');
    assert.ok(dom.modelMarkLabel.classList.contains('hidden'), 'Model Mark should disappear');
    assert.ok(!dom.editorMarkLabel.classList.contains('hidden'), 'Editor Mark should remain visible');
});

// E: dynamic switch, Edit+Publishing -> Publishing only - Editor disappears too.
run('E: switching Edit+Publishing -> Publishing-only hides Editor too', () => {
    const dom = buildDom(['EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['EDIT', 'PUBLISHING']);
    assert.ok(!dom.teamMarksSection.classList.contains('hidden'), 'sanity: section visible before switching');

    setStages(dom, ['PUBLISHING']);
    assert.ok(dom.teamMarksSection.classList.contains('hidden'), 'Team Marks section should now be hidden - Editor Mark no longer applies');
});

// D/E reverse: switching back to a stage combination that needs a mark makes it reappear.
run('reverse: switching back from Publishing-only to Edit+Publishing re-shows Editor Mark', () => {
    const dom = buildDom(['PUBLISHING']);
    loadScriptAgainst(dom.document);
    setStages(dom, ['PUBLISHING']);
    assert.ok(dom.teamMarksSection.classList.contains('hidden'), 'sanity: hidden for Publishing-only');

    setStages(dom, ['EDIT', 'PUBLISHING']);
    assert.ok(!dom.teamMarksSection.classList.contains('hidden'), 'Team Marks section should reappear');
    assert.ok(!dom.editorMarkLabel.classList.contains('hidden'), 'Editor Mark should reappear');
    assert.ok(dom.cameramanMarkLabel.classList.contains('hidden'), 'Cameraperson Mark should stay hidden - Shoot still not selected');
});

if (process.exitCode === 1) {
    console.error('\nSome mark-section-stage-visibility tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll mark-section-stage-visibility tests passed.');
}
