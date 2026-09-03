/**
 * "Create Idea" popup (fragments/idea-submit-modal.jspf) - the Reviews Workspace's tab header
 * "+ Create Idea" button opens the exact same Submit Idea form as /app/ideas/new, wired for AJAX
 * submission by idea-submit.js. This module only owns open/close (button, overlay click, Escape,
 * close button) and reacts to a successful submission - the form's own validation/submission logic
 * lives entirely in idea-submit.js, not duplicated here.
 *
 * wireIdeaCreateModal(root) instead of a plain self-invoking IIFE - reviews-content.jspf always
 * includes the modal markup regardless of which tab (Ideas/Shoot/Edit) is active, and
 * reviews-workspace.js swaps that whole region's innerHTML via AJAX on every tab/filter change, so
 * this needs re-wiring after every swap exactly like wireScriptDescriptionModal already does.
 */
(function () {
    function wireIdeaCreateModal(root) {
        var openBtn = root.querySelector('#ideaCreateModalOpen');
        var overlay = root.querySelector('#ideaCreateModalOverlay');
        if (!openBtn || !overlay) {
            return;
        }
        var closeBtn = root.querySelector('#ideaCreateModalClose');

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
        overlay.addEventListener('click', function (event) {
            if (event.target === overlay) {
                closeModal();
            }
        });
    }

    // Registered once at module load (NOT inside wireIdeaCreateModal, which re-runs on every AJAX
    // region swap - a listener added there would accumulate one copy per swap). Looks up the
    // current overlay fresh each time rather than a closure captured at some earlier wiring, so it
    // still works correctly no matter how many swaps have happened since.
    document.addEventListener('kcpc:idea-created', function () {
        var overlay = document.querySelector('#ideaCreateModalOverlay');
        if (overlay) {
            overlay.classList.add('hidden');
        }
    });

    window.wireIdeaCreateModal = wireIdeaCreateModal;
    wireIdeaCreateModal(document);
})();
