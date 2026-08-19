/**
 * ENG-055: the Publishing execution checklist - checking a row's box enables (un-disables) its
 * hidden plannedOutputId/publicationTargetId inputs and its Evidence URL field, which is how those
 * fields get included in the POST at all (disabled form fields are never submitted). That's what
 * keeps the three parallel lists the server expects (plannedOutputIds/publicationTargetIds/
 * evidenceUrls) in perfect lockstep with zero risk of misalignment - a row's three fields are only
 * ever enabled/disabled together, never independently. "Submit Published Tasks" stays disabled
 * until at least one row is checked AND every checked row's Evidence URL is filled in.
 */
(function () {
    var table = document.getElementById('publishing-checklist-table');
    var form = document.getElementById('publishing-checklist-form');
    var submitBtn = document.getElementById('publishing-checklist-submit');
    var selectAll = document.getElementById('publishing-checklist-select-all');
    if (!table || !form || !submitBtn) {
        return;
    }
    var rowCheckboxes = table.querySelectorAll('.publishing-checklist-select');

    function rowOf(checkbox) {
        return checkbox.closest('.publishing-checklist-row');
    }

    function syncRow(checkbox) {
        var row = rowOf(checkbox);
        if (!row) {
            return;
        }
        var hiddenInputs = row.querySelectorAll('.publishing-checklist-hidden');
        var evidenceInput = row.querySelector('.publishing-checklist-evidence');
        var enabled = checkbox.checked;
        for (var i = 0; i < hiddenInputs.length; i++) {
            hiddenInputs[i].disabled = !enabled;
        }
        if (evidenceInput) {
            evidenceInput.disabled = !enabled;
            evidenceInput.required = enabled;
            if (!enabled) {
                evidenceInput.value = '';
            }
        }
    }

    function updateSubmitState() {
        var anyChecked = false;
        var allEvidenceFilled = true;
        for (var i = 0; i < rowCheckboxes.length; i++) {
            if (!rowCheckboxes[i].checked) {
                continue;
            }
            anyChecked = true;
            var row = rowOf(rowCheckboxes[i]);
            var evidenceInput = row ? row.querySelector('.publishing-checklist-evidence') : null;
            if (!evidenceInput || evidenceInput.value.trim() === '') {
                allEvidenceFilled = false;
            }
        }
        submitBtn.disabled = !(anyChecked && allEvidenceFilled);
    }

    for (var i = 0; i < rowCheckboxes.length; i++) {
        rowCheckboxes[i].addEventListener('change', function (event) {
            syncRow(event.target);
            updateSubmitState();
        });
    }
    table.addEventListener('input', function (event) {
        if (event.target.classList.contains('publishing-checklist-evidence')) {
            updateSubmitState();
        }
    });

    if (selectAll) {
        selectAll.addEventListener('change', function () {
            for (var i = 0; i < rowCheckboxes.length; i++) {
                rowCheckboxes[i].checked = selectAll.checked;
                syncRow(rowCheckboxes[i]);
            }
            updateSubmitState();
        });
    }

    updateSubmitState();
})();
