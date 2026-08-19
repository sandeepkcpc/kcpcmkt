/**
 * ENG-058: My Work tabs (Active Work / History / Marks) - pure client-side show/hide, default
 * Active Work. Every panel is already fully server-rendered; this only toggles visibility, so
 * there's nothing to fetch and no reload on switching tabs.
 *
 * ENG-067: also reused as-is by My Shoots (Upcoming / Past) - the default tab is whichever button
 * is server-rendered with the "active" CSS class, not a hardcoded "active" data-tab value, so any
 * page can pick its own first-tab name.
 */
(function () {
    var tabs = document.querySelectorAll('.my-work-tab');
    var panels = document.querySelectorAll('.my-work-tab-panel');
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

    var defaultTab = document.querySelector('.my-work-tab.active');
    activate(defaultTab ? defaultTab.getAttribute('data-tab') : tabs[0].getAttribute('data-tab'));
})();
