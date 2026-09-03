/**
 * Users admin page - Edit User modal (admin-users.jsp). One shared modal, opened from any row's
 * pencil icon and populated from that icon's own data-* attributes (already server-rendered in
 * the table - no fetch needed just to open it). Access Class is never submitted or independently
 * editable - it only ever displays whatever the currently-selected Business Role option's own
 * data-access-class says (BRS-REQ-001/002), refreshed live as the Business Role <select> changes.
 *
 * Save is a real <form> POST via fetch (same X-Requested-With: fetch + CSRF-token-in-body contract
 * every other AJAX write in this app already uses - see permission-checklist.js/
 * script-description-modal.js). On success the just-edited row's own cells (and its edit icon's
 * data-* attributes) are updated in place from the server's response body - no full page reload -
 * and a transient success toast confirms the save; on failure the modal stays open with the
 * reviewer's typed values untouched and an inline error shown.
 */
(function () {
    'use strict';

    var overlay = document.getElementById('editUserModalOverlay');
    var form = document.getElementById('editUserForm');
    if (!overlay || !form) {
        return;
    }

    var closeBtn = document.getElementById('editUserModalClose');
    var cancelBtn = document.getElementById('editUserCancelBtn');
    var targetLabel = document.getElementById('editUserTargetLabel');
    var errorBox = document.getElementById('editUserError');
    var fullNameInput = document.getElementById('editUserFullName');
    var emailInput = document.getElementById('editUserEmail');
    var roleSelect = document.getElementById('editUserBusinessRoleId');
    var accessClassField = document.getElementById('editUserAccessClass');
    var activeSelect = document.getElementById('editUserActive');
    var reasonInput = document.getElementById('editUserReason');
    var saveBtn = document.getElementById('editUserSaveBtn');
    var currentUserId = null;
    var submitting = false;

    function contextPath() {
        var script = document.querySelector('script[src*="admin-edit-user-modal.js"]');
        if (!script) {
            return '';
        }
        var src = script.getAttribute('src');
        var idx = src.indexOf('/js/admin-edit-user-modal.js');
        return idx > 0 ? src.slice(0, idx) : '';
    }

    function clearError() {
        if (errorBox) {
            errorBox.classList.add('hidden');
            errorBox.textContent = '';
        }
    }

    function showError(message) {
        if (errorBox) {
            errorBox.textContent = message;
            errorBox.classList.remove('hidden');
        }
    }

    function updateAccessClassDisplay() {
        var selected = roleSelect.options[roleSelect.selectedIndex];
        accessClassField.value = selected ? (selected.getAttribute('data-access-class') || '') : '';
    }

    function openModal(trigger) {
        clearError();
        currentUserId = trigger.getAttribute('data-user-id');
        fullNameInput.value = trigger.getAttribute('data-full-name') || '';
        emailInput.value = trigger.getAttribute('data-email') || '';
        roleSelect.value = trigger.getAttribute('data-business-role-id') || '';
        activeSelect.value = trigger.getAttribute('data-active') === 'true' ? 'true' : 'false';
        reasonInput.value = '';
        updateAccessClassDisplay();
        if (targetLabel) {
            targetLabel.textContent = 'Editing ' + (trigger.getAttribute('data-full-name') || 'this user') + '.';
        }
        overlay.classList.remove('hidden');
        document.addEventListener('keydown', onKeydown);
        fullNameInput.focus();
    }

    function closeModal() {
        overlay.classList.add('hidden');
        document.removeEventListener('keydown', onKeydown);
        currentUserId = null;
    }

    function onKeydown(event) {
        if (event.key === 'Escape') {
            closeModal();
        }
    }

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest('.admin-edit-user-btn');
        if (trigger) {
            openModal(trigger);
            return;
        }
        if (event.target.closest('#editUserModalClose') || event.target.closest('#editUserCancelBtn')) {
            closeModal();
            return;
        }
        if (event.target === overlay) {
            closeModal();
        }
    });

    roleSelect.addEventListener('change', updateAccessClassDisplay);

    function showSuccessToast(message) {
        var existing = document.getElementById('adminEditUserToast');
        if (existing) {
            existing.remove();
        }
        var el = document.createElement('div');
        el.id = 'adminEditUserToast';
        el.className = 'alert-success admin-toast';
        el.textContent = message;
        document.body.appendChild(el);
        window.setTimeout(function () {
            el.remove();
        }, 4000);
    }

    function updateRowInPlace(data) {
        var row = document.querySelector('tr[data-user-row="' + data.userId + '"]');
        if (!row) {
            return;
        }
        var nameLink = row.querySelector('.admin-user-name-link');
        if (nameLink) {
            nameLink.textContent = data.fullName;
        }
        var emailCell = row.querySelector('.admin-email-cell');
        if (emailCell) {
            emailCell.textContent = data.email;
        }
        var roleCell = row.querySelector('.admin-role-cell');
        if (roleCell) {
            roleCell.textContent = data.businessRoleName;
        }
        var accessClassCell = row.querySelector('.admin-access-class');
        if (accessClassCell) {
            accessClassCell.textContent = data.accessClass;
        }
        var statusCell = row.querySelector('.admin-status-cell');
        if (statusCell) {
            statusCell.innerHTML = data.active
                ? '<span class="status-pill status-active">Active</span>'
                : '<span class="status-pill status-inactive">Deactivated</span>';
        }
        var editBtn = row.querySelector('.admin-edit-user-btn');
        if (editBtn) {
            editBtn.setAttribute('data-full-name', data.fullName);
            editBtn.setAttribute('data-email', data.email);
            editBtn.setAttribute('data-business-role-id', data.businessRoleId);
            editBtn.setAttribute('data-active', data.active ? 'true' : 'false');
            editBtn.setAttribute('aria-label', 'Edit User for ' + data.fullName);
        }
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        if (submitting || !currentUserId) {
            return;
        }
        clearError();
        if (!fullNameInput.value.trim()) {
            showError('Full Name is mandatory.');
            return;
        }
        if (!emailInput.value.trim()) {
            showError('Email is mandatory.');
            return;
        }
        if (!reasonInput.value.trim()) {
            showError('A reason is mandatory for this change.');
            return;
        }

        submitting = true;
        saveBtn.disabled = true;
        var originalLabel = saveBtn.textContent;
        saveBtn.textContent = 'Saving...';

        var params = new URLSearchParams(new FormData(form));
        fetch(contextPath() + '/app/admin/users/' + currentUserId + '/edit', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'fetch'},
            body: params.toString()
        }).then(function (response) {
            if (response.ok) {
                return response.json().then(function (body) {
                    return {ok: true, data: body};
                });
            }
            return response.json().then(function (body) {
                return {ok: false, message: body.message || 'The user could not be updated.'};
            }).catch(function () {
                return {ok: false, message: 'The user could not be updated.'};
            });
        }).then(function (result) {
            submitting = false;
            saveBtn.disabled = false;
            saveBtn.textContent = originalLabel;
            if (result.ok) {
                updateRowInPlace(result.data);
                closeModal();
                showSuccessToast('User updated successfully.');
            } else {
                showError(result.message);
            }
        }).catch(function () {
            submitting = false;
            saveBtn.disabled = false;
            saveBtn.textContent = originalLabel;
            showError('Network error - the user was not updated. Please try again.');
        });
    });
})();
