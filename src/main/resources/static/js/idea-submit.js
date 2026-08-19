/**
 * ENG-060: Submit Idea form - live character counters, client-side validation (required title,
 * valid-URL reference link) that blocks submission and shows an inline error without a page
 * reload, and a confirm-before-clear Reset. Backend validation stays authoritative (idea-submit.jsp
 * re-renders with the same errorMessage/errorField + preserved values on a server-side failure) -
 * this only stops the obvious cases before a request is even sent. Progressive enhancement: with
 * JS disabled the form is still a plain <form method="post"> (native `required` + native `reset`),
 * just without the live counters/inline messages.
 */
(function () {
    var form = document.getElementById('idea-submit-form');
    if (!form) {
        return;
    }

    var titleField = form.querySelector('#title');
    var referenceLinkField = form.querySelector('#referenceLink');
    var notesRemarksField = form.querySelector('#notesRemarks');

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

    setCounter(titleField, 120);
    setCounter(notesRemarksField, 500);
    titleField.addEventListener('input', function () { setCounter(titleField, 120); });
    notesRemarksField.addEventListener('input', function () { setCounter(notesRemarksField, 500); });

    form.addEventListener('submit', function (event) {
        var titleOk = validateTitle();
        var referenceLinkOk = validateReferenceLink();
        if (!titleOk || !referenceLinkOk) {
            event.preventDefault();
            var firstInvalid = !titleOk ? titleField : referenceLinkField;
            firstInvalid.focus();
            firstInvalid.scrollIntoView({ block: 'center', behavior: 'smooth' });
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
            setCounter(notesRemarksField, 500);
        }, 0);
    });
})();
