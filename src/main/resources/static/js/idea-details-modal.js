/**
 * Idea Details popup (My Work / Task Detail screens): native <dialog>, same open/close pattern as
 * permission-checklist.js's permDetailDialog. All content is already rendered server-side into
 * #ideaDetailsDialog by fragments/idea-details-modal.jspf - this script only opens/closes it,
 * never populates or edits any field.
 */
(function () {
    document.addEventListener('click', function (event) {
        if (event.target.closest('[data-idea-details-trigger]')) {
            var dialog = document.getElementById('ideaDetailsDialog');
            if (dialog && typeof dialog.showModal === 'function') {
                dialog.showModal();
            }
            return;
        }
        if (event.target.closest('.idea-details-close')) {
            var dialog = document.getElementById('ideaDetailsDialog');
            if (dialog) {
                dialog.close();
            }
        }
    });
})();
