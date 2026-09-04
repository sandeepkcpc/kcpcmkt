/**
 * App header profile menu - purely client-side open/close chrome (same open/close/outside-click/
 * Escape shape skip-stage-modal.js already uses elsewhere in this app), plus the same
 * initials-from-fullname avatar logic admin-shared.js already uses for admin-userdetail's own
 * avatar (duplicated here rather than shared, since admin-shared.js is only loaded on
 * Administration screens, not globally). The Sign out button inside the menu is the SAME existing
 * <form method="post" action="/logout"> this app has always used - no new/duplicate logout logic.
 *
 * Notification bell: purely visual (no notification backend/data exists anywhere in this app as
 * of this change - confirmed by inspection before adding this), so there is nothing to wire up
 * here; the badge element exists in the markup, hidden, ready for a real feature to populate it.
 */
(function () {
    'use strict';

    document.querySelectorAll('.app-header-avatar[data-fullname]').forEach(function (el) {
        var parts = el.getAttribute('data-fullname').trim().split(/\s+/).filter(Boolean);
        var initials = parts.length > 1
            ? (parts[0][0] + parts[parts.length - 1][0])
            : (parts[0] ? parts[0].slice(0, 2) : '');
        el.textContent = initials.toUpperCase();
    });

    var trigger = document.getElementById('headerProfileTrigger');
    var menu = document.getElementById('headerProfileMenu');
    if (!trigger || !menu) {
        return;
    }

    function closeMenu() {
        menu.classList.add('hidden');
        trigger.setAttribute('aria-expanded', 'false');
        document.removeEventListener('click', onDocClick, true);
        document.removeEventListener('keydown', onKeydown);
    }

    function openMenu() {
        menu.classList.remove('hidden');
        trigger.setAttribute('aria-expanded', 'true');
        document.addEventListener('click', onDocClick, true);
        document.addEventListener('keydown', onKeydown);
    }

    function onDocClick(event) {
        if (!menu.contains(event.target) && !trigger.contains(event.target)) {
            closeMenu();
        }
    }

    function onKeydown(event) {
        if (event.key === 'Escape') {
            closeMenu();
        }
    }

    trigger.addEventListener('click', function () {
        if (menu.classList.contains('hidden')) {
            openMenu();
        } else {
            closeMenu();
        }
    });
})();
