/**
 * Idea Detail / Review redesign - presentation-only glue, mirroring content-detail.js's own
 * pattern for its "Back to Pipeline" link:
 *   1. Back to Idea Queue - restores the Idea Queue's exact last-seen filter/sort/page state
 *      (written to sessionStorage by idea-queue-dashboard.js) instead of resetting to page 1/
 *      default filters.
 *   2. Review Decision form - purely cosmetic dynamic label text on the Reason field as Decision
 *      changes, plus a live character counter (same mechanics as idea-submit.js's counter). Never
 *      sets/removes the `required` attribute here - the mandatory-for-Reject rule stays enforced
 *      only by IdeaService.decide server-side (see the Reason label's own static text, which
 *      already states the exact rule); this script only updates helper wording, so there is no
 *      duplicated validation logic to drift out of sync with the backend.
 */
(function () {
    // --- Back to Idea Queue -----------------------------------------------------------------
    var backLink = document.getElementById('ideaDetailBackLink');
    if (backLink) {
        try {
            var lastQueueUrl = sessionStorage.getItem('kcpcIdeaQueueUrl');
            if (lastQueueUrl) {
                backLink.href = lastQueueUrl;
            }
        } catch (e) {
            // Storage unavailable - the link already has its default /app/ideas href.
        }
    }

    // --- Review Decision form ----------------------------------------------------------------
    var form = document.getElementById('idea-review-form');
    if (!form) {
        return;
    }
    var decisionField = document.getElementById('idea-review-decision');
    var reasonField = document.getElementById('idea-review-reason');
    var reasonLabel = document.getElementById('idea-review-reason-label');

    var REASON_HELP_TEXT = {
        '': 'Reason (mandatory for Reject; optional for Retain)',
        APPROVE: 'Reason (optional; not used for Approve)',
        REJECT: 'Reason * (mandatory for Reject)',
        RETAIN: 'Reason (optional for Retain)'
    };

    function updateReasonLabel() {
        if (!reasonLabel) {
            return;
        }
        var text = REASON_HELP_TEXT[decisionField.value] || REASON_HELP_TEXT[''];
        reasonLabel.textContent = text;
    }

    if (decisionField) {
        decisionField.addEventListener('change', updateReasonLabel);
    }

    // --- Planning Details block (workflow redesign: only relevant for Approve) ---------------
    var planningFields = document.getElementById('idea-review-planning-fields');
    var plannedLiveDateInput = form.querySelector('[name="plannedLiveDate"]');
    var planningModeSelect = document.getElementById('idea-review-planning-mode');
    var shootDateLabel = document.getElementById('idea-review-shoot-date-label');
    var editDateLabel = document.getElementById('idea-review-edit-date-label');
    var urgencyReasonLabel = document.getElementById('idea-review-urgency-reason-label');

    function updatePlanningVisibility() {
        if (!planningFields || !decisionField) {
            return;
        }
        var isApprove = decisionField.value === 'APPROVE';
        planningFields.classList.toggle('hidden', !isApprove);
        if (plannedLiveDateInput) {
            plannedLiveDateInput.required = isApprove;
        }
    }

    function updatePlanningModeFields() {
        if (!planningModeSelect) {
            return;
        }
        var isUrgent = planningModeSelect.value === 'URGENT';
        [shootDateLabel, editDateLabel, urgencyReasonLabel].forEach(function (label) {
            if (label) {
                label.classList.toggle('planning-field-required', isUrgent);
            }
        });
        var urgencyReasonInput = urgencyReasonLabel ? urgencyReasonLabel.querySelector('input') : null;
        if (urgencyReasonInput) {
            urgencyReasonInput.required = isUrgent;
        }
    }

    if (decisionField) {
        decisionField.addEventListener('change', updatePlanningVisibility);
        updatePlanningVisibility();
    }
    if (planningModeSelect) {
        planningModeSelect.addEventListener('change', updatePlanningModeFields);
        updatePlanningModeFields();
    }

    // --- Stages (ENG-091): picks where the pipeline starts - reacts live to stages-picker.js's
    // 'kcpc:stages-changed' event, no page reload. Editor(s)/Publisher(s) Assignment only appear
    // when Edit/Publishing is the starting stage (no earlier Shoot Review/Edit Review Approve
    // exists to fold that assignment into in those two cases) - the Standard case (Shoot selected)
    // is unchanged from before ENG-091. -------------------------------------------------------
    var stagesPicker = document.getElementById('idea-review-stages-picker');
    var shootAssignmentSection = document.getElementById('idea-review-shoot-assignment-section');
    var editorAssignmentSection = document.getElementById('idea-review-editor-assignment-section');
    var publisherAssignmentSection = document.getElementById('idea-review-publisher-assignment-section');
    var publisherLabel = document.getElementById('idea-review-publisher-label');
    var publisherHint = document.getElementById('idea-review-publisher-hint');
    var teamMarksRow = document.getElementById('idea-review-team-marks-row');
    var cameramanMarkField = document.getElementById('idea-review-cameraman-mark-field');
    var modelMarkField = document.getElementById('idea-review-model-mark-field');

    function ideaCheckedStages() {
        var checked = [];
        if (stagesPicker) {
            stagesPicker.querySelectorAll('input[type="checkbox"]:checked').forEach(function (cb) {
                checked.push(cb.value);
            });
        }
        return checked;
    }

    function updateStagesFields() {
        var stages = ideaCheckedStages();
        var shootStarts = stages.indexOf('SHOOT') !== -1;
        var editStarts = !shootStarts && stages.indexOf('EDIT') !== -1;
        var publishingStarts = !shootStarts && !editStarts;
        if (shootDateLabel) {
            shootDateLabel.classList.toggle('hidden', !shootStarts);
        }
        if (editDateLabel) {
            editDateLabel.classList.toggle('hidden', publishingStarts);
        }
        if (shootAssignmentSection) {
            shootAssignmentSection.classList.toggle('hidden', !shootStarts);
        }
        if (editorAssignmentSection) {
            editorAssignmentSection.classList.toggle('hidden', !editStarts);
        }
        // Publisher(s) is now mandatory for every stage combination (not just Direct Publishing) -
        // the required marker is always shown, matching IdeaService#approve's own unconditional
        // check. The old "optional here" hint no longer applies to any combination, so it stays
        // hidden always rather than showing stale wording.
        if (publisherLabel) {
            publisherLabel.textContent = 'Publisher(s) *';
        }
        if (publisherHint) {
            publisherHint.classList.add('hidden');
        }
        // ENG-096: Cameraperson/Model Marks only apply when Shoot is part of the pipeline; Editor
        // Mark whenever Edit is included (i.e. whenever the row itself isn't hidden - the row is
        // only hidden for Publishing-only, where editIncluded is always false too). Backend
        // (IdeaService#approve) is the actual authority here - it ignores any mark submitted for a
        // stage not in the pipeline regardless of what this hides, exactly like it already does for
        // camerapersonUserIds/editorUserIds/publisherUserIds above.
        if (teamMarksRow) {
            teamMarksRow.classList.toggle('hidden', publishingStarts);
        }
        if (cameramanMarkField) {
            cameramanMarkField.classList.toggle('hidden', !shootStarts);
        }
        if (modelMarkField) {
            modelMarkField.classList.toggle('hidden', !shootStarts);
        }
    }

    if (stagesPicker) {
        stagesPicker.addEventListener('kcpc:stages-changed', updateStagesFields);
        updateStagesFields();
    }

    // Checkbox groups have no native "at least one required" HTML5 validation - blocked here on
    // submit, same authoritative-server-still-validates intent as reviews-workspace.js's Idea
    // Approve blocking checks. Never intercepts/prevents the actual POST otherwise - this stays a
    // real native form submission.
    form.addEventListener('submit', function (event) {
        if (!decisionField || decisionField.value !== 'APPROVE') {
            return;
        }
        var stages = ideaCheckedStages();
        var shootStarts = stages.indexOf('SHOOT') !== -1;
        var editStarts = !shootStarts && stages.indexOf('EDIT') !== -1;
        var publishingStarts = !shootStarts && !editStarts;
        var editIncluded = !publishingStarts;
        var message = null;
        // ENG-093: Urgent Planning Mode only requires an explicit date for a stage that's actually
        // part of the pipeline - Stages decides that, Planning Mode never forces a date for a
        // stage Stages already excluded. Standard mode needs no check here - the server only ever
        // defaults, never requires, Shoot/Edit Date in Standard mode.
        if (planningModeSelect && planningModeSelect.value === 'URGENT') {
            var urgencyReasonInputForSubmit = urgencyReasonLabel ? urgencyReasonLabel.querySelector('input') : null;
            if (!urgencyReasonInputForSubmit || !urgencyReasonInputForSubmit.value.trim()) {
                message = 'Urgency Reason is required for Urgent Planning Mode.';
            } else if (shootStarts && !form.querySelector('input[name="shootDate"]').value) {
                message = 'Shoot Date is required for Urgent Planning Mode.';
            } else if (editIncluded && !form.querySelector('input[name="editDate"]').value) {
                message = 'Edit Date is required for Urgent Planning Mode.';
            }
        }
        if (!message && shootStarts) {
            if (!form.querySelector('input[name="camerapersonUserIds"]:checked')) {
                message = 'Select at least one Cameraperson.';
            } else {
                var leadField = document.getElementById('ideaLeadCameraperson');
                if (!leadField || !leadField.value) {
                    message = 'Shoot Lead is required.';
                }
            }
        } else if (!message && editStarts) {
            if (!form.querySelector('input[name="editorUserIds"]:checked')) {
                message = 'Select at least one Editor.';
            } else {
                var editorLeadField = document.getElementById('ideaLeadEditor');
                if (!editorLeadField || !editorLeadField.value) {
                    message = 'Editor Lead is required.';
                }
            }
        }
        // Publisher(s) is mandatory for every stage combination now (not just Direct Publishing) -
        // a standalone check, not an else-if branch, so it runs alongside whichever of the
        // Cameraperson/Editor checks above also applies.
        if (!message && !form.querySelector('input[name="publisherUserIds"]:checked')) {
            message = 'Select at least one Publisher.';
        }
        var stagesError = document.getElementById('idea-review-stages-error');
        if (message) {
            event.preventDefault();
            if (stagesError) {
                stagesError.textContent = message;
                stagesError.classList.remove('hidden');
            }
        } else if (stagesError) {
            stagesError.classList.add('hidden');
        }
    });

    function updateReasonCounter() {
        var counter = form.querySelector('.char-counter[data-counter-for="idea-review-reason"]');
        if (!counter || !reasonField) {
            return;
        }
        var limit = 500;
        var length = reasonField.value.length;
        counter.textContent = length + ' / ' + limit;
        counter.classList.toggle('char-counter-at-limit', length >= limit);
        counter.classList.toggle('char-counter-near-limit', length >= limit * 0.9 && length < limit);
    }

    if (reasonField) {
        reasonField.addEventListener('input', updateReasonCounter);
    }

    form.addEventListener('reset', function () {
        // Native reset runs after this handler returns; re-sync the label/counter on the next tick.
        window.setTimeout(function () {
            updateReasonLabel();
            updateReasonCounter();
        }, 0);
    });

    // --- Planned Outputs grid --------------------------------------------------------------
    // Same one-row-per-Output-Type design as Reviews -> Ideas -> Approve (reviews-workspace.js),
    // scoped to this page's own real <form> instead of an AJAX-swapped region. This is a plain
    // server-rendered <form method="post">, so the grid's checked-row state is serialized into
    // the hidden #ideaOutputsJsonField just before native submission - IdeaMvcController#decide
    // reads it the same way ReviewsMvcController#decideIdea already does.
    function contextPath() {
        var script = document.querySelector('script[src*="idea-detail.js"]');
        if (!script) {
            return '';
        }
        var src = script.getAttribute('src');
        var idx = src.indexOf('/js/idea-detail.js');
        return idx > 0 ? src.slice(0, idx) : '';
    }

    var OUTPUT_PLATFORM_ICON_FILES = {
        Instagram: 'instagram.svg', Facebook: 'facebook.svg', YouTube: 'youtube.svg',
        Threads: 'threads.svg', Moj: 'moj.svg', TikTok: 'tiktok.svg'
    };

    function outputPlatformIconSrc(platformName) {
        return contextPath() + '/icons/platforms/' + (OUTPUT_PLATFORM_ICON_FILES[platformName] || 'generic.svg');
    }

    function outputRows() {
        return Array.prototype.slice.call(form.querySelectorAll('.reviews-output-row'));
    }

    function clearOutputError() {
        var box = document.getElementById('ideaOutputError');
        if (box) {
            box.classList.add('hidden');
            box.textContent = '';
        }
    }

    function closeOutputPopover(row) {
        var container = row.querySelector('.reviews-output-platform-popovers');
        if (container) {
            container.innerHTML = '';
            delete container.dataset.openPlatform;
        }
    }

    function renderOutputChips(row) {
        var chipsBox = row.querySelector('.reviews-platform-chips');
        var countBox = row.querySelector('.reviews-platform-picker-count');
        if (!chipsBox) {
            return;
        }
        var checked = row.querySelectorAll('.reviews-output-target-checkbox:checked');
        chipsBox.innerHTML = '';
        if (checked.length === 0) {
            var placeholder = document.createElement('span');
            placeholder.className = 'muted';
            placeholder.textContent = 'Select platforms';
            chipsBox.appendChild(placeholder);
            if (countBox) {
                countBox.textContent = '0 selected';
            }
            return;
        }
        var countByPlatform = {};
        var order = [];
        checked.forEach(function (cb) {
            var platform = cb.getAttribute('data-platform');
            if (!(platform in countByPlatform)) {
                countByPlatform[platform] = 0;
                order.push(platform);
            }
            countByPlatform[platform]++;
        });
        order.forEach(function (platform) {
            var chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'reviews-output-platform-chip';
            chip.dataset.platform = platform;
            var icon = document.createElement('img');
            icon.className = 'scope-target-icon';
            icon.src = outputPlatformIconSrc(platform);
            icon.alt = '';
            icon.width = 14;
            icon.height = 14;
            chip.appendChild(icon);
            chip.appendChild(document.createTextNode('\u00d7' + countByPlatform[platform]));
            chipsBox.appendChild(chip);
        });
        if (countBox) {
            countBox.textContent = checked.length + ' selected';
        }
    }

    function syncOutputRowState(row) {
        var enableCb = row.querySelector('.reviews-output-row-enable');
        var enabled = !!(enableCb && enableCb.checked);
        row.classList.toggle('reviews-output-row-disabled', !enabled);
        if (!enabled) {
            row.querySelectorAll('.reviews-output-target-checkbox').forEach(function (cb) {
                cb.checked = false;
            });
            var details = row.querySelector('.reviews-platform-picker');
            if (details) {
                details.open = false;
            }
            renderOutputChips(row);
            closeOutputPopover(row);
        }
    }

    function toggleOutputPlatformPopover(row, platform) {
        var container = row.querySelector('.reviews-output-platform-popovers');
        if (!container) {
            return;
        }
        if (container.dataset.openPlatform === platform) {
            closeOutputPopover(row);
            return;
        }
        container.innerHTML = '';
        container.dataset.openPlatform = platform;

        var typeLabel = row.dataset.outputType;

        var channels = [];
        row.querySelectorAll('.reviews-output-target-checkbox:checked').forEach(function (cb) {
            if (cb.getAttribute('data-platform') === platform) {
                channels.push(cb.getAttribute('data-channel'));
            }
        });

        var popover = document.createElement('div');
        popover.className = 'reviews-platform-popover';

        var header = document.createElement('div');
        header.className = 'reviews-platform-popover-header';
        var title = document.createElement('div');
        title.className = 'reviews-platform-popover-title';
        var titleIcon = document.createElement('img');
        titleIcon.className = 'scope-target-icon';
        titleIcon.src = outputPlatformIconSrc(platform);
        titleIcon.alt = '';
        titleIcon.width = 16;
        titleIcon.height = 16;
        title.appendChild(titleIcon);
        title.appendChild(document.createTextNode(platform + ' (' + channels.length + ')'));
        header.appendChild(title);
        var published = document.createElement('span');
        published.className = 'reviews-platform-popover-published';
        published.textContent = '0/' + channels.length + ' published';
        header.appendChild(published);
        popover.appendChild(header);

        var table = document.createElement('table');
        table.className = 'reviews-platform-popover-table';
        var thead = document.createElement('thead');
        var headRow = document.createElement('tr');
        ['Type', 'Channel', 'Status', 'Link'].forEach(function (label) {
            var th = document.createElement('th');
            th.textContent = label;
            headRow.appendChild(th);
        });
        thead.appendChild(headRow);
        table.appendChild(thead);
        var tbody = document.createElement('tbody');
        channels.forEach(function (channel) {
            var tr = document.createElement('tr');
            var typeTd = document.createElement('td');
            typeTd.textContent = typeLabel;
            var channelTd = document.createElement('td');
            channelTd.textContent = '@' + channel;
            var statusTd = document.createElement('td');
            var pill = document.createElement('span');
            pill.className = 'status-pill status-pending';
            pill.textContent = 'Pending';
            statusTd.appendChild(pill);
            var linkTd = document.createElement('td');
            linkTd.textContent = '-';
            tr.appendChild(typeTd);
            tr.appendChild(channelTd);
            tr.appendChild(statusTd);
            tr.appendChild(linkTd);
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);
        popover.appendChild(table);
        container.appendChild(popover);
    }

    if (document.getElementById('ideaOutputsGrid')) {
        form.addEventListener('change', function (event) {
            var enableCb = event.target.closest('.reviews-output-row-enable');
            if (enableCb) {
                syncOutputRowState(enableCb.closest('.reviews-output-row'));
                return;
            }
            var targetCb = event.target.closest('.reviews-output-target-checkbox');
            if (targetCb) {
                var targetRow = targetCb.closest('.reviews-output-row');
                renderOutputChips(targetRow);
                closeOutputPopover(targetRow);
            }
        });

        form.addEventListener('click', function (event) {
            var chip = event.target.closest('.reviews-output-platform-chip');
            if (chip) {
                // Prevents the click from also toggling the parent <details> (the chip sits
                // inside its <summary>) - only the popover should open/close here.
                event.preventDefault();
                toggleOutputPlatformPopover(chip.closest('.reviews-output-row'), chip.dataset.platform);
            }
        });

        form.addEventListener('submit', function (event) {
            if (decisionField && decisionField.value !== 'APPROVE') {
                return; // Planned Outputs is only relevant/visible for Approve.
            }
            clearOutputError();
            var outputs = [];
            outputRows().forEach(function (row) {
                var enableCb = row.querySelector('.reviews-output-row-enable');
                if (!enableCb || !enableCb.checked) {
                    return;
                }
                var publicationTargetIds = [];
                row.querySelectorAll('.reviews-output-target-checkbox:checked').forEach(function (cb) {
                    publicationTargetIds.push(cb.value);
                });
                // reelTypes/outputTitleDescription: the V31 redesign dropped both fields from
                // this grid - always sent empty/blank so the outputsJson shape
                // PlanningOutputRequest still expects stays unchanged (backend DTO untouched).
                outputs.push({
                    outputType: row.dataset.outputType,
                    reelTypes: [],
                    outputTitleDescription: '',
                    publicationTargetIds: publicationTargetIds
                });
            });
            var jsonField = document.getElementById('ideaOutputsJsonField');
            if (jsonField) {
                jsonField.value = JSON.stringify(outputs);
            }
        });
    }
})();
