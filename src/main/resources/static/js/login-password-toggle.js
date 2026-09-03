// Login screen password visibility toggle - purely client-side show/hide (same
// data-toggle-password mechanism admin-shared.js already uses for Create User's Initial
// Password field), never changes what gets submitted. type="button" so it can never submit the
// login form.
(function () {
    'use strict';

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
})();
