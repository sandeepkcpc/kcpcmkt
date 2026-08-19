/**
 * ENG-051: "Submit for Planning Review" (#planning-details-form) previously always did a plain
 * form POST - any validation failure (a conditionally-required field the browser can't natively
 * check, or something only the backend catches) meant a full-page redirect, discarding whatever
 * else the user had already filled in. Client-side validation stops the obvious cases before any
 * request is even sent; whatever's left goes through AJAX so a backend failure comes back as an
 * inline message instead of a navigation. Progressive enhancement: without JS this is still a
 * plain <form method="post"> that POSTs and redirects back - less friendly on failure, but fully
 * functional (unchanged from before this file existed).
 */
(function () {
    function clearErrors(form) {
        var errors = form.querySelectorAll('.field-error');
        for (var i = 0; i < errors.length; i++) {
            errors[i].remove();
        }
        var invalidFields = form.querySelectorAll('.input-error');
        for (var j = 0; j < invalidFields.length; j++) {
            invalidFields[j].classList.remove('input-error');
        }
        var banner = form.querySelector('.planning-submit-error');
        if (banner) {
            banner.remove();
        }
    }

    function showFieldError(field, message) {
        field.classList.add('input-error');
        var err = document.createElement('div');
        err.className = 'field-error';
        err.textContent = message;
        field.insertAdjacentElement('afterend', err);
    }

    function showBanner(form, message) {
        var banner = document.createElement('div');
        banner.className = 'ajax-error planning-submit-error';
        banner.textContent = message;
        form.insertBefore(banner, form.firstChild);
    }

    /**
     * Planned Live Date is always required (also carries the native HTML5 `required` attribute,
     * kept for the no-JS fallback); Shoot Date/Edit Date/Urgency Reason only become required once
     * Planning Mode is Urgent - a rule a static `required` attribute can't express since it depends
     * on another field's current value.
     */
    function validatePlanningForm(form) {
        var errors = [];
        var plannedLiveDate = form.querySelector('[name="plannedLiveDate"]');
        if (plannedLiveDate && !plannedLiveDate.value) {
            errors.push({ field: plannedLiveDate, message: 'Planned Live Date is required.' });
        }
        var planningMode = form.querySelector('[name="planningMode"]');
        if (planningMode && planningMode.value === 'URGENT') {
            var shootDate = form.querySelector('[name="shootDate"]');
            var editDate = form.querySelector('[name="editDate"]');
            var urgencyReason = form.querySelector('[name="urgencyReason"]');
            if (shootDate && !shootDate.value) {
                errors.push({ field: shootDate, message: 'Shoot Date is required for Urgent planning.' });
            }
            if (editDate && !editDate.value) {
                errors.push({ field: editDate, message: 'Edit Date is required for Urgent planning.' });
            }
            if (urgencyReason && !urgencyReason.value.trim()) {
                errors.push({ field: urgencyReason, message: 'Urgency Reason is required for Urgent planning.' });
            }
        }
        return errors;
    }

    var form = document.getElementById('planning-details-form');
    if (!form) {
        return;
    }
    // Planned Live Date carries the native `required` attribute (for the no-JS fallback below).
    // With JS active, native constraint validation runs BEFORE the `submit` event even fires, which
    // would show the browser's own bubble for that one field while every other rule here shows a
    // custom .field-error message - novalidate hands ALL of it to validatePlanningForm instead, so
    // the experience is consistent regardless of which field is missing.
    form.noValidate = true;

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        clearErrors(form);

        var errors = validatePlanningForm(form);
        if (errors.length > 0) {
            for (var i = 0; i < errors.length; i++) {
                showFieldError(errors[i].field, errors[i].message);
            }
            errors[0].field.focus();
            errors[0].field.scrollIntoView({ block: 'center', behavior: 'smooth' });
            return;
        }

        // The "Submit for Planning Review" button lives outside this form's DOM (form="" - see
        // ENG-045), so it can't be found via form.querySelector.
        var submitBtn = document.querySelector('button[type="submit"][form="' + form.id + '"]')
            || form.querySelector('button[type="submit"]');
        if (submitBtn) {
            submitBtn.disabled = true;
        }

        // new FormData(form) includes every field associated with this form, including the
        // Cameraperson(s)/Shoot Lead/Shoot Instructions controls that sit outside its DOM subtree
        // via form="" (ENG-045) - same technique publication-scope.js's serializeForm already uses.
        var params = new URLSearchParams();
        new FormData(form).forEach(function (value, key) {
            params.append(key, value);
        });

        fetch(form.getAttribute('action'), {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'fetch'
            },
            body: params.toString()
        }).then(function (response) {
            if (response.ok) {
                // Submission succeeded and the plan moved to Planning Review - the whole page's
                // panels change for the new status, so a full reload (not an in-place DOM patch) is
                // the right outcome here, same as the plain-form redirect this replaces.
                window.location.reload();
                return;
            }
            if (submitBtn) {
                submitBtn.disabled = false;
            }
            return response.json().then(function (body) {
                showBanner(form, (body && body.message) || 'Could not submit. Please try again.');
            }).catch(function () {
                showBanner(form, 'Could not submit. Please try again.');
            });
        }).catch(function () {
            if (submitBtn) {
                submitBtn.disabled = false;
            }
            showBanner(form, 'Could not submit. Please check your connection and try again.');
        });
    });
})();
