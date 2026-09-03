'use strict';
/**
 * Dependency-free test for src/main/resources/static/js/my-work-tabs.js's main-stage/sub-tab
 * navigation behaviour - no jsdom/npm package, just enough of a DOM shim (class list, attributes,
 * click dispatch, scoped querySelectorAll) to run the REAL script file under Node's built-in `vm`
 * module. Run with: node src/test/js/my-work-tabs.test.js
 *
 * Mirrors my-work.jsp's actual structure: 4 main stage tabs/panels (All/Shoot/Edit/Publishing -
 * class my-work-stage-tab/-panel), and inside each of Shoot/Edit/Publishing (not All, which has no
 * sub-tabs in the real page) its own 2-3 Active Work/History/Marks sub-tabs/panels (class
 * my-work-tab/-panel, stage-prefixed data-tab values e.g. "shoot-active"/"shoot-history").
 *
 * Covers the acceptance cases: switching between main stage tabs always lands on that stage's own
 * "Active Work" sub-tab, never a previously-selected sub-tab leaking in from another stage or from
 * an earlier visit to the same stage.
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

class FakeClassList {
    constructor(el) {
        this.el = el;
        this.set = new Set();
    }
    add(c) { this.set.add(c); }
    remove(c) { this.set.delete(c); }
    contains(c) { return this.set.has(c); }
    toggle(c, force) {
        if (force) { this.set.add(c); } else { this.set.delete(c); }
    }
}

class FakeElement {
    constructor(tag) {
        this.tag = tag;
        this.attrs = {};
        this.children = [];
        this.parent = null;
        this.classList = new FakeClassList(this);
        this.listeners = {};
    }
    setAttribute(name, value) { this.attrs[name] = value; }
    getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null; }
    appendChild(child) { child.parent = this; this.children.push(child); return child; }
    addEventListener(type, handler) {
        (this.listeners[type] = this.listeners[type] || []).push(handler);
    }
    click() {
        (this.listeners.click || []).forEach((h) => h({ currentTarget: this, target: this }));
    }
    className(...classes) {
        classes.forEach((c) => this.classList.add(c));
        return this;
    }
    // Minimal selector support: only what my-work-tabs.js actually uses - a single compound
    // class selector like ".my-work-tab" or ".my-work-tab.active" (AND of every class listed).
    matches(selector) {
        const classes = selector.split('.').filter(Boolean);
        return classes.every((c) => this.classList.contains(c));
    }
    querySelectorAll(selector) {
        const out = [];
        const walk = (node) => {
            node.children.forEach((child) => {
                if (child.matches(selector)) {
                    out.push(child);
                }
                walk(child);
            });
        };
        walk(this);
        return out;
    }
    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
    }
}

function buildMyWorkDom() {
    const document = new FakeElement('document');

    function stageTab(name, isDefault) {
        const el = new FakeElement('button').className('my-work-stage-tab');
        if (isDefault) el.classList.add('active');
        el.setAttribute('data-tab', name);
        return el;
    }
    function stagePanel(name, isDefault) {
        const el = new FakeElement('div').className('my-work-stage-panel');
        if (!isDefault) el.classList.add('hidden');
        el.setAttribute('data-tab-panel', name);
        return el;
    }
    function subTab(stage, suffix, isDefault) {
        const el = new FakeElement('button').className('my-work-tab');
        if (isDefault) el.classList.add('active');
        el.setAttribute('data-tab', stage + '-' + suffix);
        return el;
    }
    function subPanel(stage, suffix, isDefault) {
        const el = new FakeElement('div').className('my-work-tab-panel');
        if (!isDefault) el.classList.add('hidden');
        el.setAttribute('data-tab-panel', stage + '-' + suffix);
        return el;
    }

    // Stage tabs live directly under document (own tab bar, same as the real page).
    const tabAll = stageTab('all', true);
    const tabShoot = stageTab('shoot', false);
    const tabEdit = stageTab('edit', false);
    const tabPublish = stageTab('publish', false);
    [tabAll, tabShoot, tabEdit, tabPublish].forEach((t) => document.appendChild(t));

    // Stage panels, each with its own nested Active Work/History/(Marks) sub-tab bar+panels as
    // real DOM descendants - exactly mirrors my-work.jsp's nesting, needed for scoped
    // querySelectorAll to only ever find ITS OWN stage's sub-tabs/panels.
    const panelAll = stagePanel('all', true); // no sub-tabs in the real "All" panel
    document.appendChild(panelAll);

    function buildStageWithSubTabs(stage, withMarks) {
        const panel = stagePanel(stage, false);
        const subTabsBar = new FakeElement('div');
        panel.appendChild(subTabsBar);
        subTabsBar.appendChild(subTab(stage, 'active', true));
        subTabsBar.appendChild(subTab(stage, 'history', false));
        if (withMarks) {
            subTabsBar.appendChild(subTab(stage, 'marks', false));
        }
        panel.appendChild(subPanel(stage, 'active', true));
        panel.appendChild(subPanel(stage, 'history', false));
        if (withMarks) {
            panel.appendChild(subPanel(stage, 'marks', false));
        }
        document.appendChild(panel);
        return panel;
    }

    const panelShoot = buildStageWithSubTabs('shoot', true);
    const panelEdit = buildStageWithSubTabs('edit', true);
    const panelPublish = buildStageWithSubTabs('publish', false); // Publishing has no Marks sub-tab

    return {
        document,
        tabs: { all: tabAll, shoot: tabShoot, edit: tabEdit, publish: tabPublish },
        panels: { all: panelAll, shoot: panelShoot, edit: panelEdit, publish: panelPublish }
    };
}

function loadScriptAgainst(document) {
    const src = fs.readFileSync(
        path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'my-work-tabs.js'), 'utf8');
    const sandbox = { document, console };
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox, { filename: 'my-work-tabs.js' });
}

function activeSubTabOf(panel) {
    const active = panel.querySelectorAll('.my-work-tab-panel').filter((p) => !p.classList.contains('hidden'));
    assert.strictEqual(active.length, 1, 'exactly one visible sub-panel expected in ' + panel.getAttribute('data-tab-panel'));
    return active[0].getAttribute('data-tab-panel');
}

function isStagePanelVisible(panel) {
    return !panel.classList.contains('hidden');
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

// Case 4: direct initial My Work load = Active Work (every stage panel's own default, before any
// click at all).
run('direct initial load: every stage defaults to its own Active Work sub-tab', () => {
    const dom = buildMyWorkDom();
    loadScriptAgainst(dom.document);
    assert.strictEqual(activeSubTabOf(dom.panels.shoot), 'shoot-active');
    assert.strictEqual(activeSubTabOf(dom.panels.edit), 'edit-active');
    assert.strictEqual(activeSubTabOf(dom.panels.publish), 'publish-active');
    assert.ok(isStagePanelVisible(dom.panels.all), 'All panel should be the initially visible stage');
    assert.ok(!isStagePanelVisible(dom.panels.shoot));
});

// Case 1: Edit -> History -> click Shoot = Shoot / Active Work (not History).
run('Edit -> History, then Shoot = Shoot/Active Work', () => {
    const dom = buildMyWorkDom();
    loadScriptAgainst(dom.document);

    dom.tabs.edit.click();
    dom.panels.edit.querySelectorAll('.my-work-tab').find((t) => t.getAttribute('data-tab') === 'edit-history').click();
    assert.strictEqual(activeSubTabOf(dom.panels.edit), 'edit-history', 'sanity: Edit is really on History before switching');

    dom.tabs.shoot.click();
    assert.ok(isStagePanelVisible(dom.panels.shoot));
    assert.ok(!isStagePanelVisible(dom.panels.edit));
    assert.strictEqual(activeSubTabOf(dom.panels.shoot), 'shoot-active');
});

// Case 2: Shoot -> History -> click Edit = Edit / Active Work.
run('Shoot -> History, then Edit = Edit/Active Work', () => {
    const dom = buildMyWorkDom();
    loadScriptAgainst(dom.document);

    dom.tabs.shoot.click();
    dom.panels.shoot.querySelectorAll('.my-work-tab').find((t) => t.getAttribute('data-tab') === 'shoot-history').click();
    assert.strictEqual(activeSubTabOf(dom.panels.shoot), 'shoot-history');

    dom.tabs.edit.click();
    assert.strictEqual(activeSubTabOf(dom.panels.edit), 'edit-active');
    // Revisiting Shoot resets it to Active Work too - "switching to" a main tab always lands on
    // Active Work, even returning to a stage that had a non-default sub-tab selected earlier;
    // there is no "remember where I left off" exception anywhere in the spec.
    dom.tabs.shoot.click();
    assert.strictEqual(activeSubTabOf(dom.panels.shoot), 'shoot-active');
});

// Case 3: Publishing -> Marks -> click All = All/Active Work (All has no sub-tabs of its own, so
// this is really "switching away from Publishing/Marks must not leave every sub-panel hidden the
// next time Publishing is revisited fresh" plus "All itself just becomes visible").
run('Publishing -> Marks, then All = All visible, Publishing still independently intact', () => {
    const dom = buildMyWorkDom();
    loadScriptAgainst(dom.document);

    dom.tabs.publish.click();
    dom.panels.publish.querySelectorAll('.my-work-tab').find((t) => t.getAttribute('data-tab') === 'publish-history').click();
    assert.strictEqual(activeSubTabOf(dom.panels.publish), 'publish-history');

    dom.tabs.all.click();
    assert.ok(isStagePanelVisible(dom.panels.all));
    assert.ok(!isStagePanelVisible(dom.panels.publish));

    // Switching back to Publishing resets it to Active Work too (it's the stage being switched
    // TO, same rule as every other case) - never stuck on the History it was left on.
    dom.tabs.publish.click();
    assert.strictEqual(activeSubTabOf(dom.panels.publish), 'publish-active');
});

function buildFlatTabDom(tabNames) {
    // Mirrors deliverable-detail.jsp's top-level Overview/Shoot/Edit/Publishing/Performance/
    // Timeline bar (and my-shoots.jsp's Upcoming/Past bar) - a single flat .my-work-tab/
    // .my-work-tab-panel tier living directly under document, with NO .my-work-stage-panel
    // wrapper anywhere on the page. This is the exact structure the pre-fix script left
    // completely unwired (see the guard around the loop over '.my-work-stage-panel').
    const document = new FakeElement('document');
    const tabs = {};
    const panels = {};
    tabNames.forEach((name, i) => {
        const tab = new FakeElement('button').className('my-work-tab');
        if (i === 0) tab.classList.add('active');
        tab.setAttribute('data-tab', name);
        document.appendChild(tab);
        tabs[name] = tab;

        const panel = new FakeElement('div').className('my-work-tab-panel');
        if (i !== 0) panel.classList.add('hidden');
        panel.setAttribute('data-tab-panel', name);
        document.appendChild(panel);
        panels[name] = panel;
    });
    return {document, tabs, panels};
}

function onlyVisiblePanel(panels) {
    const visible = Object.keys(panels).filter((name) => !panels[name].classList.contains('hidden'));
    assert.strictEqual(visible.length, 1, 'exactly one visible top-level panel expected, got: ' + visible.join(','));
    return visible[0];
}

// Regression test for the Content Detail navigation bug: Overview/Shoot/Edit/Publishing/
// Performance/Timeline tabs (no .my-work-stage-panel wrapper) must each open on click - before
// the fix, document.querySelectorAll('.my-work-stage-panel') found nothing on this page shape,
// so wireTabGroup was never called for this tab bar at all and every click did nothing.
run('flat tab bar (Content Detail shape): clicking each tab opens its own panel', () => {
    const names = ['overview', 'shoot', 'edit', 'publishing', 'performance', 'timeline'];
    const dom = buildFlatTabDom(names);
    loadScriptAgainst(dom.document);

    assert.strictEqual(onlyVisiblePanel(dom.panels), 'overview', 'Overview should be the initial panel');

    names.forEach((name) => {
        dom.tabs[name].click();
        assert.strictEqual(onlyVisiblePanel(dom.panels), name, 'clicking "' + name + '" should open its own panel');
        assert.ok(dom.tabs[name].classList.contains('active'), '"' + name + '" tab should be marked active after its own click');
    });
});

// Same flat shape, but only the tabs a viewer is actually authorized to see are rendered at all
// (deliverable-detail.jsp's c:if canSeeShootTab/etc.) - confirms the fix does not depend on all
// six tabs being present, matching the real permission-scoped tab visibility.
run('flat tab bar with only some tabs rendered (permission-scoped visibility): still all clickable', () => {
    const dom = buildFlatTabDom(['overview', 'edit']); // e.g. an Editor with no Shoot/Publish/Timeline authority
    loadScriptAgainst(dom.document);

    dom.tabs.edit.click();
    assert.strictEqual(onlyVisiblePanel(dom.panels), 'edit');
    dom.tabs.overview.click();
    assert.strictEqual(onlyVisiblePanel(dom.panels), 'overview');
});

// Confirms the fix's guard (`if (!stagePanels.length)`) does not double-wire My Work's own
// nested sub-tabs - a page WITH .my-work-stage-panel elements must never also get a competing
// document-scoped group over the same .my-work-tab/.my-work-tab-panel classes, which would
// attach two conflicting listeners and reintroduce the "one shared global group hides every
// other stage's panels" bug already fixed for My Work in an earlier phase.
run('nested My Work shape is not double-wired by the flat-page fallback', () => {
    const dom = buildMyWorkDom();
    loadScriptAgainst(dom.document);

    dom.tabs.edit.click();
    dom.panels.edit.querySelectorAll('.my-work-tab').find((t) => t.getAttribute('data-tab') === 'edit-history').click();
    assert.strictEqual(activeSubTabOf(dom.panels.edit), 'edit-history');
    // A single click only ever activates its own sub-tab exactly once - if a second, document-
    // scoped group were also wired, this same button would have two listeners firing activate()
    // with conflicting scopes, and querySelectorAll('.my-work-tab-panel') at the document level
    // would toggle EVERY stage's sub-panels together, leaving more than one visible.
    assert.strictEqual(activeSubTabOf(dom.panels.shoot), 'shoot-active', 'Shoot sub-tab must be untouched by an Edit click');
    assert.strictEqual(activeSubTabOf(dom.panels.publish), 'publish-active', 'Publish sub-tab must be untouched by an Edit click');
});

if (process.exitCode === 1) {
    console.error('\nSome my-work-tabs.js tests FAILED.');
    process.exit(1);
} else {
    console.log('\nAll my-work-tabs.js tests passed.');
}
