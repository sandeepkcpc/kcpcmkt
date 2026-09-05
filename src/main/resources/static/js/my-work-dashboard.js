/**
 * Wires the Platform chip popovers for My Work's Platforms columns - reuses the shared
 * platform-chip-popover.js module, the exact same interaction Content Pipeline's own Platforms
 * column already uses. No popup markup or click handling of its own.
 *
 * TWO panels carry Platform chips, and each needs its own wiring call: PlatformChipPopover only
 * installs its outside-click/Escape listeners globally - the chip-click listener itself is
 * delegated per container (see wireClicks), so a panel that is never passed in has visibly dead
 * chips. That was the defect here: Dashboard -> Upcoming Tasks was wired, and the Publishing tab's
 * own Active Publishing Tasks table (which renders the identical chips via the same fragment) was
 * not, so its icons opened nothing.
 *
 * Both panels' popovers are portalized to <body> for the same table-scroll/clipping reason
 * Content Pipeline documented originally; the two tables use distinct popover id prefixes
 * (upcoming-platform-popover-* / active-platform-popover-*) precisely so they can coexist there.
 *
 * My Work is a fully server-rendered page (no AJAX re-render of these tables), so the one-time
 * portalize + wire at page load is all that is needed - no re-portalize-on-refresh concern like
 * the Pipeline's AJAX swaps have.
 */
(function () {
    if (!window.PlatformChipPopover) {
        return;
    }
    // A panel is absent whenever this employee is not authorized for that stage, which is normal -
    // each is wired only if actually rendered.
    ['dashboard', 'publish'].forEach(function (tabName) {
        var panel = document.querySelector('[data-tab-panel="' + tabName + '"]');
        if (!panel) {
            return;
        }
        window.PlatformChipPopover.portalize(panel);
        window.PlatformChipPopover.wireClicks(panel);
    });
})();
