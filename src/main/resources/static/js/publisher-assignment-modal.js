/**
 * Publishing tab's "Assign Publisher(s)" modal: open/close chrome plus a session-only "what
 * changed" log. All the actual work (Publisher chips, Publication Target add/remove) is done by
 * the existing assignment-picker.js / publication-scope.js handlers already wired to the forms
 * this modal contains - this file only toggles visibility and listens for the
 * 'kcpc:scope-changed' event those handlers already dispatch on a successful save, so nothing
 * here re-derives or duplicates any workflow/permission/publication rule. Every scope edit made
 * while the modal is open is persisted immediately by its own real backend call (same as the
 * Planning tab's own Planned Outputs table) - "Cancel" only closes the dialog, it never discards
 * already-saved changes, and "Assign Publisher(s)" only submits the Publisher picker's own
 * existing batch-add form.
 */
(function () {
    var openBtn = document.getElementById('publishingAssignmentModalOpen');
    var overlay = document.getElementById('publishingAssignmentModalOverlay');
    if (!openBtn || !overlay) {
        return;
    }
    var closeBtn = document.getElementById('publishingAssignmentModalClose');
    var cancelBtn = document.getElementById('publishingAssignmentModalCancel');
    var changeLog = document.getElementById('publishingScopeChangeLog');

    function openModal() {
        overlay.classList.remove('hidden');
        document.addEventListener('keydown', onKeydown);
    }

    function closeModal() {
        overlay.classList.add('hidden');
        document.removeEventListener('keydown', onKeydown);
    }

    function onKeydown(event) {
        if (event.key === 'Escape') {
            closeModal();
        }
    }

    openBtn.addEventListener('click', openModal);
    closeBtn && closeBtn.addEventListener('click', closeModal);
    cancelBtn && cancelBtn.addEventListener('click', closeModal);
    overlay.addEventListener('click', function (event) {
        if (event.target === overlay) {
            closeModal();
        }
    });

    // ---- Change log: purely a session-visible summary of edits already saved, for the manager to
    // review before clicking "Assign Publisher(s)" - never a pending/unsaved state. ----
    document.addEventListener('kcpc:scope-changed', function (event) {
        if (overlay.classList.contains('hidden') || !changeLog) {
            return;
        }
        var detail = event.detail || {};
        var li = document.createElement('li');
        li.className = 'scope-change-item scope-change-' + detail.type;
        var label = detail.type === 'added' ? 'Added' : 'Removed';
        var text = label + ' ' + (detail.platform || '') + (detail.channel ? ' → ' + detail.channel : '');
        li.textContent = text;
        changeLog.appendChild(li);
        changeLog.classList.remove('hidden');
    });
})();
