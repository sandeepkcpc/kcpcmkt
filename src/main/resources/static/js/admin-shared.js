// ENG-091: Administration screens visual modernization - purely cosmetic client-side behavior
// (password visibility toggle, avatar initials). Never reads/writes anything beyond the DOM; the
// submitted form field/value is completely unaffected by whichever state the toggle is in.
(function () {
    'use strict';

    document.querySelectorAll('.admin-avatar[data-fullname]').forEach(function (el) {
        var parts = el.getAttribute('data-fullname').trim().split(/\s+/).filter(Boolean);
        var initials = parts.length > 1
            ? (parts[0][0] + parts[parts.length - 1][0])
            : (parts[0] ? parts[0].slice(0, 2) : '');
        el.textContent = initials.toUpperCase();
    });

    document.addEventListener('click', function (event) {
        var toggle = event.target.closest('[data-toggle-password]');
        if (!toggle) {
            return;
        }
        var input = document.getElementById(toggle.getAttribute('data-toggle-password'));
        if (!input) {
            return;
        }
        var showing = input.type === 'text';
        input.type = showing ? 'password' : 'text';
        toggle.setAttribute('aria-label', showing ? 'Show password' : 'Hide password');
        toggle.classList.toggle('password-visible', !showing);
    });

    // Publishing Catalogue's "+ Create Platform/Channel/Target" header buttons - reveal/hide the
    // existing create <form> below (same form/fields/action as before, just collapsed by default
    // so the Platforms/Channels cards don't grow taller just from an always-open create form).
    document.addEventListener('click', function (event) {
        var toggle = event.target.closest('[data-toggle-panel]');
        if (!toggle) {
            return;
        }
        var panel = document.getElementById(toggle.getAttribute('data-toggle-panel'));
        if (!panel) {
            return;
        }
        panel.classList.toggle('hidden');
    });

    // Admin/CEO Password Reset: one-click copy of the just-generated temporary password (shown
    // once, as a flash message) so the CEO doesn't have to manually select/copy the text.
    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-copy-target]');
        if (!button) {
            return;
        }
        var source = document.getElementById(button.getAttribute('data-copy-target'));
        if (!source) {
            return;
        }
        var text = source.textContent.trim();
        var originalLabel = button.textContent;
        function showCopied() {
            button.textContent = 'Copied!';
            window.setTimeout(function () {
                button.textContent = originalLabel;
            }, 1500);
        }
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(text).then(showCopied);
        } else {
            // Fallback for a plain-HTTP (non-secure) context, where the Clipboard API is
            // unavailable - a hidden, briefly-focused textarea + the legacy copy command.
            var scratch = document.createElement('textarea');
            scratch.value = text;
            scratch.style.position = 'fixed';
            scratch.style.opacity = '0';
            document.body.appendChild(scratch);
            scratch.focus();
            scratch.select();
            try {
                document.execCommand('copy');
                showCopied();
            } finally {
                document.body.removeChild(scratch);
            }
        }
    });
})();
