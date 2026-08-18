/**
 * Model(s) picker: a searchable, checkbox-backed multi-select shown as removable chips inside
 * an input-like box (Planning Details "Model(s)"; also reused, ENG-045, for Planning's
 * Cameraperson(s) field). The underlying control is a plain checkbox list - it stays fully
 * visible and usable without JavaScript (every checkbox submits normally with its form, e.g.
 * modelUserIds/cameramanUserIds). This script layers the chip/search UI on top once it has run:
 * the checklist becomes a toggleable dropdown, and checking/unchecking a box (directly, or via a
 * chip's x) is mirrored as a chip.
 *
 * ENG-045: a picker MAY also contain a sibling `.kcpc-lead-select` (Shoot Lead) - if present, its
 * options are kept live-in-sync with whichever checkboxes are currently checked (rebuilt from
 * scratch on every change, same approach as assignment-picker.js's refreshLeadOptions), so picking
 * a Lead is always restricted to people currently selected, with no separate save step - everything
 * submits together with the checkboxes when the surrounding form is submitted.
 */
(function () {
    function initPicker(picker) {
        var input = picker.querySelector('.kcpc-model-input');
        var chips = picker.querySelector('.kcpc-model-chips');
        var search = picker.querySelector('.kcpc-model-search');
        var checklist = picker.querySelector('.kcpc-model-checklist');
        if (!input || !chips || !search || !checklist) {
            return;
        }
        var checkboxes = checklist.querySelectorAll('input[type="checkbox"]');
        var leadSelect = picker.querySelector('.kcpc-lead-select');

        function refreshLeadOptions() {
            if (!leadSelect) {
                return;
            }
            var currentValue = leadSelect.value;
            leadSelect.innerHTML = '';
            var noneOption = document.createElement('option');
            noneOption.value = '';
            noneOption.textContent = '— None —';
            leadSelect.appendChild(noneOption);
            var anyChecked = false;
            for (var i = 0; i < checkboxes.length; i++) {
                if (!checkboxes[i].checked) {
                    continue;
                }
                anyChecked = true;
                var option = document.createElement('option');
                option.value = checkboxes[i].value;
                option.textContent = checkboxes[i].getAttribute('data-name');
                leadSelect.appendChild(option);
            }
            leadSelect.disabled = !anyChecked;
            var stillValid = false;
            for (var j = 0; j < leadSelect.options.length; j++) {
                if (leadSelect.options[j].value === currentValue) {
                    stillValid = true;
                    break;
                }
            }
            leadSelect.value = stillValid ? currentValue : '';
        }

        function renderChips() {
            chips.innerHTML = '';
            for (var i = 0; i < checkboxes.length; i++) {
                var checkbox = checkboxes[i];
                if (!checkbox.checked) {
                    continue;
                }
                var chip = document.createElement('span');
                chip.className = 'model-chip';
                chip.appendChild(document.createTextNode(checkbox.getAttribute('data-name') + ' '));

                var remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'chip-remove';
                remove.setAttribute('data-value', checkbox.value);
                remove.setAttribute('aria-label', 'Remove ' + checkbox.getAttribute('data-name'));
                remove.textContent = '×';
                remove.addEventListener('click', function (event) {
                    event.stopPropagation();
                    var targetValue = event.currentTarget.getAttribute('data-value');
                    for (var j = 0; j < checkboxes.length; j++) {
                        if (checkboxes[j].value === targetValue) {
                            checkboxes[j].checked = false;
                        }
                    }
                    renderChips();
                    refreshLeadOptions();
                });
                chip.appendChild(remove);
                chips.appendChild(chip);
            }
        }

        function filterChecklist() {
            var term = search.value.trim().toLowerCase();
            var items = checklist.querySelectorAll('.model-check-item');
            for (var i = 0; i < items.length; i++) {
                var checkbox = items[i].querySelector('input');
                var name = (checkbox ? checkbox.getAttribute('data-name') : '') || '';
                items[i].classList.toggle('hidden', term !== '' && name.toLowerCase().indexOf(term) === -1);
            }
        }

        function openDropdown() {
            checklist.classList.add('open');
        }

        function closeDropdown() {
            checklist.classList.remove('open');
        }

        checklist.classList.add('kcpc-model-checklist-js');
        input.style.display = 'flex';
        search.style.display = 'inline-block';

        for (var i = 0; i < checkboxes.length; i++) {
            checkboxes[i].addEventListener('change', function () {
                renderChips();
                refreshLeadOptions();
            });
        }
        input.addEventListener('click', function () {
            openDropdown();
            search.focus();
        });
        search.addEventListener('focus', openDropdown);
        search.addEventListener('input', filterChecklist);
        document.addEventListener('click', function (event) {
            if (!picker.contains(event.target)) {
                closeDropdown();
            }
        });

        renderChips();
        refreshLeadOptions();
    }

    var pickers = document.querySelectorAll('.kcpc-model-picker');
    for (var p = 0; p < pickers.length; p++) {
        initPicker(pickers[p]);
    }
})();
