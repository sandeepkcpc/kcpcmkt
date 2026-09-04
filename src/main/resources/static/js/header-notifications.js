/**
 * App header notification bell - dropdown open/close chrome (same open/close/outside-click/
 * Escape shape header-user-menu.js already uses for the profile menu), plus mark-as-read.
 *
 * Mark-as-read uses fetch with keepalive:true (not sendBeacon, which cannot carry Spring
 * Security's X-CSRF-TOKEN header) so the POST survives the page navigation that clicking a
 * notification item's own <a href> already triggers - no preventDefault, no artificial delay
 * before navigating. The CSRF token is read from the readable (non-HttpOnly) KCPC_CSRF cookie -
 * Spring Security's own CookieCsrfTokenRepository.withHttpOnlyFalse() double-submit-cookie
 * pattern (SecurityConfig#configureCsrf), the standard mechanism for a plain-JS (non-form) POST
 * in this app's CSRF setup.
 */
(function () {
    'use strict';

    function readCsrfCookie() {
        var match = document.cookie.match(/(?:^|;\s*)KCPC_CSRF=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    function postAjax(url) {
        var csrfToken = readCsrfCookie();
        var headers = { 'X-Requested-With': 'fetch' };
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
        fetch(url, { method: 'POST', headers: headers, keepalive: true, credentials: 'same-origin' });
    }

    function formatRelativeTime(el) {
        var iso = el.getAttribute('data-created-at');
        if (!iso) {
            return;
        }
        var then = new Date(iso).getTime();
        if (isNaN(then)) {
            return;
        }
        var diffMs = Date.now() - then;
        var minutes = Math.max(0, Math.round(diffMs / 60000));
        var text;
        if (minutes < 1) {
            text = 'just now';
        } else if (minutes < 60) {
            text = minutes + ' min ago';
        } else if (minutes < 1440) {
            text = Math.round(minutes / 60) + (Math.round(minutes / 60) === 1 ? ' hour ago' : ' hours ago');
        } else {
            var days = Math.round(minutes / 1440);
            text = days + (days === 1 ? ' day ago' : ' days ago');
        }
        el.textContent = text;
    }

    document.querySelectorAll('.app-header-notification-time[data-created-at]').forEach(formatRelativeTime);

    var trigger = document.getElementById('headerNotificationTrigger');
    var menu = document.getElementById('headerNotificationMenu');
    if (trigger && menu) {
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
    }

    // Clicking a notification item marks it read (fire-and-forget) and navigates to its Content
    // Detail link normally - never blocks/delays the navigation itself.
    document.querySelectorAll('.app-header-notification-item[data-unread="true"]').forEach(function (item) {
        item.addEventListener('click', function () {
            var url = item.getAttribute('data-mark-read-url');
            if (url) {
                postAjax(url);
            }
        });
    });

    var markAllBtn = document.getElementById('headerNotificationMarkAllRead');
    if (markAllBtn) {
        markAllBtn.addEventListener('click', function (event) {
            event.preventDefault();
            postAjax(markAllBtn.getAttribute('data-url'));
            // Reflects the action immediately client-side (dots/highlight/badge/button all clear)
            // rather than waiting for the next full page load to catch up with the server state.
            document.querySelectorAll('.app-header-notification-item-unread').forEach(function (item) {
                item.classList.remove('app-header-notification-item-unread');
                item.setAttribute('data-unread', 'false');
            });
            var badge = document.querySelector('.app-header-notification-badge');
            if (badge) {
                badge.remove();
            }
            markAllBtn.remove();
        });
    }
})();
