/**
 * Reference Link inline view/edit widget (fragments/idea-reference-link-edit.jspf). CEO/Marketing
 * Manager only get the pencil icon (the server only renders it for them -
 * IdeaService#updateReferenceLink re-checks independently, this is UI convenience only): clicking
 * it swaps the read-only link display for an editable input with Save/Cancel; Cancel discards
 * unsaved changes and returns to the read-only view.
 *
 * Two submission modes, picked by the edit form's own data-ajax attribute (set per-page by the
 * fragment's caller):
 *   - idea-detail.jsp (data-ajax="false"): a real <form method="post"> - native submission, full
 *     page reload, exactly like every other form on that page.
 *   - Reviews Workspace (data-ajax="true"): this whole panel is already AJAX-swapped
 *     (reviews-workspace.js), so a native submit here would navigate away from the panel's
 *     current filter/selection state. Intercepted instead: fetch() with X-Requested-With, then a
 *     'kcpc:idea-reference-link-updated' event so reviews-workspace.js can refresh the panel in
 *     place (mirrors 'kcpc:idea-description-updated' in script-description-modal.js).
 *
 * wireIdeaReferenceLinkEdit(root) instead of a plain self-invoking IIFE - idea-detail.jsp renders
 * this once at page load (root = document), but the Reviews Workspace's Ideas panel
 * (reviews-ideas.jspf) swaps its whole region's innerHTML via AJAX on every idea selection
 * (reviews-workspace.js), so its copy needs re-wiring after every swap exactly like
 * wireScriptDescriptionModal already does.
 */
(function () {
    function isValidHttpUrl(value) {
        if (!value) {
            return false;
        }
        try {
            var url = new URL(value);
            return url.protocol === 'http:' || url.protocol === 'https:';
        } catch (e) {
            return false;
        }
    }

    function wireIdeaReferenceLinkEdit(root) {
        var editToggleBtn = root.querySelector('#refLinkEditToggle');
        var editForm = root.querySelector('#refLinkEditForm');
        if (!editToggleBtn || !editForm) {
            return;
        }
        var viewSpan = root.querySelector('#refLinkView');
        var input = editForm.querySelector('#refLinkEditInput');
        var editError = editForm.querySelector('#refLinkEditError');
        var cancelBtn = editForm.querySelector('#refLinkEditCancel');
        var originalValue = input ? input.value : '';

        function enterEditMode() {
            if (viewSpan) {
                viewSpan.classList.add('hidden');
            }
            editForm.classList.remove('hidden');
            if (input) {
                input.focus();
            }
        }

        function exitEditMode() {
            editForm.classList.add('hidden');
            if (viewSpan) {
                viewSpan.classList.remove('hidden');
            }
            if (input) {
                input.value = originalValue; // discard any unsaved typing
            }
            if (editError) {
                editError.classList.add('hidden');
                editError.textContent = '';
            }
        }

        editToggleBtn.addEventListener('click', enterEditMode);
        cancelBtn && cancelBtn.addEventListener('click', exitEditMode);

        if (editForm.dataset.ajax === 'true') {
            editForm.addEventListener('submit', function (event) {
                event.preventDefault();
                var value = input ? input.value.trim() : '';
                if (!isValidHttpUrl(value)) {
                    if (editError) {
                        editError.textContent = 'Enter a valid URL starting with http:// or https://.';
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
                        return {ok: false, message: body.message || 'The Reference Link could not be updated.'};
                    }).catch(function () {
                        return {ok: false, message: 'The Reference Link could not be updated.'};
                    });
                }).then(function (result) {
                    if (submitBtn) {
                        submitBtn.disabled = false;
                    }
                    if (result.ok) {
                        originalValue = value;
                        document.dispatchEvent(new CustomEvent('kcpc:idea-reference-link-updated'));
                    } else if (editError) {
                        editError.textContent = result.message;
                        editError.classList.remove('hidden');
                    }
                }).catch(function () {
                    if (submitBtn) {
                        submitBtn.disabled = false;
                    }
                    if (editError) {
                        editError.textContent = 'Network error - the Reference Link was not updated. Please try again.';
                        editError.classList.remove('hidden');
                    }
                });
            });
        }
    }

    window.wireIdeaReferenceLinkEdit = wireIdeaReferenceLinkEdit;
    wireIdeaReferenceLinkEdit(document);
})();
