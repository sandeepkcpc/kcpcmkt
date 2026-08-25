/**
 * ENG-058: My Work tabs (Active Work / History / Marks) - pure client-side show/hide, default
 * Active Work. Every panel is already fully server-rendered; this only toggles visibility, so
 * there's nothing to fetch and no reload on switching tabs.
 *
 * ENG-067: also reused as-is by My Shoots (Upcoming / Past) - the default tab is whichever button
 * is server-rendered with the "active" CSS class, not a hardcoded "active" data-tab value, so any
 * page can pick its own first-tab name.
 *
 * Permission-driven My Work redesign: a second, independent tab tier (All | Shoot | Edit |
 * Publishing, class names my-work-stage-tab/my-work-stage-panel) selects which stage section is
 * visible; each stage section then has its own nested Active Work/History/Marks sub-tabs using
 * stage-prefixed data-tab values (e.g. "shoot-active") so the two tiers never collide - wireTabGroup
 * is generic and wires both tiers the same way, independently.
 */
(function () {
    function wireTabGroup(tabSelector, panelSelector) {
        var tabs = document.querySelectorAll(tabSelector);
        var panels = document.querySelectorAll(panelSelector);
        if (!tabs.length || !panels.length) {
            return;
        }

        function activate(tabName) {
            for (var i = 0; i < tabs.length; i++) {
                tabs[i].classList.toggle('active', tabs[i].getAttribute('data-tab') === tabName);
            }
            for (var j = 0; j < panels.length; j++) {
                panels[j].classList.toggle('hidden', panels[j].getAttribute('data-tab-panel') !== tabName);
            }
        }

        for (var i = 0; i < tabs.length; i++) {
            tabs[i].addEventListener('click', function (event) {
                activate(event.currentTarget.getAttribute('data-tab'));
            });
        }

        var defaultTab = document.querySelector(tabSelector + '.active');
        activate(defaultTab ? defaultTab.getAttribute('data-tab') : tabs[0].getAttribute('data-tab'));
    }

    wireTabGroup('.my-work-stage-tab', '.my-work-stage-panel');
    wireTabGroup('.my-work-tab', '.my-work-tab-panel');
    // Permission-driven Assignment Management: a third, independent tier - Execution |
    // Assignment Management (my-work-mode-tab/my-work-mode-panel) - and within Assignment
    // Management, its own Shoot | Edit sub-tabs (assignment-mgmt-tab/assignment-mgmt-panel,
    // distinct classes so they never collide with the Execution tier's own Shoot/Edit stage tabs).
    wireTabGroup('.my-work-mode-tab', '.my-work-mode-panel');
    wireTabGroup('.assignment-mgmt-tab', '.assignment-mgmt-panel');
})();
