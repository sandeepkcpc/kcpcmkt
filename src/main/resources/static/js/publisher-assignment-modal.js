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
    var publisherPicker = overlay.querySelector('.kcpc-assignment-picker');

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

    // ---- "Assign Publisher(s)" empty-selection / success feedback -----------------------------
    // assignment-picker.js does the real staging/validation/AJAX work (shared with Shoot/Edit's own
    // pickers, untouched here) and only dispatches these two events as an observability hook; this
    // is the ONLY file that reacts to them, so Shoot/Edit's existing no-reload chip UX is completely
    // unaffected. Root cause this fixes: the "Assign Publisher(s)" button lives in the modal footer
    // (shared with Cancel), outside .kcpc-assignment-picker, associated to its form only via the
    // HTML `form="publishingAssignmentAddForm"` attribute - so a successful assignment previously
    // had NO visible effect at all (no modal close, no refreshed Publisher list/button label/scope
    // table), making a working button indistinguishable from a broken one.
    if (publisherPicker) {
        publisherPicker.addEventListener('kcpc:assignment-empty-selection', function () {
            showPickerMessage(publisherPicker, 'Select at least one Publisher.');
        });
        publisherPicker.addEventListener('kcpc:assignment-saved', function () {
            // Same "action succeeds -> full page refresh" pattern every other Content Detail Action
            // Center form already uses (content-detail.js's plain form.submit() for Reschedule/
            // Reassign/Cancel/Hold/Resume) - re-rendering the whole page from the server is what
            // naturally closes this modal (it starts hidden on a fresh load) and shows the newly
            // assigned Publisher(s), the updated "Assign Publisher(s)"/"Manage Publisher(s) &
            // Publishing Scope" button label, the current verified scope, and any now-available
            // governed actions - all from the one real source of truth, never a second client-side
            // re-render of server state.
            window.location.reload();
        });
    }

    function showPickerMessage(picker, message) {
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
})();
