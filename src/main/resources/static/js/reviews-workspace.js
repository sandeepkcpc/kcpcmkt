/**
 * Manager Reviews Workspace - AJAX partial-fragment-swap tab switching/queue selection/pagination/
 * search/filter, mirroring pipeline-dashboard.js's/idea-queue-dashboard.js's own loadX() pattern
 * verbatim (X-Requested-With: fetch, #reviewsDynamicRegion innerHTML swap, history.pushState,
 * AbortController for in-flight requests, real-navigation fallback on genuine fetch failure). No
 * business/validation logic lives here - the Reason-mandatory-for-Reject/Rework rule etc. is never
 * duplicated in JS; a decision POST either succeeds or comes back with the backend's own
 * ApiErrorResponse.message, which this file just displays as-is.
 *
 * Loading state is toggled on #reviewsDynamicRegion only (never the whole page) and is cleared in
 * BOTH the fetch success path and the catch path - this session already hit and fixed the opposite
 * mistake once, in pipeline-dashboard.js (setLoading(false) missing from the success path left the
 * table permanently dimmed), so this file's loadReviews()/submitDecision() are written against that
 * exact failure mode from the start.
 */
(function () {
    var region = document.getElementById('reviewsDynamicRegion');
    if (!region) {
        return;
    }

    var currentRequest = null;

    function setLoading(isLoading) {
        region.classList.toggle('reviews-loading', isLoading);
    }

    function loadReviews(url, pushHistory) {
        if (currentRequest) {
            currentRequest.abort();
        }
        var controller = new AbortController();
        currentRequest = controller;
        setLoading(true);

        fetch(url, {headers: {'X-Requested-With': 'fetch'}, signal: controller.signal})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Reviews request failed: ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                if (controller.signal.aborted) {
                    return;
                }
                region.innerHTML = html;
                // Shoot/Edit review inspector reuses the exact same Shoot/Edit Comments thread
                // markup and AJAX behavior as Content Detail (stage-discussion.js) - re-wire it
                // within the freshly-swapped region on every load, same as a plain page load does.
                if (window.wireStageDiscussion) {
                    window.wireStageDiscussion(region);
                }
                // The Ideas tab's Model(s)/Cameraperson(s) pickers are model-picker.js components -
                // same re-wire-after-swap reasoning as wireStageDiscussion above.
                if (window.initModelPickers) {
                    window.initModelPickers(region);
                }
                // Description / Details note-icon + modal (script-description-modal.js) - same
                // re-wire-after-swap reasoning as wireStageDiscussion/initModelPickers above.
                if (window.wireScriptDescriptionModal) {
                    window.wireScriptDescriptionModal(region);
                }
                setLoading(false);
                if (pushHistory) {
                    history.pushState(null, '', url);
                }
                currentRequest = null;
            })
            .catch(function (err) {
                if (err && err.name === 'AbortError') {
                    return;
                }
                setLoading(false);
                currentRequest = null;
                // Genuine fetch failure (offline, 5xx, etc.) - fall back to a real navigation
                // instead of leaving the user stuck looking at stale/dimmed content.
                window.location.href = url;
            });
    }

    function currentFilterForm() {
        return document.getElementById('reviewsFilterForm');
    }

    function reloadFromForm(pushHistory) {
        var form = currentFilterForm();
        if (!form) {
            return;
        }
        var params = new URLSearchParams(new FormData(form)).toString();
        loadReviews(form.action + (params ? '?' + params : ''), pushHistory);
    }

    // --- Tab switching / row selection / pagination / clear - all plain <a href> links, real GET
    // URLs, intercepted here for the AJAX swap; a click that reaches window.location (JS failed/
    // disabled) still works exactly as a normal link. ------------------------------------------
    region.addEventListener('click', function (event) {
        var link = event.target.closest(
            '.reviews-tab, .reviews-row-link, .pagination-controls a, .reviews-clear, .reviews-sort-link'
        );
        if (link && link.href) {
            event.preventDefault();
            loadReviews(link.href, true);
            return;
        }
        // Clicking anywhere else in a queue row (not the link itself, already handled above)
        // still selects that row, using the same server-built URL the link carries.
        var row = event.target.closest('.reviews-row');
        if (row && row.dataset.rowUrl) {
            loadReviews(row.dataset.rowUrl, true);
        }
    });

    // Filter form submit (Enter in the search box).
    region.addEventListener('submit', function (event) {
        var form = event.target.closest('#reviewsFilterForm');
        if (!form) {
            return;
        }
        event.preventDefault();
        reloadFromForm(true);
    });

    // Mode/Priority/page-size selects and the Delayed-only checkbox re-submit immediately on change.
    region.addEventListener('change', function (event) {
        var id = event.target.id;
        var isFilterControl = /^rv(Idea|Shoot|Edit)(PageSize)$/.test(id) || event.target.name === 'mode'
            || event.target.name === 'priority' || event.target.name === 'delayedOnly';
        if (isFilterControl) {
            reloadFromForm(true);
        }
    });

    // Search input: debounced re-fetch as the user types.
    var searchDebounce = null;
    region.addEventListener('input', function (event) {
        if (!/^rv(Idea|Shoot|Edit)Search$/.test(event.target.id)) {
            return;
        }
        if (searchDebounce) {
            clearTimeout(searchDebounce);
        }
        searchDebounce = setTimeout(function () {
            reloadFromForm(true);
        }, 350);
    });

    window.addEventListener('popstate', function () {
        loadReviews(window.location.href, false);
    });

    // Idea Description edit (script-description-modal.js's AJAX path, data-ajax="true" on the
    // Reviews Workspace's copy of the edit form) - refresh the panel in place after a successful
    // save so the modal's read-only view shows the new text, same pattern as every other AJAX
    // write on this page (decision submit, reopen).
    document.addEventListener('kcpc:idea-description-updated', function () {
        loadReviews(window.location.href, false);
    });

    // --- Character counters (Reason / Decision Reason / Reviewer Comments textareas) -----------
    region.addEventListener('input', function (event) {
        var field = event.target;
        if (field.tagName !== 'TEXTAREA') {
            return;
        }
        var counter = region.querySelector('.char-counter[data-counter-for="' + field.id + '"]');
        if (!counter) {
            return;
        }
        var limit = 500;
        var length = field.value.length;
        counter.textContent = length + ' / ' + limit;
        counter.classList.toggle('char-counter-at-limit', length >= limit);
        counter.classList.toggle('char-counter-near-limit', length >= limit * 0.9 && length < limit);
    });

    // --- Decision submit -------------------------------------------------------------------
    var decisionEndpoints = {
        idea: function (id) { return contextPath() + '/app/reviews/ideas/' + id + '/decision'; },
        shoot: function (id) { return contextPath() + '/app/reviews/shoot/' + id + '/decision'; },
        edit: function (id) { return contextPath() + '/app/reviews/edit/' + id + '/decision'; }
    };

    function contextPath() {
        // Same-origin, path-relative endpoints - derive the app's context path from this script's
        // own src attribute (".../<ctx>/js/reviews-workspace.js") rather than hardcoding it.
        // document.currentScript is only valid during a script's OWN initial synchronous execution
        // (not later, inside an event handler like the decision-submit click this is called from),
        // so this always looks the <script> tag up by src instead.
        var script = document.querySelector('script[src*="reviews-workspace.js"]');
        if (!script) {
            return '';
        }
        var src = script.getAttribute('src');
        var idx = src.indexOf('/js/reviews-workspace.js');
        return idx > 0 ? src.slice(0, idx) : '';
    }

    function showDecisionError(message) {
        var box = document.getElementById('reviewsDecisionError');
        if (!box) {
            return;
        }
        box.textContent = message;
        box.classList.remove('hidden');
    }

    function clearDecisionError() {
        var box = document.getElementById('reviewsDecisionError');
        if (box) {
            box.classList.add('hidden');
            box.textContent = '';
        }
    }

    function setDecisionButtonsDisabled(disabled) {
        region.querySelectorAll('.reviews-decision-btn').forEach(function (btn) {
            btn.disabled = disabled;
        });
        ['reviewsIdeaConfirmBtn', 'reviewsPlanConfirmBtn'].forEach(function (id) {
            var confirmBtn = document.getElementById(id);
            if (confirmBtn) {
                confirmBtn.disabled = disabled;
            }
        });
    }

    function submitDecision(card, params) {
        var type = card.dataset.reviewType;
        var id = card.dataset.reviewId;
        var endpoint = decisionEndpoints[type](id);

        var csrfInput = document.getElementById('reviewsCsrfToken');
        if (csrfInput) {
            params.set(csrfInput.name, csrfInput.value);
        }

        clearDecisionError();
        setDecisionButtonsDisabled(true);

        fetch(endpoint, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params.toString()
        })
            .then(function (response) {
                if (response.ok) {
                    return {ok: true};
                }
                return response.json().then(function (body) {
                    return {ok: false, message: body.message || 'The decision could not be recorded.'};
                }).catch(function () {
                    return {ok: false, message: 'The decision could not be recorded.'};
                });
            })
            .then(function (result) {
                if (result.ok) {
                    // Re-fetch the same tab/filter/page state - the decided item naturally drops
                    // out of the pending queue server-side, which also auto-selects whatever
                    // pending item is now first (see ReviewsMvcController's selection fallback).
                    loadReviews(window.location.href, false);
                } else {
                    setDecisionButtonsDisabled(false);
                    showDecisionError(result.message);
                }
            })
            .catch(function () {
                setDecisionButtonsDisabled(false);
                showDecisionError('Network error - the decision was not submitted. Please try again.');
            });
    }

    // --- Idea decision: Approve/Reject/Retain are selector buttons, not instant-submit - clicking
    // one reveals the relevant section (the full Planning form for Approve; just the reason field
    // for Reject/Retain) and the bottom confirm bar becomes the actual submit action. -------------
    var CONFIRM_LABELS = {APPROVE: 'Approve & Assign Shoot', REJECT: 'Reject Idea', RETAIN: 'Retain Idea'};

    function selectIdeaDecision(card, decision) {
        card.dataset.selectedDecision = decision;
        region.querySelectorAll('.reviews-decision-btn').forEach(function (b) {
            b.classList.toggle('reviews-decision-btn-selected', b.dataset.decision === decision);
        });
        var banner = document.getElementById('reviewsIdeaApproveBanner');
        var reasonBlock = document.getElementById('reviewsIdeaReasonBlock');
        var planningFields = document.getElementById('reviewsIdeaPlanningFields');
        var confirmBar = document.getElementById('reviewsIdeaConfirmBar');
        var confirmBtn = document.getElementById('reviewsIdeaConfirmBtn');
        var isApprove = decision === 'APPROVE';
        if (banner) {
            banner.classList.toggle('hidden', !isApprove);
        }
        if (reasonBlock) {
            reasonBlock.classList.toggle('hidden', isApprove);
        }
        if (planningFields) {
            planningFields.classList.toggle('hidden', !isApprove);
        }
        if (confirmBar) {
            confirmBar.classList.remove('hidden');
        }
        if (confirmBtn) {
            confirmBtn.textContent = CONFIRM_LABELS[decision] || 'Confirm';
        }
        clearDecisionError();
        updateReviewsIdeaScheduleDefaults();
        updateReviewsIdeaPlanningModeFields();
    }

    // --- Shoot/Edit decision: Approve/Request Rework are also selector buttons now (workflow
    // redesign) - Approve reveals the Editor/Publisher Assignment section (same fold-in pattern as
    // Idea Review's Initial Shoot Assignment) and the Confirm bar becomes the actual submit action,
    // so approval can never be submitted before the assignment is filled in. Request Rework reveals
    // no extra fields (the Reason field is already always visible on this tab) but still routes
    // through the same Confirm bar for a single consistent interaction model. -------------------
    var PLAN_CONFIRM_LABELS = {
        shoot: {APPROVE: 'Approve & Assign Editor', REWORK: 'Confirm Request Rework'},
        edit: {APPROVE: 'Approve & Assign Publisher', REWORK: 'Confirm Request Rework'}
    };

    function selectPlanDecision(card, type, decision) {
        card.dataset.selectedDecision = decision;
        region.querySelectorAll('.reviews-decision-btn').forEach(function (b) {
            b.classList.toggle('reviews-decision-btn-selected', b.dataset.decision === decision);
        });
        var teamFields = document.getElementById('reviewsNextTeamFields');
        var confirmBar = document.getElementById('reviewsPlanConfirmBar');
        var confirmBtn = document.getElementById('reviewsPlanConfirmBtn');
        var isApprove = decision === 'APPROVE';
        if (teamFields) {
            teamFields.classList.toggle('hidden', !isApprove);
        }
        if (confirmBar) {
            confirmBar.classList.remove('hidden');
        }
        if (confirmBtn) {
            var labels = PLAN_CONFIRM_LABELS[type] || {};
            confirmBtn.textContent = labels[decision] || 'Confirm';
        }
        clearDecisionError();
    }

    function resetPlanDecisionSelection(card) {
        delete card.dataset.selectedDecision;
        region.querySelectorAll('.reviews-decision-btn').forEach(function (b) {
            b.classList.remove('reviews-decision-btn-selected');
        });
        ['reviewsNextTeamFields', 'reviewsPlanConfirmBar'].forEach(function (id) {
            var el = document.getElementById(id);
            if (el) {
                el.classList.add('hidden');
            }
        });
        clearDecisionError();
    }

    function resetIdeaDecisionSelection(card) {
        delete card.dataset.selectedDecision;
        region.querySelectorAll('.reviews-decision-btn').forEach(function (b) {
            b.classList.remove('reviews-decision-btn-selected');
        });
        ['reviewsIdeaApproveBanner', 'reviewsIdeaReasonBlock', 'reviewsIdeaPlanningFields', 'reviewsIdeaConfirmBar']
            .forEach(function (id) {
                var el = document.getElementById(id);
                if (el) {
                    el.classList.add('hidden');
                }
            });
        resetReviewsIdeaOutputRows();
        clearDecisionError();
    }

    function collectIdeaApproveParams(params) {
        var ids = {
            contentPriority: 'reviewsIdeaContentPriority', skuReference: 'reviewsIdeaSkuReference',
            planningMode: 'reviewsIdeaPlanningMode', categoryText: 'reviewsIdeaCategoryText',
            folderLink: 'reviewsIdeaFolderLink', plannedLiveDate: 'reviewsIdeaPlannedLiveDate',
            shootDate: 'reviewsIdeaShootDate', editDate: 'reviewsIdeaEditDate',
            urgencyReason: 'reviewsIdeaUrgencyReason',
            leadCamerapersonUserId: 'reviewsIdeaLeadCameraperson',
            cameramanMark: 'reviewsIdeaCameramanMark', editorMark: 'reviewsIdeaEditorMark',
            modelMark: 'reviewsIdeaModelMark'
        };
        Object.keys(ids).forEach(function (param) {
            var field = document.getElementById(ids[param]);
            if (field && field.value !== '') {
                params.set(param, field.value);
            }
        });
        region.querySelectorAll('#reviewsIdeaPlanningFields input[name="modelUserIds"]:checked').forEach(function (cb) {
            params.append('modelUserIds', cb.value);
        });
        region.querySelectorAll('#reviewsIdeaPlanningFields input[name="camerapersonUserIds"]:checked').forEach(function (cb) {
            params.append('camerapersonUserIds', cb.value);
        });
        var outputs = [];
        reviewsOutputRows().forEach(function (row) {
            var enableCb = row.querySelector('.reviews-output-row-enable');
            if (!enableCb || !enableCb.checked) {
                return;
            }
            var publicationTargetIds = [];
            row.querySelectorAll('.reviews-output-target-checkbox:checked').forEach(function (cb) {
                publicationTargetIds.push(cb.value);
            });
            // reelTypes/outputTitleDescription: the V31 redesign dropped both fields from this
            // grid (Reel Type sub-selection and Output Description no longer exist in the UI) -
            // always sent empty/blank so the outputsJson shape PlanningOutputRequest still expects
            // stays unchanged (backend DTO intentionally untouched).
            outputs.push({
                outputType: row.dataset.outputType,
                reelTypes: [],
                outputTitleDescription: '',
                publicationTargetIds: publicationTargetIds
            });
        });
        params.set('outputsJson', JSON.stringify(outputs));
    }

    // --- Planned Outputs grid (Ideas Approve flow only) -----------------------------------------
    // One fixed row per Output Type (Story/Post/Reel/Long Video, V31 redesign - was Photography/
    // Reel/Video) instead of the old arbitrary Add/Edit/Delete list - a row's own "select this
    // type" checkbox controls whether it contributes an output group to the approval payload. Only
    // two columns: Output Type and Platform/Channel - no Reel Type or Output Description field.
    // Every row's state (which platforms are checked) lives entirely in the DOM; there is no
    // in-memory mirror array to keep in sync, and collectIdeaApproveParams (above) reads straight
    // from the checked/enabled rows at submit time.
    var OUTPUT_PLATFORM_ICON_FILES = {
        Instagram: 'instagram.svg', Facebook: 'facebook.svg', YouTube: 'youtube.svg',
        Threads: 'threads.svg', Moj: 'moj.svg', TikTok: 'tiktok.svg'
    };

    function outputPlatformIconSrc(platformName) {
        return contextPath() + '/icons/platforms/' + (OUTPUT_PLATFORM_ICON_FILES[platformName] || 'generic.svg');
    }

    function reviewsOutputRows() {
        return Array.prototype.slice.call(region.querySelectorAll('.reviews-output-row'));
    }

    function clearReviewsIdeaOutputError() {
        var box = document.getElementById('reviewsIdeaOutputError');
        if (box) {
            box.classList.add('hidden');
            box.textContent = '';
        }
    }

    function closeReviewsOutputPopover(row) {
        var container = row.querySelector('.reviews-output-platform-popovers');
        if (container) {
            container.innerHTML = '';
            delete container.dataset.openPlatform;
        }
    }

    function renderReviewsOutputChips(row) {
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

    // A row's checkbox is the only thing that decides whether it contributes to the submitted
    // outputs array (see collectIdeaApproveParams) - unchecking it clears everything the row was
    // holding so a later confirm never picks up stale selections from a type the user backed out of.
    function syncReviewsOutputRowState(row) {
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
            renderReviewsOutputChips(row);
            closeReviewsOutputPopover(row);
        }
    }

    function resetReviewsIdeaOutputRows() {
        reviewsOutputRows().forEach(function (row) {
            var enableCb = row.querySelector('.reviews-output-row-enable');
            if (enableCb) {
                enableCb.checked = false;
            }
            syncReviewsOutputRowState(row);
        });
        clearReviewsIdeaOutputError();
    }

    // Clicking a chip opens a small in-flow popover (below the chips, inside the same table cell)
    // listing the row's currently selected channels for that one platform - Type/Channel/Status/
    // Link, matching the pre-existing Publishing Scope table's look. Status/Link are always
    // "Pending"/"-" here since nothing has actually been created or published yet - this whole
    // grid is still just a staged approval payload.
    function toggleReviewsOutputPlatformPopover(row, platform) {
        var container = row.querySelector('.reviews-output-platform-popovers');
        if (!container) {
            return;
        }
        if (container.dataset.openPlatform === platform) {
            closeReviewsOutputPopover(row);
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

    region.addEventListener('change', function (event) {
        var enableCb = event.target.closest('.reviews-output-row-enable');
        if (enableCb) {
            syncReviewsOutputRowState(enableCb.closest('.reviews-output-row'));
            return;
        }
        var targetCb = event.target.closest('.reviews-output-target-checkbox');
        if (targetCb) {
            var targetRow = targetCb.closest('.reviews-output-row');
            renderReviewsOutputChips(targetRow);
            closeReviewsOutputPopover(targetRow);
        }
    });

    region.addEventListener('click', function (event) {
        var chip = event.target.closest('.reviews-output-platform-chip');
        if (chip) {
            // Prevents the click from also toggling the parent <details> (the chip sits inside
            // its <summary>) - only the popover should open/close here.
            event.preventDefault();
            toggleReviewsOutputPlatformPopover(chip.closest('.reviews-output-row'), chip.dataset.platform);
        }
    });

    region.addEventListener('click', function (event) {
        var decisionBtn = event.target.closest('.reviews-decision-btn');
        if (decisionBtn && !decisionBtn.disabled) {
            var decisionCard = decisionBtn.closest('.reviews-detail-card');
            if (!decisionCard) {
                return;
            }
            if (decisionCard.dataset.reviewType === 'idea') {
                selectIdeaDecision(decisionCard, decisionBtn.dataset.decision);
                return;
            }
            // Shoot/Edit: select+reveal, same as Idea - Approve reveals the Editor/Publisher
            // Assignment section below and a Confirm bar becomes the actual submit action.
            selectPlanDecision(decisionCard, decisionCard.dataset.reviewType, decisionBtn.dataset.decision);
            return;
        }

        var cancelBtn = event.target.closest('#reviewsIdeaCancelBtn');
        if (cancelBtn) {
            // The Planning section (and its Cancel/Confirm bar) is a sibling of the narrow
            // .reviews-detail-card, not nested inside it (moved out to be full-width) - look the
            // card up by id rather than via .closest().
            var cancelCard = document.getElementById('reviewsDetailCard');
            if (cancelCard) {
                resetIdeaDecisionSelection(cancelCard);
            }
            return;
        }

        var confirmBtn = event.target.closest('#reviewsIdeaConfirmBtn');
        if (confirmBtn && !confirmBtn.disabled) {
            var confirmCard = document.getElementById('reviewsDetailCard');
            var decision = confirmCard && confirmCard.dataset.selectedDecision;
            if (!confirmCard || !decision) {
                return;
            }
            var confirmParams = new URLSearchParams();
            confirmParams.set('decision', decision);
            if (decision === 'APPROVE') {
                clearReviewsIdeaOutputError();
                collectIdeaApproveParams(confirmParams);
            } else {
                var reasonField = document.getElementById('reviewsIdeaReason');
                if (reasonField && reasonField.value.trim()) {
                    confirmParams.set('reason', reasonField.value.trim());
                }
            }
            submitDecision(confirmCard, confirmParams);
            return;
        }

        var planCancelBtn = event.target.closest('#reviewsPlanCancelBtn');
        if (planCancelBtn) {
            var planCancelCard = planCancelBtn.closest('.reviews-detail-card');
            if (planCancelCard) {
                resetPlanDecisionSelection(planCancelCard);
            }
            return;
        }

        var planConfirmBtn = event.target.closest('#reviewsPlanConfirmBtn');
        if (planConfirmBtn && !planConfirmBtn.disabled) {
            var planConfirmCard = planConfirmBtn.closest('.reviews-detail-card');
            var planDecision = planConfirmCard && planConfirmCard.dataset.selectedDecision;
            if (!planConfirmCard || !planDecision) {
                return;
            }
            var type = planConfirmCard.dataset.reviewType;
            var planParams = new URLSearchParams();
            planParams.set('approve', planDecision === 'APPROVE' ? 'true' : 'false');
            var planReasonField = document.getElementById('reviewsPlanReason');
            if (planReasonField && planReasonField.value.trim()) {
                planParams.set('reason', planReasonField.value.trim());
            }
            region.querySelectorAll('.reviews-qualifying-checkbox:checked').forEach(function (cb) {
                planParams.append('qualifyingRecipientUserIds', cb.value);
            });
            if (planDecision === 'APPROVE') {
                var teamLabel = type === 'shoot' ? 'Editor' : 'Publisher';
                var teamIds = [];
                region.querySelectorAll('#reviewsNextTeamFields input[name="nextTeamUserIds"]:checked')
                    .forEach(function (cb) {
                        teamIds.push(cb.value);
                    });
                if (teamIds.length === 0) {
                    showDecisionError('Select at least one ' + teamLabel + '.');
                    return;
                }
                if (type === 'shoot') {
                    // Editor Assignment has a Lead, same as Cameraperson/Editor elsewhere in this app.
                    var leadSelect = document.getElementById('reviewsNextTeamLead');
                    var leadId = leadSelect ? leadSelect.value : '';
                    if (!leadId) {
                        showDecisionError(teamLabel + ' Lead is required.');
                        return;
                    }
                    teamIds.forEach(function (v) {
                        planParams.append('editorUserIds', v);
                    });
                    planParams.set('leadEditorUserId', leadId);
                } else {
                    // Publisher Assignment has no Lead concept (ENG-036/ENG-044) - Publisher(s) only.
                    teamIds.forEach(function (v) {
                        planParams.append('publisherUserIds', v);
                    });
                }
            }
            submitDecision(planConfirmCard, planParams);
        }
    });

    // --- Standard/Urgent schedule defaulting (Ideas tab Approve form) --------------------------
    // Pure frontend convenience - the submitted values are unchanged either way, since
    // IdeaService#approve already defaults blank Shoot/Edit dates identically server-side.
    function updateReviewsIdeaScheduleDefaults() {
        var liveDateField = document.getElementById('reviewsIdeaPlannedLiveDate');
        var modeSelect = document.getElementById('reviewsIdeaPlanningMode');
        var shootDateField = document.getElementById('reviewsIdeaShootDate');
        var editDateField = document.getElementById('reviewsIdeaEditDate');
        if (!liveDateField || !modeSelect || !shootDateField || !editDateField || !liveDateField.value) {
            return;
        }
        if (modeSelect.value === 'URGENT') {
            return;
        }
        var liveDate = new Date(liveDateField.value + 'T00:00:00');
        if (isNaN(liveDate.getTime())) {
            return;
        }
        var shootDate = new Date(liveDate);
        shootDate.setDate(shootDate.getDate() - 5);
        var editDate = new Date(liveDate);
        editDate.setDate(editDate.getDate() - 2);
        shootDateField.value = shootDate.toISOString().slice(0, 10);
        editDateField.value = editDate.toISOString().slice(0, 10);
    }

    function updateReviewsIdeaPlanningModeFields() {
        var modeSelect = document.getElementById('reviewsIdeaPlanningMode');
        var urgencyLabel = document.getElementById('reviewsIdeaUrgencyReasonLabel');
        var urgencyInput = document.getElementById('reviewsIdeaUrgencyReason');
        var shootHint = document.getElementById('reviewsIdeaShootDateHint');
        var editHint = document.getElementById('reviewsIdeaEditDateHint');
        var shootDateField = document.getElementById('reviewsIdeaShootDate');
        var editDateField = document.getElementById('reviewsIdeaEditDate');
        if (!modeSelect) {
            return;
        }
        var isUrgent = modeSelect.value === 'URGENT';
        if (urgencyLabel) {
            urgencyLabel.classList.toggle('hidden', !isUrgent);
        }
        if (urgencyInput) {
            urgencyInput.required = isUrgent;
        }
        [shootHint, editHint].forEach(function (hint) {
            if (hint) {
                hint.classList.toggle('hidden', isUrgent);
            }
        });
        if (isUrgent) {
            if (shootDateField) {
                shootDateField.readOnly = false;
            }
            if (editDateField) {
                editDateField.readOnly = false;
            }
        } else {
            updateReviewsIdeaScheduleDefaults();
        }
    }

    region.addEventListener('change', function (event) {
        if (event.target.id === 'reviewsIdeaPlannedLiveDate') {
            updateReviewsIdeaScheduleDefaults();
        }
        if (event.target.id === 'reviewsIdeaPlanningMode') {
            updateReviewsIdeaPlanningModeFields();
        }
    });

    // --- Reopen (Retained sub-view only) ----------------------------------------------------
    // Deliberately a separate small handler, not routed through submitDecision/decisionEndpoints -
    // Reopen isn't an IdeaReviewDecision (no APPROVE/REJECT/RETAIN value for it) and posts to its
    // own endpoint with no body params beyond CSRF, but still reuses the exact same
    // loadReviews(window.location.href, false) refresh-in-place and reviewsDecisionError display
    // as every other Reviews action.
    region.addEventListener('click', function (event) {
        var btn = event.target.closest('#reviewsIdeaReopenBtn');
        if (!btn || btn.disabled) {
            return;
        }
        var card = btn.closest('.reviews-detail-card');
        if (!card) {
            return;
        }
        var id = card.dataset.reviewId;
        var params = new URLSearchParams();
        var csrfInput = document.getElementById('reviewsCsrfToken');
        if (csrfInput) {
            params.set(csrfInput.name, csrfInput.value);
        }

        clearDecisionError();
        btn.disabled = true;

        fetch(contextPath() + '/app/reviews/ideas/' + id + '/reopen', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params.toString()
        })
            .then(function (response) {
                if (response.ok) {
                    return {ok: true};
                }
                return response.json().then(function (body) {
                    return {ok: false, message: body.message || 'The idea could not be reopened.'};
                }).catch(function () {
                    return {ok: false, message: 'The idea could not be reopened.'};
                });
            })
            .then(function (result) {
                if (result.ok) {
                    // The reopened idea drops out of the Retained list server-side (it's back at
                    // PA now) - re-fetching the same URL naturally reflects that, same pattern as
                    // a decision dropping an item out of the pending queue.
                    loadReviews(window.location.href, false);
                } else {
                    btn.disabled = false;
                    showDecisionError(result.message);
                }
            })
            .catch(function () {
                btn.disabled = false;
                showDecisionError('Network error - the idea was not reopened. Please try again.');
            });
    });
})();
