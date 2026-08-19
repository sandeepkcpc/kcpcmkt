/**
 * Planning/Shoot/Edit Review Decision forms: the Reason field only exists to explain a rejection -
 * it's mandatory (and, ENG-049, only even shown) when "Request Rework" is selected; Approve never
 * needs one. Toggles both the input's `required` attribute and its wrapping field's visibility to
 * match the Decision select's current value, both on load (matching whatever's pre-selected) and
 * on every change - the browser's own validation only prompts for a reason when it's actually
 * required, and the screen doesn't show an irrelevant field. Purely a UX nicety - the server still
 * enforces the mandatory-for-rework rule independently either way.
 *
 * ENG-052: Shoot/Edit Review Decision also carry a "Qualifying Cameraperson(s)/Editor(s)" chip
 * picker (`.qualifying-picker-field`, a `.kcpc-model-picker` wired separately by model-picker.js)
 * in the SAME grid slot Reason occupies - the two are mutually exclusive (Approve shows the picker,
 * Request Rework shows Reason), toggled together with Reason so the grid never has an empty or
 * doubled-up second cell. Approve additionally requires at least one qualifying recipient checked
 * before the form is allowed to submit (a plan can't be approved with nobody credited).
 */
(function () {
    function showQualifyingError(field) {
        if (field.querySelector('.field-error')) {
            return;
        }
        var err = document.createElement('div');
        err.className = 'field-error';
        err.textContent = 'Select at least one qualifying recipient before approving.';
        field.appendChild(err);
        field.classList.add('input-error');
        field.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }

    function clearQualifyingError(field) {
        var err = field.querySelector('.field-error');
        if (err) {
            err.remove();
        }
        field.classList.remove('input-error');
    }

    function wire(form) {
        var decision = form.querySelector('select[name="approve"]');
        var reason = form.querySelector('input[name="reason"]');
        if (!decision || !reason) {
            return;
        }
        var reasonField = reason.closest('label') || reason.parentElement;
        var qualifyingField = form.querySelector('.qualifying-picker-field');
        var qualifyingCheckboxes = qualifyingField ? qualifyingField.querySelectorAll('input[type="checkbox"]') : [];

        function sync() {
            var isRework = decision.value === 'false';
            reason.required = isRework;
            if (reasonField) {
                reasonField.classList.toggle('hidden', !isRework);
            }
            if (qualifyingField) {
                qualifyingField.classList.toggle('hidden', isRework);
                if (isRework) {
                    clearQualifyingError(qualifyingField);
                }
            }
        }
        decision.addEventListener('change', sync);
        sync();

        if (qualifyingField && qualifyingCheckboxes.length) {
            for (var i = 0; i < qualifyingCheckboxes.length; i++) {
                qualifyingCheckboxes[i].addEventListener('change', function () {
                    clearQualifyingError(qualifyingField);
                });
            }
            form.addEventListener('submit', function (event) {
                if (decision.value !== 'true') {
                    return;
                }
                var anyChecked = false;
                for (var j = 0; j < qualifyingCheckboxes.length; j++) {
                    if (qualifyingCheckboxes[j].checked) {
                        anyChecked = true;
                        break;
                    }
                }
                if (!anyChecked) {
                    event.preventDefault();
                    showQualifyingError(qualifyingField);
                }
            });
        }
    }

    var forms = document.querySelectorAll('form');
    for (var i = 0; i < forms.length; i++) {
        wire(forms[i]);
    }
})();
