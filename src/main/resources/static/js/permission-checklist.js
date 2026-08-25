/**
 * User Detail unified Permission Management table (permission-admin-ui final redesign): one row
 * per permission handles Grant/Revoke (checkbox), Scope/Stage/Expiry/Reason (inline controls), and
 * Update (save) together - replacing the earlier separate checklist/current-grants tables.
 *
 * Every write here is a REAL <form> POST (quick-grant, revoke, update) - this script only
 * intercepts submission to avoid a full page reload; without JS every one of these forms still
 * works exactly as a normal server round-trip (progressive enhancement, not a requirement). After
 * any successful write, the 2 server-rendered panels (Permission Summary, the unified table) are
 * swapped in from a fresh copy of this same page - every number/badge/date/status stays 100%
 * server-computed, never recalculated here.
 *
 * Multi-active-grant rows (2+ simultaneously active grants for one permission - the schema allows
 * this, no unique constraint) are never touched by the checkbox: that checkbox is server-rendered
 * checked+disabled and is purely a summary indicator. Each grant in that state is only revocable
 * individually, from its own row in the "Manage (N)" sub-table.
 */
(function () {
    'use strict';

    var PANEL_IDS = ['permissionSummaryCard', 'permChecklistPanel'];

    // ---------------------------------------------------------------- search / granted-only filter

    function applyFilter() {
        var search = document.getElementById('permChecklistSearch');
        var grantedOnly = document.getElementById('permChecklistGrantedOnly');
        var term = search ? search.value.trim().toLowerCase() : '';
        var onlyGranted = grantedOnly ? grantedOnly.checked : false;
        var rows = document.querySelectorAll('.perm-row');
        var visibleCount = 0;
        rows.forEach(function (row) {
            var haystack = row.getAttribute('data-search') || '';
            var matchesSearch = !term || haystack.indexOf(term) !== -1;
            var matchesGranted = !onlyGranted || row.getAttribute('data-granted') === 'true';
            var visible = matchesSearch && matchesGranted;
            row.style.display = visible ? '' : 'none';
            var subRow = document.getElementById('multi-row-' + row.getAttribute('data-permission'));
            if (subRow && !visible) {
                subRow.style.display = 'none';
            }
            if (visible) {
                visibleCount++;
            }
        });
        var empty = document.getElementById('permChecklistEmpty');
        if (empty) {
            empty.style.display = visibleCount === 0 ? '' : 'none';
        }
        var showingText = document.getElementById('permMgmtShowingText');
        if (showingText) {
            var total = rows.length;
            showingText.textContent = visibleCount === total
                ? total + ' permissions'
                : visibleCount + ' of ' + total + ' permissions';
        }
    }

    function showErrorToast(message) {
        var existing = document.getElementById('permChecklistToast');
        if (existing) {
            existing.remove();
        }
        var el = document.createElement('div');
        el.id = 'permChecklistToast';
        el.className = 'ajax-error';
        el.textContent = message;
        document.body.appendChild(el);
        setTimeout(function () {
            el.remove();
        }, 4000);
    }

    // ---------------------------------------------------------------- server round-trips

    function submitFormViaFetch(form) {
        var body = new URLSearchParams(new FormData(form));
        return fetch(form.getAttribute('action'), {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'fetch'
            },
            body: body.toString()
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('request failed: ' + response.status);
            }
            return response.text();
        });
    }

    function refreshViaGet() {
        return fetch(window.location.href, {
            method: 'GET',
            credentials: 'same-origin',
            headers: {'X-Requested-With': 'fetch'}
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('refresh failed: ' + response.status);
            }
            return response.text();
        });
    }

    function currentFilterState() {
        var searchEl = document.getElementById('permChecklistSearch');
        var grantedOnlyEl = document.getElementById('permChecklistGrantedOnly');
        return {
            search: searchEl ? searchEl.value : '',
            hadFocus: searchEl === document.activeElement,
            grantedOnly: grantedOnlyEl ? grantedOnlyEl.checked : false
        };
    }

    function restoreFilterState(state) {
        var newSearch = document.getElementById('permChecklistSearch');
        if (newSearch) {
            newSearch.value = state.search;
            if (state.hadFocus) {
                newSearch.focus();
            }
        }
        var newGrantedOnly = document.getElementById('permChecklistGrantedOnly');
        if (newGrantedOnly) {
            newGrantedOnly.checked = state.grantedOnly;
        }
    }

    function swapPanelsFromHtml(html, flashPermission) {
        var state = currentFilterState();
        var doc = new DOMParser().parseFromString(html, 'text/html');
        PANEL_IDS.forEach(function (id) {
            var fresh = doc.getElementById(id);
            var live = document.getElementById(id);
            if (fresh && live) {
                live.replaceWith(fresh);
            }
        });
        restoreFilterState(state);
        initRowState();
        applyFilter();
        if (flashPermission) {
            flashUpdated(flashPermission);
        }
    }

    function flashUpdated(permission) {
        var row = document.querySelector('.perm-row[data-permission="' + permission + '"]');
        var badge = row ? row.querySelector('.perm-update-saved') : null;
        if (!badge) {
            return;
        }
        badge.style.display = '';
        setTimeout(function () {
            badge.style.display = 'none';
        }, 2500);
    }

    // ---------------------------------------------------------------- checkbox grant / revoke

    function handleCheckboxToggle(checkbox) {
        var form = checkbox.closest('form.perm-toggle-form');
        if (!form) {
            return;
        }
        var isQuickGrant = form.getAttribute('action').indexOf('/permission-grants/quick') !== -1;
        checkbox.disabled = true;

        if (isQuickGrant) {
            submitFormViaFetch(form)
                .then(refreshViaGet)
                .then(function (html) {
                    swapPanelsFromHtml(html);
                })
                .catch(function () {
                    checkbox.checked = false;
                    checkbox.disabled = false;
                    showErrorToast('Could not grant this permission. Please try again.');
                });
        } else {
            submitFormViaFetch(form)
                .then(function (html) {
                    swapPanelsFromHtml(html);
                })
                .catch(function () {
                    checkbox.checked = true;
                    checkbox.disabled = false;
                    showErrorToast('Could not revoke this permission. Please try again.');
                });
        }
    }

    // ---------------------------------------------------------------- inline Scope/Stage/Item/Update

    function applyScopeVisibility(scopeSelect) {
        var row = scopeSelect.closest('tr');
        if (!row) {
            return;
        }
        var dash = row.querySelector('.perm-stage-dash');
        var stageSelect = row.querySelector('.perm-stage-select');
        var itemInput = row.querySelector('.perm-item-input');
        var value = scopeSelect.value;
        if (dash) {
            dash.style.display = value === 'GLOBAL' ? '' : 'none';
        }
        if (stageSelect) {
            stageSelect.style.display = value === 'STAGE_RESTRICTED' ? '' : 'none';
            stageSelect.disabled = value !== 'STAGE_RESTRICTED';
        }
        if (itemInput) {
            itemInput.style.display = value === 'ITEM_SPECIFIC' ? '' : 'none';
            itemInput.disabled = value !== 'ITEM_SPECIFIC';
        }
    }

    function stageSelectionSignature(select) {
        return Array.prototype.slice.call(select.selectedOptions)
            .map(function (o) { return o.value; }).sort().join(',');
    }

    function markDirty(el) {
        var row = el.closest('tr');
        if (!row) {
            return;
        }
        var updateBtn = row.querySelector('.perm-update-btn');
        if (!updateBtn) {
            return;
        }
        var scopeSelect = row.querySelector('.perm-scope-select');
        var stageSelect = row.querySelector('.perm-stage-select');
        var itemInput = row.querySelector('.perm-item-input');
        var expiryInput = row.querySelector('.perm-expiry-input');
        var reasonInput = row.querySelector('.perm-reason-input');

        var dirty = false;
        if (scopeSelect && scopeSelect.value !== scopeSelect.getAttribute('data-original')) {
            dirty = true;
        }
        if (!dirty && scopeSelect && scopeSelect.value === 'STAGE_RESTRICTED' && stageSelect) {
            var originalStages = stageSelect.getAttribute('data-original') || '';
            if (stageSelectionSignature(stageSelect) !== originalStages) {
                dirty = true;
            }
        }
        if (!dirty && scopeSelect && scopeSelect.value === 'ITEM_SPECIFIC' && itemInput) {
            if (itemInput.value.trim() !== (itemInput.getAttribute('data-original') || '')) {
                dirty = true;
            }
        }
        if (!dirty && expiryInput && expiryInput.value !== (expiryInput.getAttribute('data-original') || '')) {
            dirty = true;
        }
        if (!dirty && reasonInput && reasonInput.value !== (reasonInput.getAttribute('data-original') || '')) {
            dirty = true;
        }
        updateBtn.disabled = !dirty;
    }

    // ---------------------------------------------------------------- event wiring

    document.addEventListener('change', function (event) {
        var checkbox = event.target.closest('.perm-checkbox');
        if (checkbox) {
            if (!checkbox.disabled) {
                handleCheckboxToggle(checkbox);
            }
            return;
        }
        if (event.target.id === 'permChecklistGrantedOnly') {
            applyFilter();
            return;
        }
        var scopeSelect = event.target.closest('.perm-scope-select');
        if (scopeSelect) {
            applyScopeVisibility(scopeSelect);
            markDirty(scopeSelect);
            return;
        }
        var trackedControl = event.target.closest('.perm-stage-select');
        if (trackedControl) {
            markDirty(trackedControl);
        }
    });

    document.addEventListener('input', function (event) {
        if (event.target.id === 'permChecklistSearch') {
            applyFilter();
            return;
        }
        var trackedInput = event.target.closest('.perm-item-input, .perm-expiry-input, .perm-reason-input');
        if (trackedInput) {
            markDirty(trackedInput);
        }
    });

    document.addEventListener('submit', function (event) {
        var form = event.target.closest('form.perm-update-form');
        if (!form) {
            return;
        }
        event.preventDefault();
        var parentRow = form.closest('tr');
        var permission = parentRow ? parentRow.getAttribute('data-permission') : null;
        var btn = form.querySelector('.perm-update-btn');
        if (btn) {
            btn.disabled = true;
        }
        submitFormViaFetch(form)
            .then(function (html) {
                swapPanelsFromHtml(html, permission);
            })
            .catch(function () {
                if (btn) {
                    btn.disabled = false;
                }
                showErrorToast('Could not save this update. Please try again.');
            });
    });

    document.addEventListener('click', function (event) {
        var expandBtn = event.target.closest('.perm-expand-btn');
        if (expandBtn) {
            var target = document.getElementById(expandBtn.getAttribute('data-target'));
            if (target) {
                target.style.display = target.style.display === 'none' ? '' : 'none';
            }
            return;
        }
        var nameLink = event.target.closest('.perm-name-link');
        if (nameLink) {
            openDetailDialog(nameLink);
            return;
        }
        if (event.target.closest('.perm-detail-close')) {
            var dialog = document.getElementById('permDetailDialog');
            if (dialog) {
                dialog.close();
            }
        }
    });

    function openDetailDialog(trigger) {
        var dialog = document.getElementById('permDetailDialog');
        if (!dialog) {
            return;
        }
        var name = document.getElementById('permDetailName');
        var code = document.getElementById('permDetailCode');
        var description = document.getElementById('permDetailDescription');
        if (name) {
            name.textContent = trigger.getAttribute('data-display-name') || '';
        }
        if (code) {
            code.textContent = trigger.getAttribute('data-code') || '';
        }
        if (description) {
            description.textContent = trigger.getAttribute('data-description') || '';
        }
        if (typeof dialog.showModal === 'function') {
            dialog.showModal();
        }
    }

    // ---------------------------------------------------------------- (re-)initialization

    /** Captures each row's "original" values for dirty-tracking, and applies the correct Scope ->
     * Stage(s)/Item visibility - run once on load and again after every panel swap, since swapped-
     * in rows are fresh server DOM that hasn't had this applied yet. */
    function initRowState() {
        document.querySelectorAll('.perm-scope-select').forEach(function (select) {
            applyScopeVisibility(select);
        });
        document.querySelectorAll('.perm-stage-select').forEach(function (select) {
            select.setAttribute('data-original', stageSelectionSignature(select));
        });
        document.querySelectorAll('.perm-item-input').forEach(function (input) {
            input.setAttribute('data-original', input.value.trim());
        });
    }

    initRowState();
    applyFilter();
})();
