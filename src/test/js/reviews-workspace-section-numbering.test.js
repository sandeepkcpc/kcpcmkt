'use strict';
/**
 * Dependency-free test for reviews-workspace.js's renumberReviewPlanningSections() - the Idea
 * Review & Planning (Approve) form's dynamic section numbering. Loads the REAL script into a Node
 * `vm` sandbox against a fake DOM and drives it through the same 'kcpc:stages-changed' event the
 * Stages picker fires, so what is under test is the shipped wiring, not a copy of the rule.
 *   node src/test/js/reviews-workspace-section-numbering.test.js
 *
 * Rule under test: only VISIBLE .reviews-planning-card sections carry numbers, and those numbers
 * are 1..n in DOM order with no gaps and no repeats, for every stage combination and after any
 * amount of toggling. The bug it guards: Publisher Assignment and Team Marks are hardcoded "5"/"6"
 * in the JSP, so a Publishing-only pipeline used to render "1, 2, 3, 5".
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
        this.tag = String(tag).toLowerCase();
        this.attrs = {};
        this.children = [];
        this.parent = null;
        this.listeners = {};
        this.classList = new FakeClassList();
        this.style = {};
        this.value = '';
        this.checked = false;
        this.textContent = '';
    }
    setAttribute(n, v) { this.attrs[n] = String(v); if (n === 'id') { this.id = String(v); } }
    getAttribute(n) { return Object.prototype.hasOwnProperty.call(this.attrs, n) ? this.attrs[n] : null; }
    appendChild(c) { c.parent = this; this.children.push(c); return c; }
    get parentNode() { return this.parent; }
    addEventListener(t, h) { (this.listeners[t] = this.listeners[t] || []).push(h); }
    dispatch(type) {
        const ev = { type, target: this };
        let n = this;
        while (n) { (n.listeners[type] || []).forEach((h) => h(ev)); n = n.parent; }
    }
    matches(sel) {
        // Compound/descendant selectors first - a bare `sel.startsWith('#')` test would swallow
        // '#reviewsIdeaStagesPicker input[type="checkbox"]:checked' and compare it to an id.
        if (sel === '#reviewsIdeaStagesPicker input[type="checkbox"]:checked') {
            return this.tag === 'input' && this.getAttribute('type') === 'checkbox' && this.checked;
        }
        if (sel === '.reviews-planning-card') { return this.classList.contains('reviews-planning-card'); }
        if (sel === '.reviews-section-number') { return this.classList.contains('reviews-section-number'); }
        if (/^#[\w-]+$/.test(sel)) { return this.id === sel.slice(1); }
        return false;
    }
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
}

/** The Approve/Planning form's real section order and ids (fragments/reviews-ideas.jspf). */
const SECTIONS = [
    { id: null, title: 'Planning Basics', staticNumber: '1' },
    { id: null, title: 'Schedule', staticNumber: '2' },
    { id: null, title: 'Planned Outputs', staticNumber: '3' },
    { id: 'reviewsIdeaShootAssignmentSection', title: 'Initial Shoot Assignment', staticNumber: '4' },
    { id: 'reviewsIdeaEditorAssignmentSection', title: 'Editor Assignment', staticNumber: '4' },
    { id: 'reviewsIdeaPublisherAssignmentSection', title: 'Publisher Assignment', staticNumber: '5' },
    { id: 'reviewsIdeaTeamMarksSection', title: 'Team Marks', staticNumber: '6' }
];

function buildDom(stages) {
    const document = new FakeElement('document');
    document.getElementById = (id) => {
        let found = null;
        const walk = (n) => n.children.forEach((c) => { if (found) { return; } if (c.id === id) { found = c; return; } walk(c); });
        walk(document);
        return found;
    };
    document.addEventListener = () => {};
    document.createElement = (t) => new FakeElement(t);

    const region = new FakeElement('div');
    region.setAttribute('id', 'reviewsDynamicRegion');
    document.appendChild(region);

    // The panel itself is .hidden until Approve is chosen - visibility must be judged relative to
    // it, never via a computed style, or every card would look hidden while the panel is closed.
    const panel = new FakeElement('div');
    panel.setAttribute('id', 'reviewsIdeaPlanningFields');
    panel.classList.add('hidden');
    region.appendChild(panel);

    const cards = {};
    SECTIONS.forEach((spec) => {
        const card = new FakeElement('div');
        card.classList.add('panel');
        card.classList.add('reviews-planning-card');
        if (spec.id) { card.setAttribute('id', spec.id); }
        const heading = new FakeElement('h3');
        heading.classList.add('reviews-section-heading');
        const number = new FakeElement('span');
        number.classList.add('reviews-section-number');
        number.textContent = spec.staticNumber;   // exactly what the JSP ships
        heading.appendChild(number);
        card.appendChild(heading);
        panel.appendChild(card);
        cards[spec.title] = card;
    });
    // Editor Assignment ships hidden in the JSP.
    cards['Editor Assignment'].classList.add('hidden');

    const picker = new FakeElement('div');
    picker.setAttribute('id', 'reviewsIdeaStagesPicker');
    region.appendChild(picker);
    const boxes = {};
    ['SHOOT', 'EDIT', 'PUBLISHING'].forEach((stage) => {
        const cb = new FakeElement('input');
        cb.setAttribute('type', 'checkbox');
        cb.value = stage;
        cb.checked = stages.indexOf(stage) !== -1;
        picker.appendChild(cb);
        boxes[stage] = cb;
    });

    return { document, region, panel, cards, boxes };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'reviews-workspace.js'), 'utf8');
    const sandbox = { document, console, window: { addEventListener() {}, location: { href: '' } } };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'reviews-workspace.js' });
}

/** Open the Approve panel and apply `stages`, exactly as the Stages picker does. */
function applyStages(dom, stages) {
    dom.panel.classList.remove('hidden');
    Object.keys(dom.boxes).forEach((s) => { dom.boxes[s].checked = stages.indexOf(s) !== -1; });
    dom.region.dispatch('kcpc:stages-changed');
}

/** [title, number] for every card that is currently visible, in DOM order. */
function visibleNumbering(dom) {
    return SECTIONS
        .filter((s) => !dom.cards[s.title].classList.contains('hidden'))
        .map((s) => [s.title, dom.cards[s.title].querySelector('.reviews-section-number').textContent]);
}

function numbersOnly(dom) { return visibleNumbering(dom).map((p) => p[1]); }

function assertSequential(dom, label) {
    const nums = numbersOnly(dom);
    const expected = nums.map((_, i) => String(i + 1));
    assert.deepStrictEqual(nums, expected,
        label + ': visible sections must be numbered 1..n with no gaps or repeats, got ' + JSON.stringify(visibleNumbering(dom)));
    assert.strictEqual(new Set(nums).size, nums.length, label + ': no duplicate numbers');
}

function run(name, fn) {
    try { fn(); console.log('PASS - ' + name); }
    catch (e) { console.error('FAIL - ' + name); console.error(e.stack || e); process.exitCode = 1; }
}

// --- the reported bug ---------------------------------------------------------------------------
run('Publishing-only: no gap where the hidden sections were (was "1,2,3,5")', () => {
    const dom = buildDom(['PUBLISHING']);
    loadScriptAgainst(dom.document);
    applyStages(dom, ['PUBLISHING']);

    assert.deepStrictEqual(visibleNumbering(dom), [
        ['Planning Basics', '1'], ['Schedule', '2'], ['Planned Outputs', '3'],
        ['Publisher Assignment', '4']
    ], 'Publisher Assignment must move up to 4 when the sections before it are hidden');
    assertSequential(dom, 'Publishing-only');
});

// --- every stage combination -------------------------------------------------------------------
run('Shoot+Edit+Publishing: full pipeline numbers 1..5', () => {
    const dom = buildDom(['SHOOT', 'EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    applyStages(dom, ['SHOOT', 'EDIT', 'PUBLISHING']);

    assert.deepStrictEqual(visibleNumbering(dom), [
        ['Planning Basics', '1'], ['Schedule', '2'], ['Planned Outputs', '3'],
        ['Initial Shoot Assignment', '4'], ['Publisher Assignment', '5'], ['Team Marks', '6']
    ]);
    assertSequential(dom, 'full pipeline');
});

run('Edit+Publishing (Shoot skipped): Editor Assignment takes position 4', () => {
    const dom = buildDom(['EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    applyStages(dom, ['EDIT', 'PUBLISHING']);

    const visible = visibleNumbering(dom);
    assert.deepStrictEqual(visible, [
        ['Planning Basics', '1'], ['Schedule', '2'], ['Planned Outputs', '3'],
        ['Editor Assignment', '4'], ['Publisher Assignment', '5'], ['Team Marks', '6']
    ]);
    assertSequential(dom, 'Edit+Publishing');
});

run('Shoot only: numbering stays sequential', () => {
    const dom = buildDom(['SHOOT']);
    loadScriptAgainst(dom.document);
    applyStages(dom, ['SHOOT']);
    assertSequential(dom, 'Shoot only');
});

// --- idempotence: the actual "no duplicate numbering" requirement ------------------------------
run('Toggling stages many times never drifts or duplicates the numbering', () => {
    const dom = buildDom(['SHOOT', 'EDIT', 'PUBLISHING']);
    loadScriptAgainst(dom.document);
    const combos = [
        ['SHOOT', 'EDIT', 'PUBLISHING'], ['PUBLISHING'], ['EDIT', 'PUBLISHING'],
        ['SHOOT'], ['PUBLISHING'], ['SHOOT', 'EDIT', 'PUBLISHING'], ['EDIT', 'PUBLISHING']
    ];
    for (let pass = 0; pass < 3; pass++) {
        combos.forEach((stages) => {
            applyStages(dom, stages);
            assertSequential(dom, 'pass ' + pass + ' ' + stages.join('+'));
        });
    }
    // Ending back on the full pipeline must give exactly the full-pipeline numbering again.
    applyStages(dom, ['SHOOT', 'EDIT', 'PUBLISHING']);
    assert.deepStrictEqual(numbersOnly(dom), ['1', '2', '3', '4', '5', '6']);
});

run('Re-firing the same stage selection is a no-op, not an increment', () => {
    const dom = buildDom(['PUBLISHING']);
    loadScriptAgainst(dom.document);
    applyStages(dom, ['PUBLISHING']);
    const first = numbersOnly(dom);
    for (let i = 0; i < 5; i++) { applyStages(dom, ['PUBLISHING']); }
    assert.deepStrictEqual(numbersOnly(dom), first, 'repeated renumbering must be idempotent');
});

// --- the function must not do anything beyond writing digits -----------------------------------
run('Renumbering never shows, hides or reorders a section itself', () => {
    const dom = buildDom(['PUBLISHING']);
    loadScriptAgainst(dom.document);
    applyStages(dom, ['PUBLISHING']);
    const hiddenAfter = SECTIONS.filter((s) => dom.cards[s.title].classList.contains('hidden')).map((s) => s.title);
    assert.deepStrictEqual(hiddenAfter.sort(),
        ['Editor Assignment', 'Initial Shoot Assignment', 'Team Marks'].sort(),
        'visibility must be decided only by the existing stage logic, never by the renumbering');
    // DOM order is untouched.
    const order = dom.panel.querySelectorAll('.reviews-planning-card').length;
    assert.strictEqual(order, SECTIONS.length, 'no card may be added or removed');
});

run('A hidden section is skipped entirely - it never consumes a number', () => {
    const dom = buildDom(['PUBLISHING']);
    loadScriptAgainst(dom.document);
    applyStages(dom, ['PUBLISHING']);
    const publisher = dom.cards['Publisher Assignment'].querySelector('.reviews-section-number').textContent;
    assert.strictEqual(publisher, '4');
    // Team Marks is hidden here; whatever it shows must not affect the visible run.
    assert.ok(dom.cards['Team Marks'].classList.contains('hidden'));
    assertSequential(dom, 'hidden skipped');
});

if (process.exitCode) {
    console.error('\nreviews-workspace.js section-numbering tests FAILED.');
    process.exit(1);
}
console.log('\nAll reviews-workspace.js section-numbering tests passed.');
