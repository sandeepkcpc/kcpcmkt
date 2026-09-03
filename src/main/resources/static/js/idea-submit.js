/**
 * ENG-060: Submit Idea form - live character counters, client-side validation (required title,
 * valid-URL reference link) that blocks submission and shows an inline error without a page
 * reload, and a confirm-before-clear Reset. Backend validation stays authoritative (idea-submit.jsp
 * re-renders with the same errorMessage/errorField + preserved values on a server-side failure) -
 * this only stops the obvious cases before a request is even sent. Progressive enhancement: with
 * JS disabled the form is still a plain <form method="post"> (native `required` + native `reset`),
 * just without the live counters/inline messages.
 *
 * Two submission modes, picked by the form's own data-ajax attribute (set per-page by
 * fragments/idea-submit-form.jspf's caller):
 *   - idea-submit.jsp (data-ajax="false"): a real <form method="post"> - native submission, full
 *     page reload/redirect, exactly as before this file supported the Reviews Workspace modal too.
 *   - Reviews Workspace's "+ Create Idea" popup (data-ajax="true", fragments/idea-submit-modal.jspf):
 *     intercepted instead - fetch() with X-Requested-With, reusing the exact same
 *     showFieldError/clearFieldError functions client validation already uses to render the
 *     server's error message against the right field, then a 'kcpc:idea-created' event on success
 *     so idea-create-modal.js can close the popup and reviews-workspace.js can refresh the Ideas
 *     list/count in place (mirrors 'kcpc:idea-description-updated' in script-description-modal.js).
 *
 * wireIdeaSubmitForm(root) instead of a plain self-invoking IIFE - idea-submit.jsp renders this
 * once at page load (root = document), but the Reviews Workspace's tab header (reviews-content.jspf)
 * always includes the modal's copy of this same form and swaps its whole region's innerHTML via
 * AJAX on every tab/filter change (reviews-workspace.js), so its copy needs re-wiring after every
 * swap exactly like wireScriptDescriptionModal already does.
 */
(function () {
    function wireIdeaSubmitForm(root) {
        var form = root.querySelector('#idea-submit-form');
        if (!form) {
            return;
        }

        var titleField = form.querySelector('#title');
        var referenceLinkField = form.querySelector('#referenceLink');
        var notesRemarksField = form.querySelector('#notesRemarks');
        var submitBtn = form.querySelector('#idea-submit-btn');

        function errorAnchor(field) {
            var wrapper = field.closest('.input-with-icon');
            return wrapper || field;
        }

        function clearFieldError(field) {
            field.classList.remove('input-error');
            var anchor = errorAnchor(field);
            var next = anchor.nextElementSibling;
            if (next && next.classList.contains('field-error')) {
                next.remove();
            }
        }

        function showFieldError(field, message) {
            clearFieldError(field);
            field.classList.add('input-error');
            var err = document.createElement('div');
            err.className = 'field-error';
            err.textContent = message;
            errorAnchor(field).insertAdjacentElement('afterend', err);
        }

        function isValidUrl(value) {
            try {
                var url = new URL(value.trim());
                return url.protocol === 'http:' || url.protocol === 'https:';
            } catch (e) {
                return false;
            }
        }

        function validateTitle() {
            if (!titleField.value.trim()) {
                showFieldError(titleField, 'Idea Title is required.');
                return false;
            }
            clearFieldError(titleField);
            return true;
        }

        function validateReferenceLink() {
            var value = referenceLinkField.value.trim();
            if (value && !isValidUrl(value)) {
                showFieldError(referenceLinkField, 'Enter a valid URL.');
                return false;
            }
            clearFieldError(referenceLinkField);
            return true;
        }

        // Remove the inline error immediately once the field becomes valid.
        titleField.addEventListener('input', validateTitle);
        referenceLinkField.addEventListener('input', validateReferenceLink);

        function setCounter(field, limit) {
            var counter = form.querySelector('.char-counter[data-counter-for="' + field.id + '"]');
            if (!counter) {
                return;
            }
            var length = field.value.length;
            counter.textContent = length + ' / ' + limit;
            counter.classList.toggle('char-counter-at-limit', length >= limit);
            counter.classList.toggle('char-counter-near-limit', length >= limit * 0.9 && length < limit);
        }

        // Idea Description / Details (notesRemarks) has no length limit - unlimited-length script
        // content is a supported use case, so this only ever shows a running count, never a cap.
        function setUnboundedCounter(field) {
            var counter = form.querySelector('.char-counter[data-counter-for="' + field.id + '"]');
            if (!counter) {
                return;
            }
            counter.textContent = field.value.length + ' characters';
        }

        setCounter(titleField, 120);
        setUnboundedCounter(notesRemarksField);
        titleField.addEventListener('input', function () { setCounter(titleField, 120); });
        notesRemarksField.addEventListener('input', function () { setUnboundedCounter(notesRemarksField); });

        function submitAjax() {
            if (submitBtn) {
                submitBtn.disabled = true;
            }
            var params = new URLSearchParams(new FormData(form));
            fetch(form.getAttribute('action'), {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'fetch'},
                body: params.toString()
            }).then(function (response) {
                if (response.ok) {
                    return {ok: true};
                }
                return response.json().then(function (body) {
                    return {ok: false, message: body.message || 'The Idea could not be submitted.'};
                }).catch(function () {
                    return {ok: false, message: 'The Idea could not be submitted.'};
                });
            }).then(function (result) {
                if (submitBtn) {
                    submitBtn.disabled = false;
                }
                if (result.ok) {
                    // Deliberately not form.reset() - that fires the form's own 'reset' event
                    // (below), which would pop the "Clear all entered idea details?" confirm
                    // dialog right after a successful save. Plain field clears instead.
                    titleField.value = '';
                    referenceLinkField.value = '';
                    notesRemarksField.value = '';
                    clearFieldError(titleField);
                    clearFieldError(referenceLinkField);
                    setCounter(titleField, 120);
                    setUnboundedCounter(notesRemarksField);
                    document.dispatchEvent(new CustomEvent('kcpc:idea-created'));
                } else {
                    // Same field-attribution rule the server itself uses for the non-AJAX path
                    // (IdeaMvcController#submit) - Reference Link errors always mention it by name.
                    var field = result.message.indexOf('Reference Link') !== -1 ? referenceLinkField : titleField;
                    showFieldError(field, result.message);
                    field.scrollIntoView({block: 'center', behavior: 'smooth'});
                }
            }).catch(function () {
                if (submitBtn) {
                    submitBtn.disabled = false;
                }
                showFieldError(titleField, 'Network error - the Idea was not submitted. Please try again.');
            });
        }

        form.addEventListener('submit', function (event) {
            var titleOk = validateTitle();
            var referenceLinkOk = validateReferenceLink();
            if (!titleOk || !referenceLinkOk) {
                event.preventDefault();
                var firstInvalid = !titleOk ? titleField : referenceLinkField;
                firstInvalid.focus();
                firstInvalid.scrollIntoView({ block: 'center', behavior: 'smooth' });
                return;
            }
            if (form.dataset.ajax === 'true') {
                event.preventDefault();
                submitAjax();
            }
        });

        // Must clear only the current form after user confirmation if meaningful data is already
        // entered - an empty/untouched form just resets immediately, no need to ask.
        form.addEventListener('reset', function (event) {
            var hasData = form.querySelectorAll('input[type="text"], textarea').length &&
                Array.prototype.some.call(form.querySelectorAll('input[type="text"], textarea'), function (field) {
                    return field.value.trim().length > 0;
                });
            if (hasData && !window.confirm('Clear all entered idea details?')) {
                event.preventDefault();
                return;
            }
            // Native reset runs after this handler returns; clean up validation state on the next tick.
            window.setTimeout(function () {
                clearFieldError(titleField);
                clearFieldError(referenceLinkField);
                setCounter(titleField, 120);
                setUnboundedCounter(notesRemarksField);
            }, 0);
        });
    }

    window.wireIdeaSubmitForm = wireIdeaSubmitForm;
    wireIdeaSubmitForm(document);
})();
