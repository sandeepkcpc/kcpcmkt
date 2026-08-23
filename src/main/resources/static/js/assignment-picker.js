/**
 * Assignment pickers (Shoot/Edit/Publishing "...(s)" fields): the same searchable, checkbox-backed
 * multi-select-as-chips UX as the Model(s) picker (model-picker.js) and reusing its CSS classes
 * (kcpc-model-picker/kcpc-model-input/kcpc-model-chips/model-chip/kcpc-model-search/kcpc-model-checklist/
 * model-check-item).
 *
 * Explicit user request: checking a box only STAGES a chip locally (instant visual feedback, matching
 * Model(s)); nothing is saved until the single "Assign ...(s)" button is clicked, which persists every
 * currently staged chip AND the selected Team Lead (Shoot/Edit only - Publishing has no Lead concept)
 * together in ONE batch AJAX call to a combined "team" endpoint (no reload) - mirrors
 * publication-scope.js's "+ Add Target" pattern (check boxes, one button saves the batch), not a
 * per-checkbox network call. A STAGED chip's own x just un-stages it locally (nothing to undo
 * server-side yet); once a chip is actually saved, its x becomes a real (still no-reload) removal.
 *
 * ENG-041: this used to be two separate buttons/forms/requests (an "Assign ...(s)" add-form plus a
 * "Set Lead" lead-select-form). The user asked for one button doing both in a single
 * transaction/request, so the Lead <select> now lives inside the same .assignment-add-form as the
 * checklist and both are submitted together - see PlanningService#assignShootTeam /
 * EditingService#assignEditTeam on the server side.
 *
 * Progressive enhancement, not a requirement: without JS, the checklist and Lead <select> live inside
 * a real <form class="assignment-add-form"> with its own "Assign ...(s)" submit button (batch-adds
 * every checked box and sets the Lead together, page reload), and every existing chip is its own tiny
 * <form class="chip-remove-form"> with a working "x" submit button (page reload) - both fully
 * functional server round-trips. This script layers the no-reload UX on top once it has run (same
 * principle documented in model-picker.js), it does not change what the button/form fundamentally does.
 */
(function () {
    function ajaxPost(action, csrfName, csrfValue, paramName, paramValue) {
        var params = new URLSearchParams();
        params.append(csrfName, csrfValue);
        params.append(paramName, paramValue);
        return fetch(action, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'fetch'
            },
            body: params.toString()
        });
    }

    function showAjaxError(picker, message) {
        var existing = picker.querySelector('.ajax-error');
        if (existing) {
            existing.remove();
        }
        var el = document.createElement('div');
        el.className = 'ajax-error';
        el.textContent = message;
        picker.appendChild(el);
        setTimeout(function () {
            el.remove();
        }, 4000);
    }

    /** staged=true: not yet saved server-side - its x just un-stages it locally, no network call. */
    function buildChipForm(picker, userId, name, staged) {
        var removeAction = picker.getAttribute('data-remove-action');
        var paramName = picker.getAttribute('data-param-name');
        var csrfInput = picker.querySelector('.assignment-add-form input[type="hidden"]');

        var form = document.createElement('form');
        form.className = 'chip-remove-form';
        form.method = 'post';
        form.setAttribute('action', removeAction);

        var csrfHidden = document.createElement('input');
        csrfHidden.type = 'hidden';
        csrfHidden.name = csrfInput.name;
        csrfHidden.value = csrfInput.value;
        form.appendChild(csrfHidden);

        var valueHidden = document.createElement('input');
        valueHidden.type = 'hidden';
        valueHidden.name = paramName;
        valueHidden.value = userId;
        form.appendChild(valueHidden);

        var chip = document.createElement('span');
        chip.className = 'model-chip';
        chip.setAttribute('data-user-id', userId);
        chip.setAttribute('data-name', name);
        if (staged) {
            chip.setAttribute('data-staged', 'true');
        }
        chip.appendChild(document.createTextNode(name + ' '));

        var button = document.createElement('button');
        button.type = 'submit';
        button.className = 'chip-remove';
        button.title = 'Remove ' + name;
        button.textContent = '×';
        chip.appendChild(button);

        form.appendChild(chip);
        return form;
    }

    function buildLeadOption(userId, name) {
        var option = document.createElement('option');
        option.value = userId;
        option.textContent = name;
        return option;
    }

    /** Every currently visible chip (staged AND saved) - the full pool the Lead dropdown draws from. */
    function getSelectedChips(picker) {
        var chips = picker.querySelectorAll('.kcpc-model-chips .model-chip');
        return Array.prototype.map.call(chips, function (chip) {
            return {id: chip.getAttribute('data-user-id'), name: chip.getAttribute('data-name')};
        });
    }

    /**
     * Rebuilds the Lead <select>'s options from the current chip set - a still-STAGED (unsaved)
     * chip counts too, so you can pick a Lead from people you're about to assign in the same click,
     * not only from already-saved assignees. Keeps the current selection if it's still in the new
     * list; otherwise it falls back to the first option ("— None —"), which is exactly the "Lead
     * clears when their assignee chip is removed" rule with no extra logic needed.
     */
    function refreshLeadOptions(picker) {
        var leadSelect = picker.querySelector('.kcpc-lead-select');
        if (!leadSelect) {
            return;
        }
        var selected = getSelectedChips(picker);
        var currentValue = leadSelect.value;
        leadSelect.innerHTML = '';
        var noneOption = document.createElement('option');
        noneOption.value = '';
        noneOption.textContent = '— None —';
        leadSelect.appendChild(noneOption);
        selected.forEach(function (editor) {
            leadSelect.appendChild(buildLeadOption(editor.id, editor.name));
        });
        leadSelect.disabled = selected.length === 0;
        if (selected.some(function (e) { return e.id === currentValue; })) {
            leadSelect.value = currentValue;
        }
    }

    /**
     * The "Assign ...(s)" submit button is normally nested inside the picker (Shoot/Edit), but
     * Publishing's modal footer places it outside .kcpc-assignment-picker entirely (shared with
     * "Cancel" in a modal-wide footer) and associates it to the form purely via the HTML
     * <button form="..."> attribute - so it must also be findable that way, or every button-state
     * call below silently no-ops for Publishing (the actual root cause of ENG-Publishing-assign:
     * the button always stayed in its default enabled/no-feedback state because this lookup never
     * found it, not because the click/submit/fetch chain itself was broken).
     */
    function findAssignSubmitButton(picker, addForm) {
        var inline = picker.querySelector('.assignment-add-submit');
        if (inline) {
            return inline;
        }
        return addForm && addForm.id
            ? document.querySelector('button.assignment-add-submit[form="' + addForm.id + '"]')
            : null;
    }

    /** Only clickable once there's something to save: a staged chip, or a changed Lead selection. */
    function updateAssignButtonState(picker) {
        var addForm = picker.querySelector('.assignment-add-form');
        var addSubmit = findAssignSubmitButton(picker, addForm);
        if (!addSubmit) {
            return;
        }
        var hasStagedChip = !!picker.querySelector('.kcpc-model-chips .model-chip[data-staged="true"]');
        var leadSelect = picker.querySelector('.kcpc-lead-select');
        var leadChanged = leadSelect && leadSelect.value !== (leadSelect.getAttribute('data-current-lead') || '');
        addSubmit.disabled = !hasStagedChip && !leadChanged;
    }

    function buildChecklistItem(picker, userId, name) {
        var paramName = picker.querySelector('.kcpc-model-checklist input[type="checkbox"]');
        var fieldName = paramName ? paramName.name : picker.getAttribute('data-param-name') + 's';

        var label = document.createElement('label');
        label.className = 'model-check-item';

        var checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.name = fieldName;
        checkbox.value = userId;
        checkbox.setAttribute('data-name', name);
        label.appendChild(checkbox);
        label.appendChild(document.createTextNode(' ' + name));
        return label;
    }

    function initPicker(picker) {
        var chips = picker.querySelector('.kcpc-model-chips');
        var search = picker.querySelector('.kcpc-model-search');
        var checklist = picker.querySelector('.kcpc-model-checklist');
        var addForm = picker.querySelector('.assignment-add-form');
        var input = picker.querySelector('.kcpc-model-input');
        if (!chips || !search || !checklist || !addForm) {
            return;
        }
        var addAction = picker.getAttribute('data-add-action');
        var paramName = picker.getAttribute('data-param-name');
        var csrfInput = addForm.querySelector('input[type="hidden"]');

        // Team Lead (Shoot/Edit only - Publishing has no .kcpc-lead-select, everything below is a
        // no-op then). The Lead options are always a subset of the current SAVED assignee chips
        // (never a still-staged/unsaved one), kept in sync as chips are saved/removed. It now lives
        // inside the same addForm as the checklist (ENG-041) so both submit together.
        var leadSelect = addForm.querySelector('.kcpc-lead-select');

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

        if (leadSelect) {
            leadSelect.setAttribute('data-current-lead', leadSelect.value);
        }
        updateAssignButtonState(picker);

        input.addEventListener('click', function () {
            openDropdown();
            search.focus();
        });
        search.addEventListener('focus', openDropdown);
        search.addEventListener('input', filterChecklist);
        if (leadSelect) {
            leadSelect.addEventListener('change', function () {
                updateAssignButtonState(picker);
            });
        }
        document.addEventListener('click', function (event) {
            if (!picker.contains(event.target)) {
                closeDropdown();
            }
        });

        // Checking a box only stages a chip locally - nothing is saved until "+ Assign" is clicked.
        checklist.addEventListener('change', function (event) {
            var checkbox = event.target;
            if (!checkbox.matches('input[type="checkbox"]') || !checkbox.checked) {
                return;
            }
            var userId = checkbox.value;
            var name = checkbox.getAttribute('data-name') || '';
            var item = checkbox.closest('.model-check-item');

            item.remove();
            chips.appendChild(buildChipForm(picker, userId, name, true));
            refreshLeadOptions(picker);
            updateAssignButtonState(picker);
        });

        // Single "Assign ...(s)" button (ENG-041): batch-saves every currently staged chip AND the
        // selected Team Lead together in one AJAX call to the combined "team" endpoint, no reload.
        addForm.addEventListener('submit', function (event) {
            event.preventDefault();
            var stagedChips = Array.prototype.slice.call(chips.querySelectorAll('.model-chip[data-staged="true"]'));
            var leadValue = leadSelect ? leadSelect.value : null;
            var currentLead = leadSelect ? (leadSelect.getAttribute('data-current-lead') || '') : null;
            var leadChanged = leadSelect ? leadValue !== currentLead : false;
            if (stagedChips.length === 0 && !leadChanged) {
                // Purely an observability hook for callers that want to react (e.g. Publishing's
                // modal shows its own "Select at least one Publisher." message for this) - Shoot/
                // Edit have no listener for it, so nothing changes for them; they still just no-op
                // exactly as before.
                picker.dispatchEvent(new CustomEvent('kcpc:assignment-empty-selection', {bubbles: true}));
                return;
            }
            var checkboxSample = picker.querySelector('.kcpc-model-checklist input[type="checkbox"]');
            var pluralField = checkboxSample ? checkboxSample.name : paramName + 's';
            var userIds = stagedChips.map(function (c) {
                return c.getAttribute('data-user-id');
            });

            var params = new URLSearchParams();
            params.append(csrfInput.name, csrfInput.value);
            userIds.forEach(function (v) {
                params.append(pluralField, v);
            });
            if (leadSelect) {
                params.append(leadSelect.name, leadValue);
            }

            // Disable for the round-trip, both as a "processing" cue and to stop a rapid double-
            // click from firing a second overlapping request (the backend is already idempotent on
            // this specific POST - see PublishingService#assignPublisher's existing-assignment
            // short-circuit - but there is no reason to rely on that alone for normal clicking).
            var addSubmit = findAssignSubmitButton(picker, addForm);
            if (addSubmit) {
                addSubmit.disabled = true;
            }

            fetch(addAction, {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'fetch'
                },
                body: params.toString()
            }).then(function (response) {
                if (!response.ok) {
                    throw new Error('assign-failed');
                }
                stagedChips.forEach(function (chip) {
                    chip.removeAttribute('data-staged');
                });
                if (leadSelect) {
                    // The staged chips' options were already in the dropdown before this submit
                    // (refreshLeadOptions runs as soon as a box is checked) - just re-derive the
                    // options from the now-all-saved chip set and restore the chosen value.
                    refreshLeadOptions(picker);
                    leadSelect.value = leadValue;
                    leadSelect.setAttribute('data-current-lead', leadValue);
                }
                updateAssignButtonState(picker);
                // Same observability hook as above, for a successful save - Publishing's modal
                // reloads the page on this (its own established "action succeeds -> full refresh"
                // pattern, same as every other Content Detail Action Center form); Shoot/Edit have
                // no listener, so their existing no-reload chip-based UX is completely unchanged.
                picker.dispatchEvent(new CustomEvent('kcpc:assignment-saved', {bubbles: true, detail: {userIds: userIds}}));
            }).catch(function () {
                if (addSubmit) {
                    addSubmit.disabled = false;
                }
                updateAssignButtonState(picker);
                showAjaxError(picker, 'Could not save the assignment. Please try again.');
            });
        });
    }

    var pickers = document.querySelectorAll('.kcpc-assignment-picker');
    for (var p = 0; p < pickers.length; p++) {
        initPicker(pickers[p]);
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form.classList || !form.classList.contains('chip-remove-form')) {
            return;
        }
        var picker = form.closest('.kcpc-assignment-picker');
        if (!picker) {
            return; // not one of ours - e.g. the Publication Scope channel chips (publication-scope.js handles those)
        }
        event.preventDefault();

        var chip = form.querySelector('.model-chip');
        var userId = chip.getAttribute('data-user-id');
        var name = chip.getAttribute('data-name');
        var checklist = picker.querySelector('.kcpc-model-checklist');
        var leadSelect = picker.querySelector('.kcpc-lead-select');

        if (chip.getAttribute('data-staged') === 'true') {
            // Never saved - just un-stage it locally, no network call needed.
            form.remove();
            checklist.appendChild(buildChecklistItem(picker, userId, name));
            refreshLeadOptions(picker);
            updateAssignButtonState(picker);
            return;
        }

        var csrfInput = form.querySelector('input[type="hidden"]');
        var removeAction = picker.getAttribute('data-remove-action');
        var paramName = picker.getAttribute('data-param-name');

        // If this person was the selected Lead, remember it so a failed removal can restore the
        // selection - refreshLeadOptions() itself already falls back to "— None —" once their chip
        // (and so their option) is gone, which is exactly the "Lead clears when their assignee chip
        // is removed" rule with no extra logic needed for the optimistic/success path.
        var wasSelectedLead = leadSelect && leadSelect.value === userId;

        form.remove();
        refreshLeadOptions(picker);
        updateAssignButtonState(picker);

        ajaxPost(removeAction, csrfInput.name, csrfInput.value, paramName, userId).then(function (response) {
            if (!response.ok) {
                throw new Error('remove-failed');
            }
            // Removed for real - the person becomes available to re-assign again.
            checklist.appendChild(buildChecklistItem(picker, userId, name));
            if (leadSelect && leadSelect.getAttribute('data-current-lead') === userId) {
                leadSelect.setAttribute('data-current-lead', '');
            }
            updateAssignButtonState(picker);
        }).catch(function () {
            picker.querySelector('.kcpc-model-chips').appendChild(buildChipForm(picker, userId, name, false));
            refreshLeadOptions(picker);
            if (leadSelect && wasSelectedLead) {
                leadSelect.value = userId;
            }
            updateAssignButtonState(picker);
            showAjaxError(picker, 'Could not remove ' + name + '. Please try again.');
        });
    });
})();
