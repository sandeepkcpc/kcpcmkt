'use strict';
/**
 * Dependency-free test for src/main/resources/static/js/my-work-dashboard.js's platform-chip
 * popover wiring - no jsdom/npm package, just enough of a DOM shim to run the REAL script file
 * under Node's built-in `vm` module, following the same convention as my-work-tabs.test.js.
 * Run with: node src/test/js/my-work-platform-popover-wiring.test.js
 *
 * The defect this pins: My Work renders identical Platform chips in TWO panels - Dashboard ->
 * Upcoming Tasks and Publishing -> Active Publishing Tasks - but platform-chip-popover.js
 * delegates its chip-click listener PER CONTAINER (only outside-click/Escape are page-global).
 * Only the dashboard panel was ever passed to wireClicks(), so the Active Publishing table's icons
 * rendered correctly and did nothing when clicked. Both panels must be portalized AND wired.
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

class FakeElement {
    constructor(tag) {
        this.tag = tag;
        this.attrs = {};
        this.children = [];
    }
    setAttribute(name, value) { this.attrs[name] = value; }
    getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null; }
    appendChild(child) { this.children.push(child); return child; }
    // Minimal selector support: only the one form my-work-dashboard.js uses -
    // [data-tab-panel="<name>"].
    matches(selector) {
        const m = /^\[data-tab-panel="([^"]+)"\]$/.exec(selector);
        return m ? this.getAttribute('data-tab-panel') === m[1] : false;
    }
    querySelector(selector) {
        const walk = (node) => {
            for (const child of node.children) {
                if (child.matches(selector)) return child;
                const found = walk(child);
                if (found) return found;
            }
            return null;
        };
        return walk(this);
    }
}

/** A My Work page containing the given stage panels, in document order. */
function buildDom(panelNames) {
    const document = new FakeElement('document');
    panelNames.forEach((name) => {
        const panel = new FakeElement('div');
        panel.setAttribute('data-tab-panel', name);
        document.appendChild(panel);
    });
    return document;
}

/** Records which containers the shared module was asked to portalize/wire. */
function recordingPopoverModule() {
    return {
        portalized: [],
        wired: [],
        portalize(container) { this.portalized.push(container.getAttribute('data-tab-panel')); },
        wireClicks(container) { this.wired.push(container.getAttribute('data-tab-panel')); }
    };
}

function run(panelNames, popoverModule) {
    const document = buildDom(panelNames);
    const scriptPath = path.join(__dirname, '../../main/resources/static/js/my-work-dashboard.js');
    const source = fs.readFileSync(scriptPath, 'utf8');
    const sandbox = { document, window: {} };
    if (popoverModule) {
        sandbox.window.PlatformChipPopover = popoverModule;
    }
    sandbox.window.document = document;
    vm.createContext(sandbox);
    vm.runInContext(source, sandbox, { filename: scriptPath });
    return document;
}

// --- Both panels present: both are portalized AND wired ---------------------------------------
{
    const popovers = recordingPopoverModule();
    run(['dashboard', 'shoot', 'edit', 'publish'], popovers);

    assert.deepStrictEqual(popovers.wired.sort(), ['dashboard', 'publish'],
        'both Platform-chip panels must have their click listener wired - wiring only "dashboard" '
        + 'is exactly the defect where Active Publishing Tasks icons did nothing on click');
    assert.deepStrictEqual(popovers.portalized.sort(), ['dashboard', 'publish'],
        'both panels must have their popovers portalized to <body>');
    // Stage panels with no Platform chips are not wired - nothing to wire there.
    assert.ok(!popovers.wired.includes('shoot') && !popovers.wired.includes('edit'),
        'only the two panels that render Platform chips are wired');
}

// --- Publishing tab only (employee not authorized for the Dashboard panel) ---------------------
{
    const popovers = recordingPopoverModule();
    run(['publish'], popovers);
    assert.deepStrictEqual(popovers.wired, ['publish'],
        'the Publishing panel is wired even when the Dashboard panel is absent - the two must be '
        + 'independent, not "publish only if dashboard exists"');
    assert.deepStrictEqual(popovers.portalized, ['publish']);
}

// --- Dashboard only -----------------------------------------------------------------------------
{
    const popovers = recordingPopoverModule();
    run(['dashboard'], popovers);
    assert.deepStrictEqual(popovers.wired, ['dashboard']);
}

// --- Neither panel rendered: no calls, no crash --------------------------------------------------
{
    const popovers = recordingPopoverModule();
    run(['shoot'], popovers);
    assert.deepStrictEqual(popovers.wired, [], 'no panel with chips means nothing to wire');
    assert.deepStrictEqual(popovers.portalized, []);
}

// --- Shared module absent: exits quietly rather than throwing ------------------------------------
{
    assert.doesNotThrow(() => run(['dashboard', 'publish'], null),
        'a missing PlatformChipPopover must not break the rest of the page');
}

console.log('my-work-platform-popover-wiring.test.js: all assertions passed');
