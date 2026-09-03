/**
 * Content Detail "Skip Stage" confirmation modals (ENG-090) - open/close chrome only. Each modal's
 * form is a real POST (same convention as Reschedule/Reassign/Cancel elsewhere on this page), so
 * submitting it navigates the page and the server re-renders the result - no fetch/AJAX needed
 * here, and no client-side re-render of server state. Editor(s)/Publisher(s) pickers and the
 * Editor Lead select are server-rendered .kcpc-model-picker markup, already wired by
 * model-picker.js's own initModelPickers(document) call on page load - nothing to re-init here.
 */
(function () {
    'use strict';

    function wireSkipModal(btnId, overlayId, closeId, cancelId) {
        var btn = document.getElementById(btnId);
        var overlay = document.getElementById(overlayId);
        if (!btn || !overlay) {
            return;
        }
        var closeBtn = document.getElementById(closeId);
        var cancelBtn = document.getElementById(cancelId);

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

        btn.addEventListener('click', openModal);
        closeBtn && closeBtn.addEventListener('click', closeModal);
        cancelBtn && cancelBtn.addEventListener('click', closeModal);
        overlay.addEventListener('click', function (event) {
            if (event.target === overlay) {
                closeModal();
            }
        });
    }

    wireSkipModal('skipShootBtn', 'skipShootModalOverlay', 'skipShootModalClose', 'skipShootCancelBtn');
    wireSkipModal('skipEditBtn', 'skipEditModalOverlay', 'skipEditModalClose', 'skipEditCancelBtn');
})();
