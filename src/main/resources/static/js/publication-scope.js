/**
 * Planning Workspace dependent-field behaviour (Publication Scope "+ Add Target" and Planned
 * Output "Output Type" / "Reel Type"). Two independent, self-contained enhancements:
 *
 * 1. Platform -> Channel: selecting a Platform filters the Channel checklist below it to only
 *    that Platform's channels, and resets any previous selection so a Channel from one Platform
 *    can never be submitted while a different Platform is selected.
 * 2. Output Type -> Reel Type: the Reel Type field (checkbox multi-select in "+ Add Output" and
 *    in the per-row Edit form) is only shown while Output Type is REEL; switching away from REEL
 *    hides it, disables its checkboxes and clears whatever was selected, matching ERD-CON-008
 *    (Reel Type is mandatory for REEL and must be blank otherwise).
 *
 * Progressive enhancement, not a requirement: the server renders every Channel checkbox visible
 * and the Reel Type field visible/enabled by default, so both forms are fully usable with
 * JavaScript disabled (the server still rejects a Reel Type submitted against a non-REEL Output
 * Type) - this script only narrows/gates the choices once it has run.
 */
(function () {
    function filterChannels(select) {
        var form = select.closest('form');
        if (!form) {
            return;
        }
        var checklist = form.querySelector('.kcpc-channel-checklist');
        if (!checklist) {
            return;
        }
        var selectedPlatform = select.value;

        // The whole Channels section (label + checklist) stays hidden until a Platform is
        // chosen - not just each non-matching item - so "Select Platform" shows no empty box.
        var group = form.querySelector('.kcpc-channel-group');
        if (group) {
            group.classList.toggle('hidden', selectedPlatform === '');
        }

        var items = checklist.querySelectorAll('.channel-check-item');
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            var matches = selectedPlatform !== '' && item.getAttribute('data-platform') === selectedPlatform;
            item.classList.toggle('hidden', !matches);
            if (!matches) {
                var checkbox = item.querySelector('input[type="checkbox"]');
                if (checkbox) {
                    checkbox.checked = false;
                }
            }
        }
    }

    function toggleReelType(outputTypeSelect) {
        var form = outputTypeSelect.closest('form');
        if (!form) {
            return;
        }
        var isReel = outputTypeSelect.value === 'REEL';

        var group = form.querySelector('.kcpc-reeltype-group'); // Add Output + Edit: checkbox multi-select
        if (group) {
            group.classList.toggle('hidden', !isReel);
            var checkboxes = group.querySelectorAll('input[type="checkbox"]');
            for (var i = 0; i < checkboxes.length; i++) {
                checkboxes[i].disabled = !isReel;
                if (!isReel) {
                    checkboxes[i].checked = false;
                }
            }
        }
    }

    var platformSelects = document.querySelectorAll('.kcpc-platform-select');
    for (var p = 0; p < platformSelects.length; p++) {
        filterChannels(platformSelects[p]); // start with the checklist narrowed to "no Platform chosen yet"
    }

    var outputTypeSelects = document.querySelectorAll('.kcpc-output-type-select');
    for (var o = 0; o < outputTypeSelects.length; o++) {
        toggleReelType(outputTypeSelects[o]); // start matching whatever Output Type is currently selected
    }

    document.addEventListener('change', function (event) {
        var target = event.target;
        if (!target.classList) {
            return;
        }
        if (target.classList.contains('kcpc-platform-select')) {
            filterChannels(target);
        } else if (target.classList.contains('kcpc-output-type-select')) {
            toggleReelType(target);
        }
    });

    // ---- Publication Scope add/remove: submitted via fetch so chip edits never reload the
    // page. Both forms stay normal <form method="post"> elements (the fully-functional no-JS
    // fallback - the server tells the two POST endpoints apart via the X-Requested-With header
    // this script adds, and only skips the redirect+flash-message response for those). ----

    function serializeForm(form) {
        var params = new URLSearchParams();
        var formData = new FormData(form);
        formData.forEach(function (value, key) {
            params.append(key, value);
        });
        return params.toString();
    }

    function ajaxPost(form) {
        return fetch(form.getAttribute('action'), {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'fetch'
            },
            body: serializeForm(form)
        });
    }

    function showAjaxError(list, message) {
        var existing = list.querySelector('.ajax-error');
        if (existing) {
            existing.remove();
        }
        var el = document.createElement('div');
        el.className = 'ajax-error';
        el.textContent = message;
        list.appendChild(el);
        setTimeout(function () {
            el.remove();
        }, 4000);
    }

    function handleChipRemoveSubmit(form) {
        var chip = form.closest('.channel-chip');
        var list = form.closest('.target-list');
        if (!chip || !list) {
            return;
        }
        var row = chip.closest('.target-row');
        var channelsContainer = chip.parentElement;
        var wasOnlyChipInRow = channelsContainer.querySelectorAll('.channel-chip').length === 1;
        var nextSibling = chip.nextSibling;
        var channelHandle = chip.getAttribute('data-channel-handle') || chip.textContent.trim();
        var platformName = row ? (row.querySelector('.target-platform') || {}).textContent : '';

        // Optimistic removal: the chip (and its now-empty platform row) disappears immediately;
        // a failed request restores both and surfaces an error, so the UI never silently drifts
        // from what's actually saved.
        chip.remove();
        if (wasOnlyChipInRow) {
            row.style.display = 'none';
        }

        ajaxPost(form).then(function (response) {
            if (!response.ok) {
                return extractErrorMessage(response, 'Could not remove channel. Please try again.').then(function (message) {
                    throw new Error(message);
                });
            }
            if (wasOnlyChipInRow) {
                row.remove();
            }
            if (!list.querySelector('.channel-chip') && !list.querySelector('.muted-summary')) {
                var muted = document.createElement('span');
                muted.className = 'muted-summary';
                muted.textContent = '(none yet)';
                list.appendChild(muted);
            }
            document.dispatchEvent(new CustomEvent('kcpc:scope-changed', {
                detail: {type: 'removed', groupId: list.getAttribute('data-group-id'), platform: platformName, channel: channelHandle}
            }));
        }).catch(function (err) {
            if (nextSibling) {
                channelsContainer.insertBefore(chip, nextSibling);
            } else {
                channelsContainer.appendChild(chip);
            }
            if (wasOnlyChipInRow) {
                row.style.display = '';
            }
            showAjaxError(list, (err && err.message) || 'Could not remove channel. Please try again.');
        });
    }

    function findOrCreateTargetRow(list, platformName) {
        var rows = list.querySelectorAll('.target-row');
        for (var i = 0; i < rows.length; i++) {
            var label = rows[i].querySelector('.target-platform');
            if (label && label.textContent === platformName) {
                return rows[i];
            }
        }
        var row = document.createElement('div');
        row.className = 'target-row';
        var platformSpan = document.createElement('span');
        platformSpan.className = 'target-platform';
        platformSpan.textContent = platformName;
        var channelsSpan = document.createElement('span');
        channelsSpan.className = 'target-channels';
        row.appendChild(platformSpan);
        row.appendChild(channelsSpan);
        list.appendChild(row);
        return row;
    }

    var PLATFORM_ICON_FILES = {
        Instagram: 'instagram.svg', Facebook: 'facebook.svg', YouTube: 'youtube.svg',
        Threads: 'threads.svg', Moj: 'moj.svg', TikTok: 'tiktok.svg'
    };

    function platformIconSrc(platformName) {
        var table = document.getElementById('planned-outputs-table');
        var iconsBase = table ? table.getAttribute('data-context-path') : '';
        return (iconsBase || '') + '/icons/platforms/' + (PLATFORM_ICON_FILES[platformName] || 'generic.svg');
    }

    function buildChip(removeActionBase, targetId, channelHandle, csrfInput, platformName) {
        var chip = document.createElement('span');
        chip.className = 'channel-chip';
        chip.setAttribute('data-target-id', targetId);
        chip.setAttribute('data-channel-handle', channelHandle);
        var icon = document.createElement('img');
        icon.className = 'scope-target-icon';
        icon.src = platformIconSrc(platformName);
        icon.alt = '';
        icon.width = 14;
        icon.height = 14;
        chip.appendChild(icon);
        chip.appendChild(document.createTextNode(channelHandle + ' '));

        var removeForm = document.createElement('form');
        removeForm.method = 'post';
        removeForm.className = 'chip-remove-form';
        removeForm.setAttribute('action', removeActionBase + '/' + targetId + '/remove');

        var hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = csrfInput.name;
        hidden.value = csrfInput.value;
        removeForm.appendChild(hidden);

        var button = document.createElement('button');
        button.type = 'submit';
        button.className = 'chip-remove';
        button.title = 'Remove ' + channelHandle;
        button.textContent = '×';
        removeForm.appendChild(button);

        chip.appendChild(removeForm);
        return chip;
    }

    function handleAddTargetSubmit(form) {
        var groupId = form.getAttribute('data-group-id');
        // Scoped to the form's own row rather than a global data-group-id lookup: the same
        // reelGroupId can legitimately appear twice in the DOM at once (the Planning tab's table
        // and the Assign Publisher(s) modal's verification table both render every output), and a
        // global lookup would always resolve to whichever copy comes first regardless of which
        // form was actually submitted.
        var row = form.closest('tr');
        var list = row ? row.querySelector('.target-list[data-group-id="' + groupId + '"]') : null;
        var checked = form.querySelectorAll('.kcpc-channel-checklist input[type="checkbox"]:checked');
        var csrfInput = form.querySelector('input[type="hidden"]');
        if (!list || !csrfInput || checked.length === 0) {
            return;
        }
        var selections = [];
        for (var i = 0; i < checked.length; i++) {
            selections.push({
                targetId: checked[i].value,
                platform: checked[i].getAttribute('data-platform'),
                channel: checked[i].getAttribute('data-channel')
            });
        }

        ajaxPost(form).then(function (response) {
            if (!response.ok) {
                return extractErrorMessage(response, 'Could not add target(s). Please try again.').then(function (message) {
                    throw new Error(message);
                });
            }
            var muted = list.querySelector('.muted-summary');
            if (muted) {
                muted.remove();
            }
            for (var j = 0; j < selections.length; j++) {
                var selection = selections[j];
                var targetRow = findOrCreateTargetRow(list, selection.platform);
                var channelsSpan = targetRow.querySelector('.target-channels');
                if (channelsSpan.querySelector('.channel-chip[data-target-id="' + selection.targetId + '"]')) {
                    continue; // already mapped - mapPublicationScope() is additive/idempotent too
                }
                channelsSpan.appendChild(buildChip(form.getAttribute('action'), selection.targetId, selection.channel, csrfInput, selection.platform));
                document.dispatchEvent(new CustomEvent('kcpc:scope-changed', {
                    detail: {type: 'added', groupId: groupId, platform: selection.platform, channel: selection.channel}
                }));
            }
            for (var k = 0; k < checked.length; k++) {
                checked[k].checked = false;
            }
        }).catch(function (err) {
            showAjaxError(list, (err && err.message) || 'Could not add target(s). Please try again.');
        });
    }

    // ---- Planned Output Add/Edit/Remove: same fetch-based approach as the Publication Scope
    // chips above, so adding, editing or removing a Planned Output never reloads the page either.
    // Add is the one case with no existing DOM row to update, so it needs enough static reference
    // data (Output/Reel Type names, active Platforms/Channels, the CSRF pair) to build the new
    // row's Edit/+ Add Target forms client-side - kept in one small JSON blob
    // (#kcpc-planning-options, written by DeliverableMvcController) rather than duplicated here,
    // so the server's enum/master-data lists stay the single source of truth. ----

    var planningOptions = (function () {
        var el = document.getElementById('kcpc-planning-options');
        if (!el) {
            return null;
        }
        try {
            return JSON.parse(el.textContent);
        } catch (e) {
            return null;
        }
    })();

    function outputsBase() {
        var table = document.getElementById('planned-outputs-table');
        if (!table) {
            return null;
        }
        return table.getAttribute('data-context-path') + '/app/deliverables/'
            + table.getAttribute('data-plan-id') + '/outputs';
    }

    function extractErrorMessage(response, fallback) {
        return response.json().then(function (body) {
            return (body && body.message) || fallback;
        }).catch(function () {
            return fallback;
        });
    }

    function setLoading(button, loadingText) {
        if (!button) {
            return;
        }
        button.dataset.originalText = button.textContent;
        button.textContent = loadingText;
        button.disabled = true;
    }

    function clearLoading(button) {
        if (!button || button.dataset.originalText === undefined) {
            return;
        }
        button.textContent = button.dataset.originalText;
        delete button.dataset.originalText;
        button.disabled = false;
    }

    function renderTitleCell(td, titleDescription) {
        td.textContent = titleDescription ? titleDescription : '—';
    }

    function renderOutputTypeCell(td, outputType, reelTypes) {
        while (td.firstChild) {
            td.removeChild(td.firstChild);
        }
        td.appendChild(document.createTextNode(outputType + ' '));
        if (outputType === 'REEL' && reelTypes) {
            for (var i = 0; i < reelTypes.length; i++) {
                var chip = document.createElement('span');
                chip.className = 'reeltype-chip';
                chip.textContent = reelTypes[i];
                td.appendChild(chip);
            }
        }
    }

    function hiddenCsrfInput() {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = planningOptions.csrfParamName;
        input.value = planningOptions.csrfToken;
        return input;
    }

    /** Mirrors the server-rendered row markup (deliverable-detail.jsp §2) for a brand-new group. */
    function buildOutputRowSkeleton(groupId) {
        var base = outputsBase();

        var tr = document.createElement('tr');
        tr.setAttribute('data-group-id', groupId);

        var titleTd = document.createElement('td');
        titleTd.className = 'output-title-cell';
        var typeTd = document.createElement('td');
        typeTd.className = 'output-type-cell';

        var targetsTd = document.createElement('td');
        var targetList = document.createElement('div');
        targetList.className = 'target-list';
        targetList.setAttribute('data-group-id', groupId);
        var muted = document.createElement('span');
        muted.className = 'muted-summary';
        muted.textContent = '(none yet)';
        targetList.appendChild(muted);
        targetsTd.appendChild(targetList);

        var actionTd = document.createElement('td');
        var stack = document.createElement('div');
        stack.className = 'action-stack';

        var details = document.createElement('details');
        var summary = document.createElement('summary');
        summary.textContent = 'Edit';
        details.appendChild(summary);

        var editForm = document.createElement('form');
        editForm.className = 'action-form edit-output-form';
        editForm.method = 'post';
        editForm.setAttribute('data-group-id', groupId);
        editForm.setAttribute('action', base + '/' + groupId + '/edit');
        editForm.appendChild(hiddenCsrfInput());

        var outputTypeLabel = document.createElement('label');
        outputTypeLabel.appendChild(document.createTextNode('Output Type'));
        var outputTypeSelect = document.createElement('select');
        outputTypeSelect.className = 'kcpc-output-type-select';
        outputTypeSelect.name = 'outputType';
        planningOptions.outputTypes.forEach(function (t) {
            var opt = document.createElement('option');
            opt.value = t;
            opt.textContent = t;
            outputTypeSelect.appendChild(opt);
        });
        outputTypeLabel.appendChild(outputTypeSelect);
        editForm.appendChild(outputTypeLabel);

        var reelGroupDiv = document.createElement('div');
        reelGroupDiv.className = 'kcpc-reeltype-group';
        var reelLabel = document.createElement('label');
        reelLabel.textContent = 'Reel Type (Reel only — select one or more)';
        reelGroupDiv.appendChild(reelLabel);
        var reelChecklist = document.createElement('div');
        reelChecklist.className = 'kcpc-reeltype-checklist';
        planningOptions.reelTypes.forEach(function (rt) {
            var item = document.createElement('label');
            item.className = 'reeltype-check-item';
            var cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.name = 'reelTypes';
            cb.value = rt;
            item.appendChild(cb);
            item.appendChild(document.createTextNode(' ' + rt));
            reelChecklist.appendChild(item);
        });
        reelGroupDiv.appendChild(reelChecklist);
        var reelNote = document.createElement('p');
        reelNote.className = 'muted';
        reelNote.textContent = 'All Reel Types in this group share one Publication Target set below — '
            + 'per-Reel-Type targets aren\'t supported.';
        reelGroupDiv.appendChild(reelNote);
        editForm.appendChild(reelGroupDiv);

        var descLabel = document.createElement('label');
        descLabel.appendChild(document.createTextNode('Description '));
        var descInput = document.createElement('input');
        descInput.type = 'text';
        descInput.name = 'titleDescription';
        descLabel.appendChild(descInput);
        editForm.appendChild(descLabel);

        var saveButton = document.createElement('button');
        saveButton.type = 'submit';
        saveButton.textContent = 'Save';
        editForm.appendChild(saveButton);
        details.appendChild(editForm);

        var addTargetForm = document.createElement('form');
        addTargetForm.className = 'action-form add-target-form';
        addTargetForm.method = 'post';
        addTargetForm.setAttribute('data-group-id', groupId);
        addTargetForm.setAttribute('action', base + '/' + groupId + '/targets');
        addTargetForm.appendChild(hiddenCsrfInput());

        var platformLabel = document.createElement('label');
        platformLabel.appendChild(document.createTextNode('+ Add Target — Platform'));
        var platformSelect = document.createElement('select');
        platformSelect.className = 'kcpc-platform-select';
        var noneOpt = document.createElement('option');
        noneOpt.value = '';
        noneOpt.textContent = 'Select Platform';
        platformSelect.appendChild(noneOpt);
        var platforms = [];
        planningOptions.targets.forEach(function (t) {
            if (platforms.indexOf(t.platform) === -1) {
                platforms.push(t.platform);
            }
        });
        platforms.sort();
        platforms.forEach(function (p) {
            var opt = document.createElement('option');
            opt.value = p;
            opt.textContent = p;
            platformSelect.appendChild(opt);
        });
        platformLabel.appendChild(platformSelect);
        addTargetForm.appendChild(platformLabel);

        var channelGroup = document.createElement('div');
        channelGroup.className = 'kcpc-channel-group hidden'; // hidden until a Platform is chosen, like the server-rendered rows

        var channelsLabel = document.createElement('label');
        channelsLabel.textContent = 'Channels';
        channelGroup.appendChild(channelsLabel);

        var channelChecklist = document.createElement('div');
        channelChecklist.className = 'kcpc-channel-checklist';
        planningOptions.targets.forEach(function (t) {
            var item = document.createElement('label');
            item.className = 'channel-check-item';
            item.setAttribute('data-platform', t.platform);
            var cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.name = 'publicationTargetIds';
            cb.value = t.id;
            cb.setAttribute('data-platform', t.platform);
            cb.setAttribute('data-channel', t.channel);
            item.appendChild(cb);
            item.appendChild(document.createTextNode(' ' + t.channel));
            channelChecklist.appendChild(item);
        });
        channelGroup.appendChild(channelChecklist);
        addTargetForm.appendChild(channelGroup);

        var addTargetButton = document.createElement('button');
        addTargetButton.type = 'submit';
        addTargetButton.textContent = '+ Add Target';
        addTargetForm.appendChild(addTargetButton);
        details.appendChild(addTargetForm);
        stack.appendChild(details);

        var removeForm = document.createElement('form');
        removeForm.className = 'action-form remove-output-form';
        removeForm.method = 'post';
        removeForm.setAttribute('data-group-id', groupId);
        removeForm.setAttribute('action', base + '/' + groupId + '/remove');
        removeForm.appendChild(hiddenCsrfInput());
        var removeButton = document.createElement('button');
        removeButton.type = 'submit';
        removeButton.className = 'secondary';
        removeButton.textContent = 'Remove';
        removeForm.appendChild(removeButton);
        stack.appendChild(removeForm);

        actionTd.appendChild(stack);

        tr.appendChild(titleTd);
        tr.appendChild(typeTd);
        tr.appendChild(targetsTd);
        tr.appendChild(actionTd);

        return { tr: tr, titleTd: titleTd, typeTd: typeTd, editForm: editForm };
    }

    /** Syncs an edit-output-form's own fields (Output Type/Reel Type/Description) to server data. */
    function syncEditForm(form, data) {
        var select = form.querySelector('.kcpc-output-type-select');
        if (select) {
            select.value = data.outputType;
            toggleReelType(select);
        }
        var checkboxes = form.querySelectorAll('.kcpc-reeltype-checklist input[type="checkbox"]');
        for (var i = 0; i < checkboxes.length; i++) {
            checkboxes[i].checked = !!data.reelTypes && data.reelTypes.indexOf(checkboxes[i].value) !== -1;
        }
        var descInput = form.querySelector('input[name="titleDescription"]');
        if (descInput) {
            descInput.value = data.titleDescription || '';
        }
    }

    function handleAddOutputSubmit(form) {
        var tbody = document.getElementById('planned-outputs-tbody');
        if (!planningOptions || !tbody) {
            return; // no JS-enhanced row builder available - fall back to the plain form submit
        }
        var button = form.querySelector('button[type="submit"]');
        setLoading(button, 'Adding...');

        ajaxPost(form).then(function (response) {
            if (!response.ok) {
                return extractErrorMessage(response, 'Could not add output. Please try again.').then(function (message) {
                    throw new Error(message);
                });
            }
            return response.json();
        }).then(function (data) {
            var refs = buildOutputRowSkeleton(data.groupId);
            renderTitleCell(refs.titleTd, data.titleDescription);
            renderOutputTypeCell(refs.typeTd, data.outputType, data.reelTypes);
            syncEditForm(refs.editForm, data); // the new row's own Edit form must match what was actually created

            var emptyRow = document.getElementById('planned-outputs-empty-row');
            emptyRow && emptyRow.classList.add('hidden');
            tbody.insertBefore(refs.tr, emptyRow || null);

            var outputTypeSelect = form.querySelector('.kcpc-output-type-select');
            form.reset();
            toggleReelType(outputTypeSelect);
            clearLoading(button);
        }).catch(function (err) {
            clearLoading(button);
            showAjaxError(form, err.message || 'Could not add output. Please try again.');
        });
    }

    function handleEditOutputSubmit(form) {
        var groupId = form.getAttribute('data-group-id');
        var tr = document.querySelector('#planned-outputs-tbody tr[data-group-id="' + groupId + '"]');
        if (!tr) {
            return;
        }
        var button = form.querySelector('button[type="submit"]');
        setLoading(button, 'Saving...');

        ajaxPost(form).then(function (response) {
            if (!response.ok) {
                return extractErrorMessage(response, 'Could not save changes. Please try again.').then(function (message) {
                    throw new Error(message);
                });
            }
            return response.json();
        }).then(function (data) {
            renderTitleCell(tr.querySelector('.output-title-cell'), data.titleDescription);
            renderOutputTypeCell(tr.querySelector('.output-type-cell'), data.outputType, data.reelTypes);
            syncEditForm(form, data);
            clearLoading(button);
        }).catch(function (err) {
            clearLoading(button);
            showAjaxError(form, err.message || 'Could not save changes. Please try again.');
        });
    }

    function handleRemoveOutputSubmit(form) {
        var groupId = form.getAttribute('data-group-id');
        var tr = document.querySelector('#planned-outputs-tbody tr[data-group-id="' + groupId + '"]');
        if (!tr) {
            return;
        }
        if (!window.confirm('Remove this Planned Output? This cannot be undone.')) {
            return;
        }
        var button = form.querySelector('button[type="submit"]');
        setLoading(button, 'Removing...');

        ajaxPost(form).then(function (response) {
            if (!response.ok) {
                return extractErrorMessage(response, 'Could not remove output. Please try again.').then(function (message) {
                    throw new Error(message);
                });
            }
            var tbody = document.getElementById('planned-outputs-tbody');
            tr.remove();
            var emptyRow = document.getElementById('planned-outputs-empty-row');
            if (tbody && emptyRow && !tbody.querySelector('tr[data-group-id]')) {
                emptyRow.classList.remove('hidden');
            }
        }).catch(function (err) {
            clearLoading(button);
            showAjaxError(form, err.message || 'Could not remove output. Please try again.');
        });
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form.classList) {
            return;
        }
        if (form.classList.contains('chip-remove-form')) {
            event.preventDefault();
            handleChipRemoveSubmit(form);
        } else if (form.classList.contains('add-target-form')) {
            event.preventDefault();
            handleAddTargetSubmit(form);
        } else if (form.classList.contains('add-output-form')) {
            if (!planningOptions) {
                return; // no JS-enhanced row builder available - fall back to the plain form submit
            }
            event.preventDefault();
            handleAddOutputSubmit(form);
        } else if (form.classList.contains('edit-output-form')) {
            event.preventDefault();
            handleEditOutputSubmit(form);
        } else if (form.classList.contains('remove-output-form')) {
            event.preventDefault();
            handleRemoveOutputSubmit(form);
        }
    });
})();
