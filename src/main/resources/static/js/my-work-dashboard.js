/**
 * Wires the Platform chip popovers for My Work -> Dashboard -> Upcoming Tasks' own Platforms
 * column - reuses the shared platform-chip-popover.js module, the exact same interaction Content
 * Pipeline's own Platforms column already uses. My Work is a fully server-rendered page (no AJAX
 * re-render), so this only needs the one-time portalize + wire pipeline-dashboard.js's own init
 * does at page load - no re-portalize-on-refresh concern like the Pipeline's AJAX swaps have.
 */
(function () {
    if (!window.PlatformChipPopover) {
        return;
    }
    var panel = document.querySelector('[data-tab-panel="dashboard"]');
    if (!panel) {
        return;
    }
    window.PlatformChipPopover.portalize(panel);
    window.PlatformChipPopover.wireClicks(panel);
})();
