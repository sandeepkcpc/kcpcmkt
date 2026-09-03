/**
 * ENG-054: Reassign's "New Cameraperson(s)"/"New Assignee(s)" is Task Stage-dependent - two
 * separate Business-Role-filtered `.kcpc-model-picker` instances (one per TaskStage, both
 * server-rendered and each already wired by model-picker.js) sit in the form together; only the
 * one matching the current Task Stage selection is shown, toggled client-side with no reload/AJAX.
 * Switching stages clears the previously-visible picker's selection entirely (unchecking every box
 * and firing its `change` event so model-picker.js removes the corresponding chip) so a stray
 * Cameraperson never rides along into an Editor reassignment, or vice versa. "Confirm Reassignment"
 * stays disabled until the active assignee picker has at least one checked assignee AND Reason is
 * non-blank.
 *
 * ENG-102: Model(s)/Talent is a separate, SHOOTING-only `.reassign-model-picker` (own checkbox
 * name newModelUserIds) that rides along with the same stage-toggle/clear-on-switch behavior as the
 * assignee pickers above, but is optional - it never gates "Confirm Reassignment" the way the
 * Cameraperson/Editor picker's own selection does (see modelPickers below, kept out of
 * `pickers`/`activePicker()`/`updateSubmitState()` entirely for that reason).
 */
(function () {
    var form = document.getElementById('reassign-form');
    var stageSelect = document.getElementById('reassign-task-stage');
    if (!form || !stageSelect) {
        return;
    }
    var pickers = form.querySelectorAll('.reassign-assignee-picker');
    var modelPickers = form.querySelectorAll('.reassign-model-picker');
    var reasonInput = form.querySelector('input[name="reason"]');
    var submitBtn = form.querySelector('button[type="submit"]');

    function activePicker() {
        for (var i = 0; i < pickers.length; i++) {
            if (pickers[i].getAttribute('data-stage') === stageSelect.value) {
                return pickers[i];
            }
        }
        return null;
    }

    function updateSubmitState() {
        if (!submitBtn) {
            return;
        }
        var picker = activePicker();
        var anyChecked = !!(picker && picker.querySelector('input[type="checkbox"]:checked'));
        var hasReason = !!(reasonInput && reasonInput.value.trim() !== '');
        submitBtn.disabled = !(anyChecked && hasReason);
    }

    function syncGroup(group, stage) {
        for (var i = 0; i < group.length; i++) {
            var picker = group[i];
            var isMatch = picker.getAttribute('data-stage') === stage;
            if (!isMatch) {
                var checkboxes = picker.querySelectorAll('input[type="checkbox"]:checked');
                for (var j = 0; j < checkboxes.length; j++) {
                    checkboxes[j].checked = false;
                    checkboxes[j].dispatchEvent(new Event('change'));
                }
            }
            picker.classList.toggle('hidden', !isMatch);
        }
    }

    function syncStage() {
        var stage = stageSelect.value;
        syncGroup(pickers, stage);
        syncGroup(modelPickers, stage);
        updateSubmitState();
    }

    stageSelect.addEventListener('change', syncStage);
    if (reasonInput) {
        reasonInput.addEventListener('input', updateSubmitState);
    }
    for (var i = 0; i < pickers.length; i++) {
        var boxes = pickers[i].querySelectorAll('input[type="checkbox"]');
        for (var j = 0; j < boxes.length; j++) {
            boxes[j].addEventListener('change', updateSubmitState);
        }
    }

    syncStage();
})();
