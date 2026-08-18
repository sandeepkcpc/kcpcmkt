/**
 * ENG-046: Shoot/Edit/Publishing Description (one shared textarea per stage) and their Jira-style
 * Comments threads - both save/post via AJAX, no page reload. Progressive enhancement: without JS
 * both are plain <form>s that POST and redirect back (still fully functional).
 */
(function () {
    function showFlash(container, className, message, timeoutMs) {
        var existing = container.querySelector('.' + className);
        if (existing) {
            existing.remove();
        }
        var el = document.createElement(className === 'ajax-error' ? 'div' : 'span');
        el.className = className;
        el.textContent = message;
        container.appendChild(el);
        setTimeout(function () {
            el.remove();
        }, timeoutMs);
    }

    /**
     * ENG-049: defaults to a clean read view (heading + "Edit" button on one row, text below);
     * clicking Edit reveals the textarea + Cancel/Save in place of the text (and hides Edit, since
     * it'd be redundant while already editing). Cancel discards unsaved edits and reverts to read
     * view. On a successful Save, the read view's text updates to the just-saved value and the form
     * collapses back to read view too - no reload, and the screen doesn't sit open-for-editing by
     * default. The heading + Edit button live in .stage-description-header, a sibling of the text/
     * form (not a wrapper around them) so Edit can sit next to the heading instead of floating below
     * the instruction text.
     */
    function wireDescriptionBlock(block) {
        var form = block.querySelector('.stage-description-form');
        if (!form) {
            return; // read-only viewer - just the plain text, nothing to wire
        }
        var textEl = block.querySelector('.stage-description-view');
        var editBtn = block.querySelector('.stage-description-edit-btn');
        var textarea = form.querySelector('textarea[name="description"]');
        var cancelBtn = form.querySelector('.stage-description-cancel-btn');
        var csrfInput = form.querySelector('input[type="hidden"]');
        var submitBtn = form.querySelector('button[type="submit"]');
        var originalValue = textarea.value;
        var emptyText = block.getAttribute('data-empty-text') || 'Nothing entered yet.';

        function showEditForm() {
            if (textEl) {
                textEl.classList.add('hidden');
            }
            if (editBtn) {
                editBtn.classList.add('hidden');
            }
            form.classList.remove('hidden');
            textarea.focus();
        }

        function showReadView() {
            form.classList.add('hidden');
            if (textEl) {
                textEl.classList.remove('hidden');
            }
            if (editBtn) {
                editBtn.classList.remove('hidden');
            }
        }

        if (editBtn) {
            editBtn.addEventListener('click', showEditForm);
        }
        if (cancelBtn) {
            cancelBtn.addEventListener('click', function () {
                textarea.value = originalValue;
                showReadView();
            });
        }

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var params = new URLSearchParams();
            params.append(csrfInput.name, csrfInput.value);
            params.append('description', textarea.value);
            submitBtn.disabled = true;
            fetch(form.getAttribute('action'), {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'fetch'
                },
                body: params.toString()
            }).then(function (response) {
                submitBtn.disabled = false;
                if (!response.ok) {
                    throw new Error('save-failed');
                }
                originalValue = textarea.value;
                if (textEl) {
                    textEl.textContent = originalValue.trim() === '' ? emptyText : originalValue;
                }
                showReadView();
            }).catch(function () {
                submitBtn.disabled = false;
                showFlash(form, 'ajax-error', 'Could not save. Please try again.', 4000);
            });
        });
    }

    function wireCommentForm(form) {
        var container = form.closest('.stage-comments');
        var list = container.querySelector('.stage-comments-list');
        var commentsAction = container.getAttribute('data-comments-action');

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var textarea = form.querySelector('textarea[name="commentText"]');
            var csrfInput = form.querySelector('input[type="hidden"]');
            var button = form.querySelector('button[type="submit"]');
            var text = textarea.value.trim();
            if (!text) {
                return;
            }
            var params = new URLSearchParams();
            params.append(csrfInput.name, csrfInput.value);
            params.append('commentText', text);
            button.disabled = true;

            fetch(form.getAttribute('action'), {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'fetch'
                },
                body: params.toString()
            }).then(function (response) {
                button.disabled = false;
                if (!response.ok) {
                    throw new Error('comment-failed');
                }
                return response.json();
            }).then(function (data) {
                if (!data) {
                    return;
                }
                // ENG-050: what you just posted is your OWN comment, so it needs the same "..."
                // Edit/Delete menu a page reload would give it - not just plain read-only text.
                list.appendChild(buildCommentEntry(data, commentsAction, csrfInput.name, csrfInput.value));
                textarea.value = '';
            }).catch(function () {
                button.disabled = false;
                showFlash(container, 'ajax-error', 'Could not post the comment. Please try again.', 4000);
            });
        });
    }

    function hiddenCsrfInput(name, value) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        return input;
    }

    function reviewActionsRow(cancelBtn, submitBtn) {
        var row = document.createElement('div');
        row.className = 'review-actions';
        row.appendChild(cancelBtn);
        row.appendChild(submitBtn);
        return row;
    }

    /**
     * ENG-050: builds a freshly-posted comment with the exact same markup shape the server renders
     * (see the c:otherwise branch in deliverable-detail.jsp's Comments block) - own-comment menu,
     * hidden edit form, hidden delete-confirm form - then wires it live via wireCommentMenu, same as
     * every server-rendered comment gets on page load.
     */
    function buildCommentEntry(data, commentsAction, csrfName, csrfValue) {
        var entry = document.createElement('div');
        entry.className = 'stage-comment';
        entry.setAttribute('data-comment-id', data.commentId);
        entry.setAttribute('data-commenter-name', data.commenterName);
        entry.setAttribute('data-created-at', data.createdAt);

        var meta = document.createElement('div');
        meta.className = 'stage-comment-meta';
        var metaText = document.createElement('span');
        metaText.className = 'stage-comment-meta-text';
        var strong = document.createElement('strong');
        strong.textContent = data.commenterName;
        metaText.appendChild(strong);
        metaText.appendChild(document.createTextNode(' · ' + data.createdAt));
        meta.appendChild(metaText);

        var menu = document.createElement('div');
        menu.className = 'stage-comment-menu';
        var menuBtn = document.createElement('button');
        menuBtn.type = 'button';
        menuBtn.className = 'stage-comment-menu-btn';
        menuBtn.setAttribute('aria-label', 'Comment actions');
        menuBtn.textContent = '…';
        var dropdown = document.createElement('div');
        dropdown.className = 'stage-comment-menu-dropdown hidden';
        var editTrigger = document.createElement('button');
        editTrigger.type = 'button';
        editTrigger.className = 'stage-comment-edit-trigger';
        editTrigger.textContent = 'Edit';
        var deleteTrigger = document.createElement('button');
        deleteTrigger.type = 'button';
        deleteTrigger.className = 'stage-comment-delete-trigger';
        deleteTrigger.textContent = 'Delete';
        dropdown.appendChild(editTrigger);
        dropdown.appendChild(deleteTrigger);
        menu.appendChild(menuBtn);
        menu.appendChild(dropdown);
        meta.appendChild(menu);

        var body = document.createElement('div');
        body.className = 'stage-comment-text';
        body.textContent = data.commentText;

        var editForm = document.createElement('form');
        editForm.className = 'action-form stage-comment-edit-form hidden';
        editForm.method = 'post';
        editForm.action = commentsAction + '/' + data.commentId + '/edit';
        editForm.appendChild(hiddenCsrfInput(csrfName, csrfValue));
        var editTextarea = document.createElement('textarea');
        editTextarea.name = 'commentText';
        editTextarea.rows = 2;
        editTextarea.required = true;
        editTextarea.value = data.commentText;
        editForm.appendChild(editTextarea);
        var editCancelBtn = document.createElement('button');
        editCancelBtn.type = 'button';
        editCancelBtn.className = 'stage-comment-edit-cancel-btn';
        editCancelBtn.textContent = 'Cancel';
        var editSaveBtn = document.createElement('button');
        editSaveBtn.type = 'submit';
        editSaveBtn.textContent = 'Save';
        editForm.appendChild(reviewActionsRow(editCancelBtn, editSaveBtn));

        var deleteForm = document.createElement('form');
        deleteForm.className = 'action-form stage-comment-delete-form hidden';
        deleteForm.method = 'post';
        deleteForm.action = commentsAction + '/' + data.commentId + '/delete';
        deleteForm.appendChild(hiddenCsrfInput(csrfName, csrfValue));
        var confirmText = document.createElement('span');
        confirmText.className = 'stage-comment-delete-confirm-text';
        confirmText.textContent = 'Delete this comment? This cannot be undone.';
        deleteForm.appendChild(confirmText);
        var deleteCancelBtn = document.createElement('button');
        deleteCancelBtn.type = 'button';
        deleteCancelBtn.className = 'stage-comment-delete-cancel-btn';
        deleteCancelBtn.textContent = 'Cancel';
        var deleteConfirmBtn = document.createElement('button');
        deleteConfirmBtn.type = 'submit';
        deleteConfirmBtn.className = 'stage-comment-delete-confirm-btn';
        deleteConfirmBtn.textContent = 'Yes, delete';
        deleteForm.appendChild(reviewActionsRow(deleteCancelBtn, deleteConfirmBtn));

        entry.appendChild(meta);
        entry.appendChild(body);
        entry.appendChild(editForm);
        entry.appendChild(deleteForm);

        wireCommentMenu(entry);
        return entry;
    }

    function closeAllCommentMenus() {
        var openDropdowns = document.querySelectorAll('.stage-comment-menu-dropdown:not(.hidden)');
        for (var i = 0; i < openDropdowns.length; i++) {
            openDropdowns[i].classList.add('hidden');
            // ENG-050.1: once portaled into <body> (see openCommentDropdown below), the dropdown is
            // no longer a DOM sibling of its trigger button, so `previousElementSibling` can't find
            // it anymore - `_menuBtn` (set at wire time) is the stable link back to it.
            var btn = openDropdowns[i]._menuBtn || openDropdowns[i].previousElementSibling;
            if (btn) {
                btn.classList.remove('open');
            }
        }
    }
    document.addEventListener('click', closeAllCommentMenus);
    // .stage-comments-list scrolls internally (overflow-y: auto) - a scroll there won't move a
    // portaled (position:fixed) dropdown, which would otherwise drift away from its trigger button.
    // Simplest correct behavior: just close it, same as scrolling the whole page. `capture: true` is
    // required - scroll events don't bubble, so only the capture phase on window sees one that fired
    // on an inner scrollable element like .stage-comments-list.
    window.addEventListener('scroll', closeAllCommentMenus, true);
    window.addEventListener('resize', closeAllCommentMenus);

    /**
     * ENG-050.1: .stage-comments-list clips overflowing content (overflow-y: auto; max-height:
     * 320px) - a dropdown opened on a comment near the bottom of that scrollable list gets cut off
     * by the clip, no matter how high its z-index is (z-index only affects paint order among
     * unclipped content, not clipping itself). Reparenting the dropdown into <body> and switching it
     * to position:fixed (computed from the trigger button's live on-screen rect) escapes that
     * ancestor's clip entirely - the standard "portal" pattern for exactly this situation.
     */
    function openCommentDropdown(menuBtn, dropdown) {
        if (dropdown.parentElement !== document.body) {
            document.body.appendChild(dropdown);
            dropdown.classList.add('portal');
            dropdown._menuBtn = menuBtn;
        }
        var rect = menuBtn.getBoundingClientRect();
        dropdown.style.top = (rect.bottom + 4) + 'px';
        dropdown.style.right = (window.innerWidth - rect.right) + 'px';
        dropdown.classList.remove('hidden');
        menuBtn.classList.add('open');
    }

    /**
     * ENG-050: "..." menu -> Edit/Delete, shown only on your own comment (server-side gate;
     * commenter.id == user.id). Edit swaps the read text for an inline textarea (Cancel/Save);
     * Delete swaps it for a small inline "are you sure" instead of a native confirm() dialog (which
     * would also break the AJAX flow this page otherwise avoids reload for). Both post via AJAX -
     * same request/response shape as wireDescriptionBlock above. A hard DELETE is never sent - the
     * server soft-deletes (StageCommentService#deleteComment) and this just reflects that locally.
     */
    function wireCommentMenu(comment) {
        var menuBtn = comment.querySelector('.stage-comment-menu-btn');
        var dropdown = comment.querySelector('.stage-comment-menu-dropdown');
        var editTrigger = comment.querySelector('.stage-comment-edit-trigger');
        var deleteTrigger = comment.querySelector('.stage-comment-delete-trigger');
        var textEl = comment.querySelector('.stage-comment-text');
        var editForm = comment.querySelector('.stage-comment-edit-form');
        var deleteForm = comment.querySelector('.stage-comment-delete-form');

        function showText() {
            if (editForm) {
                editForm.classList.add('hidden');
            }
            if (deleteForm) {
                deleteForm.classList.add('hidden');
            }
            if (textEl) {
                textEl.classList.remove('hidden');
            }
        }

        if (menuBtn && dropdown) {
            menuBtn.addEventListener('click', function (event) {
                event.stopPropagation();
                var wasOpen = !dropdown.classList.contains('hidden');
                closeAllCommentMenus();
                if (!wasOpen) {
                    openCommentDropdown(menuBtn, dropdown);
                }
            });
            dropdown.addEventListener('click', function (event) {
                event.stopPropagation();
            });
        }

        if (editTrigger && editForm) {
            editTrigger.addEventListener('click', function () {
                closeAllCommentMenus();
                if (deleteForm) {
                    deleteForm.classList.add('hidden');
                }
                if (textEl) {
                    textEl.classList.add('hidden');
                }
                editForm.classList.remove('hidden');
                var ta = editForm.querySelector('textarea');
                if (ta) {
                    ta.focus();
                }
            });
            var editCancelBtn = editForm.querySelector('.stage-comment-edit-cancel-btn');
            if (editCancelBtn) {
                editCancelBtn.addEventListener('click', function () {
                    var ta = editForm.querySelector('textarea');
                    if (ta && textEl) {
                        ta.value = textEl.textContent;
                    }
                    showText();
                });
            }
            editForm.addEventListener('submit', function (event) {
                event.preventDefault();
                var textarea = editForm.querySelector('textarea[name="commentText"]');
                var csrfInput = editForm.querySelector('input[type="hidden"]');
                var submitBtn = editForm.querySelector('button[type="submit"]');
                var text = textarea.value.trim();
                if (!text) {
                    return;
                }
                var params = new URLSearchParams();
                params.append(csrfInput.name, csrfInput.value);
                params.append('commentText', text);
                submitBtn.disabled = true;
                fetch(editForm.getAttribute('action'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'X-Requested-With': 'fetch'
                    },
                    body: params.toString()
                }).then(function (response) {
                    submitBtn.disabled = false;
                    if (!response.ok) {
                        throw new Error('comment-edit-failed');
                    }
                    return response.json();
                }).then(function (data) {
                    if (!data) {
                        return;
                    }
                    if (textEl) {
                        textEl.textContent = data.commentText;
                    }
                    var metaText = comment.querySelector('.stage-comment-meta-text');
                    if (metaText && !metaText.querySelector('.stage-comment-edited')) {
                        metaText.appendChild(document.createTextNode(' · '));
                        var edited = document.createElement('span');
                        edited.className = 'stage-comment-edited';
                        edited.textContent = 'edited';
                        metaText.appendChild(edited);
                    }
                    showText();
                }).catch(function () {
                    submitBtn.disabled = false;
                    showFlash(editForm, 'ajax-error', 'Could not save. Please try again.', 4000);
                });
            });
        }

        if (deleteTrigger && deleteForm) {
            deleteTrigger.addEventListener('click', function () {
                closeAllCommentMenus();
                if (editForm) {
                    editForm.classList.add('hidden');
                }
                if (textEl) {
                    textEl.classList.add('hidden');
                }
                deleteForm.classList.remove('hidden');
            });
            var deleteCancelBtn = deleteForm.querySelector('.stage-comment-delete-cancel-btn');
            if (deleteCancelBtn) {
                deleteCancelBtn.addEventListener('click', showText);
            }
            deleteForm.addEventListener('submit', function (event) {
                event.preventDefault();
                var csrfInput = deleteForm.querySelector('input[type="hidden"]');
                var submitBtn = deleteForm.querySelector('button[type="submit"]');
                var params = new URLSearchParams();
                params.append(csrfInput.name, csrfInput.value);
                submitBtn.disabled = true;
                fetch(deleteForm.getAttribute('action'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'X-Requested-With': 'fetch'
                    },
                    body: params.toString()
                }).then(function (response) {
                    submitBtn.disabled = false;
                    if (!response.ok) {
                        throw new Error('comment-delete-failed');
                    }
                    var meta = comment.querySelector('.stage-comment-meta');
                    if (meta) {
                        meta.innerHTML = '';
                        var strong = document.createElement('strong');
                        strong.textContent = comment.getAttribute('data-commenter-name') || '';
                        meta.appendChild(strong);
                        meta.appendChild(document.createTextNode(' · ' + (comment.getAttribute('data-created-at') || '')));
                    }
                    editForm.remove();
                    deleteForm.remove();
                    if (textEl) {
                        textEl.classList.remove('hidden');
                        textEl.classList.add('muted');
                        textEl.textContent = 'This comment was deleted.';
                    }
                }).catch(function () {
                    submitBtn.disabled = false;
                    showFlash(deleteForm, 'ajax-error', 'Could not delete. Please try again.', 4000);
                });
            });
        }
    }

    var descriptionBlocks = document.querySelectorAll('.stage-description');
    for (var i = 0; i < descriptionBlocks.length; i++) {
        wireDescriptionBlock(descriptionBlocks[i]);
    }
    var commentForms = document.querySelectorAll('.stage-comment-form');
    for (var j = 0; j < commentForms.length; j++) {
        wireCommentForm(commentForms[j]);
    }
    var existingComments = document.querySelectorAll('.stage-comment[data-comment-id]');
    for (var k = 0; k < existingComments.length; k++) {
        wireCommentMenu(existingComments[k]);
    }
})();
