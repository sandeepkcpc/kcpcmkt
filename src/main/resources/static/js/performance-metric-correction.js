/**
 * Correct-a-Metric: the submitted-scorecard correction form renders one "Corrected Value" field
 * per applicable metric (server-side, already filtered to metrics not marked N/A on that specific
 * scorecard) but shows only the one matching the "Metric to correct" dropdown. Every lookup here is
 * scoped via select.closest('form') - never document-wide - so this works unmodified across
 * multiple Performance obligation cards on the same page without any card reading or clearing
 * another card's fields. Progressive enhancement: without JS, every field is simply visible at
 * once and the form still posts correctly (the backend accepts any subset of metrics).
 */
(function () {
    function wireMetricCorrectionForm(form) {
        var select = form.querySelector('.metric-correction-select');
        if (!select) {
            return;
        }
        var fields = form.querySelectorAll('.performance-metric-correction-field');

        function applySelection() {
            for (var i = 0; i < fields.length; i++) {
                var field = fields[i];
                if (field.getAttribute('data-metric') === select.value) {
                    field.classList.remove('hidden');
                } else {
                    field.classList.add('hidden');
                }
            }
        }

        select.addEventListener('change', applySelection);
        applySelection();
    }

    function wirePerformanceMetricCorrections(root) {
        var scope = root || document;
        var forms = scope.querySelectorAll('.performance-correction-form');
        for (var i = 0; i < forms.length; i++) {
            wireMetricCorrectionForm(forms[i]);
        }
    }

    wirePerformanceMetricCorrections(document);
})();
