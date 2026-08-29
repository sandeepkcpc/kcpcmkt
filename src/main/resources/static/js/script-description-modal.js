/**
 * Idea Description view/edit modal (fragments/idea-description-modal*.jspf). Clicking the note
 * icon opens a modal showing the complete, untruncated text (server already renders it in full
 * via <c:out> - this script only toggles visibility, no truncation/fetch of its own). CEO/
 * Marketing Manager additionally get an Edit control (the server only renders it for them -
 * IdeaService#updateDescription re-checks independently, this is UI convenience only): clicking
 * it swaps the read-only view for a Description + mandatory Update Reason form; Cancel discards
 * unsaved changes and returns to the read-only view; Save Changes submits.
 *
 * Two submission modes, picked by the edit form's own data-ajax attribute (set per-page by the
 * fragment's caller):
 *   - idea-detail.jsp (data-ajax="false"): a real <form method="post"> - native submission, full
 *     page reload, exactly like every other form on that page.
 *   - Reviews Workspace (data-ajax="true"): this whole panel is already AJAX-swapped
 *     (reviews-workspace.js), so a native submit here would navigate away from the panel's
 *     current filter/selection state. Intercepted instead: fetch() with X-Requested-With, then a
 *     'kcpc:idea-description-updated' event so reviews-workspace.js can refresh the panel in
 *     place (mirrors the kcpc:scope-changed event publication-scope.js already dispatches for the
 *     same "AJAX write, someone else's job to refresh the view" reason).
 *
 * wireScriptDescriptionModal(root) instead of a plain self-invoking IIFE - Content Detail/Idea
 * Detail render this once at page load (root = document), but the Reviews Workspace's Ideas panel
 * (reviews-ideas.jspf) swaps its whole region's innerHTML via AJAX on every idea selection
 * (reviews-workspace.js), so its copy of the icon/modal needs re-wiring after every swap exactly
 * like wireStageDiscussion/initModelPickers already do.
 */
(function () {
    function wireScriptDescriptionModal(root) {
        var openBtn = root.querySelector('#scriptDescriptionOpen');
        var overlay = root.querySelector('#scriptDescriptionModalOverlay');
        if (!openBtn || !overlay) {
            return;
        }
        var closeBtn = root.querySelector('#scriptDescriptionModalClose');
        var editToggleBtn = root.querySelector('#scriptDescriptionEditToggle');
        var viewBody = root.querySelector('#scriptDescriptionViewBody');
        var editForm = root.querySelector('#scriptDescriptionEditForm');
        var descTextarea = editForm ? editForm.querySelector('#scriptDescriptionEditTextarea') : null;
        var reasonTextarea = editForm ? editForm.querySelector('#scriptDescriptionReasonTextarea') : null;
        var editError = editForm ? editForm.querySelector('#scriptDescriptionEditError') : null;
        var cancelBtn = editForm ? editForm.querySelector('#scriptDescriptionEditCancel') : null;
        var originalDescription = descTextarea ? descTextarea.value : '';

        function openModal() {
            overlay.classList.remove('hidden');
            document.addEventListener('keydown', onKeydown);
        }

        function closeModal() {
            overlay.classList.add('hidden');
            document.removeEventListener('keydown', onKeydown);
            exitEditMode();
        }

        function onKeydown(event) {
            if (event.key === 'Escape') {
                closeModal();
            }
        }

        function enterEditMode() {
            if (!editForm) {
                return;
            }
            if (viewBody) {
                viewBody.classList.add('hidden');
            }
            editForm.classList.remove('hidden');
            if (editToggleBtn) {
                editToggleBtn.classList.add('hidden');
            }
            if (reasonTextarea) {
                reasonTextarea.focus();
            }
        }

        function exitEditMode() {
            if (!editForm) {
                return;
            }
            editForm.classList.add('hidden');
            if (viewBody) {
                viewBody.classList.remove('hidden');
            }
            if (editToggleBtn) {
                editToggleBtn.classList.remove('hidden');
            }
            if (descTextarea) {
                descTextarea.value = originalDescription; // discard any unsaved typing
            }
            if (reasonTextarea) {
                reasonTextarea.value = '';
            }
            if (editError) {
                editError.classList.add('hidden');
                editError.textContent = '';
            }
        }

        openBtn.addEventListener('click', openModal);
        closeBtn && closeBtn.addEventListener('click', closeModal);
        overlay.addEventListener('click', function (event) {
            if (event.target === overlay) {
                closeModal();
            }
        });
        editToggleBtn && editToggleBtn.addEventListener('click', enterEditMode);
        cancelBtn && cancelBtn.addEventListener('click', exitEditMode);

        if (editForm && editForm.dataset.ajax === 'true') {
            editForm.addEventListener('submit', function (event) {
                event.preventDefault();
                if (reasonTextarea && !reasonTextarea.value.trim()) {
                    if (editError) {
                        editError.textContent = 'A reason is mandatory to update the Idea Description.';
                        editError.classList.remove('hidden');
                    }
                    return;
                }
                var submitBtn = editForm.querySelector('button[type="submit"]');
                if (submitBtn) {
                    submitBtn.disabled = true;
                }
                var params = new URLSearchParams(new FormData(editForm));
                fetch(editForm.getAttribute('action'), {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'fetch'},
                    body: params.toString()
                }).then(function (response) {
                    if (response.ok) {
                        return {ok: true};
                    }
                    return response.json().then(function (body) {
                        return {ok: false, message: body.message || 'The Idea Description could not be updated.'};
                    }).catch(function () {
                        return {ok: false, message: 'The Idea Description could not be updated.'};
                    });
                }).then(function (result) {
                    if (submitBtn) {
                        submitBtn.disabled = false;
                    }
                    if (result.ok) {
                        document.dispatchEvent(new CustomEvent('kcpc:idea-description-updated'));
                    } else if (editError) {
                        editError.textContent = result.message;
                        editError.classList.remove('hidden');
                    }
                }).catch(function () {
                    if (submitBtn) {
                        submitBtn.disabled = false;
                    }
                    if (editError) {
                        editError.textContent = 'Network error - the Idea Description was not updated. Please try again.';
                        editError.classList.remove('hidden');
                    }
                });
            });
        }
    }

    window.wireScriptDescriptionModal = wireScriptDescriptionModal;
    wireScriptDescriptionModal(document);
})();
