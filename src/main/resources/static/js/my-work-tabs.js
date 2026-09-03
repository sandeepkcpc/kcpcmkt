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
 * stage-prefixed data-tab values (e.g. "shoot-active") so the two tiers never collide.
 *
 * Each stage's own Active Work/History/Marks sub-tab-group is wired SEPARATELY, scoped to that
 * one stage panel (never one shared group spanning Shoot+Edit+Publishing's buttons/panels
 * together) - otherwise a single global group would only ever recognize ONE "currently active"
 * sub-tab across all three stages at a time, hiding the other two stages' own panels entirely.
 * Switching the main stage tab (All/Shoot/Edit/Publishing) always resets the stage being switched
 * TO back to its own first sub-tab ("Active Work") - a sub-tab selection never carries over from
 * whatever was last clicked on a different stage, or from an earlier visit to this same stage
 * earlier in the page's lifetime.
 */
(function () {
    function wireTabGroup(root, tabSelector, panelSelector) {
        var tabs = root.querySelectorAll(tabSelector);
        var panels = root.querySelectorAll(panelSelector);
        if (!tabs.length || !panels.length) {
            return null;
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

        var defaultTab = root.querySelector(tabSelector + '.active') || tabs[0];
        activate(defaultTab.getAttribute('data-tab'));

        return {
            // Re-selects this group's own FIRST tab (its server-rendered "Active Work" button,
            // always first in document order within its stage panel) - used when the main stage
            // tab is switched TO this panel, so it never shows whatever sub-tab was last active.
            resetToFirst: function () {
                activate(tabs[0].getAttribute('data-tab'));
            }
        };
    }

    var stagePanels = document.querySelectorAll('.my-work-stage-panel');
    var subTabGroupsByStagePanel = [];
    stagePanels.forEach(function (panel) {
        var group = wireTabGroup(panel, '.my-work-tab', '.my-work-tab-panel');
        if (group) {
            subTabGroupsByStagePanel.push({panel: panel, group: group});
        }
    });

    // Pages that reuse the .my-work-tab/.my-work-tab-panel markup for a single, flat tab bar with
    // no nested .my-work-stage-panel tier at all - Content Detail's top-level Overview/Shoot/Edit/
    // Publishing/Performance/Timeline tabs (deliverable-detail.jsp) and My Shoots' Upcoming/Past
    // tabs (my-shoots.jsp) - never get a group from the loop above, since it only ever looks
    // INSIDE a .my-work-stage-panel (My Work's own nested Shoot/Edit/Publishing sub-tabs). Without
    // this fallback, those pages' tab buttons render but have no click handler at all. Guarded by
    // stagePanels.length so My Work's own tabs are never wired a second time (which would attach
    // two conflicting listeners and break the nested-group behavior the loop above exists for).
    if (!stagePanels.length) {
        wireTabGroup(document, '.my-work-tab', '.my-work-tab-panel');
    }

    var stageGroup = wireTabGroup(document, '.my-work-stage-tab', '.my-work-stage-panel');
    if (stageGroup) {
        document.querySelectorAll('.my-work-stage-tab').forEach(function (tab) {
            tab.addEventListener('click', function (event) {
                var targetStage = event.currentTarget.getAttribute('data-tab');
                subTabGroupsByStagePanel.forEach(function (entry) {
                    if (entry.panel.getAttribute('data-tab-panel') === targetStage) {
                        entry.group.resetToFirst();
                    }
                });
            });
        });
    }

    // Permission-driven Assignment Management: a third, independent tier - Execution |
    // Assignment Management (my-work-mode-tab/my-work-mode-panel) - and within Assignment
    // Management, its own Shoot | Edit sub-tabs (assignment-mgmt-tab/assignment-mgmt-panel,
    // distinct classes so they never collide with the Execution tier's own Shoot/Edit stage tabs).
    // Neither has further nested Active Work/History/Marks sub-tabs, so no reset-on-switch concern.
    wireTabGroup(document, '.my-work-mode-tab', '.my-work-mode-panel');
    wireTabGroup(document, '.assignment-mgmt-tab', '.assignment-mgmt-panel');
})();
