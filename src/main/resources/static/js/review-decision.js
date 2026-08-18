/**
 * Planning/Shoot/Edit Review Decision forms: the Reason field only exists to explain a rejection -
 * it's mandatory (and, ENG-049, only even shown) when "Request Rework" is selected; Approve never
 * needs one. Toggles both the input's `required` attribute and its wrapping field's visibility to
 * match the Decision select's current value, both on load (matching whatever's pre-selected) and
 * on every change - the browser's own validation only prompts for a reason when it's actually
 * required, and the screen doesn't show an irrelevant field. Purely a UX nicety - the server still
 * enforces the mandatory-for-rework rule independently either way.
 */
(function () {
    function wire(form) {
        var decision = form.querySelector('select[name="approve"]');
        var reason = form.querySelector('input[name="reason"]');
        if (!decision || !reason) {
            return;
        }
        var reasonField = reason.closest('label') || reason.parentElement;
        function sync() {
            var isRework = decision.value === 'false';
            reason.required = isRework;
            if (reasonField) {
                reasonField.classList.toggle('hidden', !isRework);
            }
        }
        decision.addEventListener('change', sync);
        sync();
    }

    var forms = document.querySelectorAll('form');
    for (var i = 0; i < forms.length; i++) {
        wire(forms[i]);
    }
})();
