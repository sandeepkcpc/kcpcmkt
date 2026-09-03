/**
 * Stages picker (ENG-091): a fixed 3-option (Shoot/Edit/Publishing) "starting point" selector for
 * Idea Review's Planning Details form, used by both reviews-ideas.jspf (AJAX, reviews-workspace.js
 * reads its checked state) and idea-detail.jsp (real form POST - checkbox values submit natively).
 * Deliberately its own small component, not a reuse of .kcpc-model-picker (checkbox-list-of-people
 * semantics don't fit a fixed 3-option cascading selector) - built on the same <details>/<summary>
 * pattern the Planned Outputs grid's own platform picker already uses, for visual consistency.
 *
 * Only 3 combinations are ever valid - {SHOOT,EDIT,PUBLISHING}, {EDIT,PUBLISHING}, {PUBLISHING} -
 * enforced here purely for UX (the server re-validates independently, never trusting the client):
 * PUBLISHING can never be unchecked; checking SHOOT also checks EDIT; unchecking EDIT also
 * unchecks SHOOT (SHOOT always implies EDIT downstream in the starting-point model).
 *
 * Exposed as window.initStagesPicker(root), mirroring model-picker.js's initModelPickers(root)
 * convention. Dispatches a bubbling 'kcpc:stages-changed' custom event (detail: {stages: [...]})
 * on every change so each page's own show/hide logic reacts without this file knowing anything
 * about Schedule/Assignment sections.
 */
(function () {
    var STAGE_ORDER = ['SHOOT', 'EDIT', 'PUBLISHING'];
    var STAGE_LABELS = {SHOOT: 'Shoot', EDIT: 'Edit', PUBLISHING: 'Publishing'};

    function initPicker(picker) {
        var details = picker;
        var chipsBox = picker.querySelector('.kcpc-stages-chips');
        var checkboxes = picker.querySelectorAll('input[type="checkbox"]');
        if (!chipsBox || !checkboxes.length) {
            return;
        }

        function checkboxFor(stage) {
            for (var i = 0; i < checkboxes.length; i++) {
                if (checkboxes[i].value === stage) {
                    return checkboxes[i];
                }
            }
            return null;
        }

        function checkedStages() {
            var result = [];
            STAGE_ORDER.forEach(function (stage) {
                var cb = checkboxFor(stage);
                if (cb && cb.checked) {
                    result.push(stage);
                }
            });
            return result;
        }

        function renderChips() {
            chipsBox.innerHTML = '';
            checkedStages().forEach(function (stage) {
                var chip = document.createElement('span');
                chip.className = 'model-chip';
                chip.appendChild(document.createTextNode(STAGE_LABELS[stage] + ' '));
                if (stage !== 'PUBLISHING') {
                    var remove = document.createElement('button');
                    remove.type = 'button';
                    remove.className = 'chip-remove';
                    remove.setAttribute('aria-label', 'Remove ' + STAGE_LABELS[stage]);
                    remove.textContent = '×';
                    remove.addEventListener('click', function (event) {
                        event.stopPropagation();
                        checkboxFor(stage).checked = false;
                        applyCascade(stage);
                    });
                    chip.appendChild(remove);
                }
                chipsBox.appendChild(chip);
            });
        }

        function applyCascade(changedStage) {
            var publishing = checkboxFor('PUBLISHING');
            var edit = checkboxFor('EDIT');
            var shoot = checkboxFor('SHOOT');
            // PUBLISHING can never be unchecked - it's always the pipeline's last stage.
            publishing.checked = true;
            if (changedStage === 'SHOOT' && shoot.checked) {
                edit.checked = true;
            }
            if (changedStage === 'EDIT' && !edit.checked) {
                shoot.checked = false;
            }
            renderChips();
            if (details.tagName === 'DETAILS') {
                details.open = false;
            }
            picker.dispatchEvent(new CustomEvent('kcpc:stages-changed', {
                bubbles: true,
                detail: {stages: checkedStages()}
            }));
        }

        for (var i = 0; i < checkboxes.length; i++) {
            checkboxes[i].addEventListener('change', function (event) {
                applyCascade(event.target.value);
            });
        }
        document.addEventListener('click', function (event) {
            if (details.tagName === 'DETAILS' && details.open && !picker.contains(event.target)) {
                details.open = false;
            }
        });

        renderChips();
    }

    function initStagesPicker(root) {
        var pickers = (root || document).querySelectorAll('.kcpc-stages-picker');
        for (var p = 0; p < pickers.length; p++) {
            initPicker(pickers[p]);
        }
    }

    window.initStagesPicker = initStagesPicker;
    initStagesPicker(document);
})();
