/**
 * ENG-071: Content Pipeline - per-column filter popups. Filtering, sorting, and pagination are all
 * real server-side GET requests (query params on the existing /app/pipeline route - see
 * LandingMvcController and pipeline.jsp); every field lives inside the one shared
 * #pipelineFilterForm, so clicking Apply in any popup submits every other column's current filter
 * value too. This script only ever does two purely cosmetic things: opening/closing a popup, and
 * clearing a popup's own fields before an explicit Clear-triggered submit. It never decides what
 * rows are shown - that's the server's job.
 */
(function () {
    var triggers = document.querySelectorAll('.pipeline-filter-trigger');
    var openPopup = null;

    function closeOpenPopup() {
        if (openPopup) {
            openPopup.classList.add('hidden');
            openPopup = null;
        }
    }

    triggers.forEach(function (trigger) {
        var popup = document.getElementById(trigger.getAttribute('data-popup-target'));
        if (!popup) {
            return;
        }
        trigger.addEventListener('click', function (event) {
            event.stopPropagation();
            var alreadyOpen = popup === openPopup;
            closeOpenPopup();
            if (!alreadyOpen) {
                popup.classList.remove('hidden');
                openPopup = popup;
            }
        });
        popup.addEventListener('click', function (event) {
            event.stopPropagation();
        });
    });

    document.addEventListener('click', closeOpenPopup);

    document.querySelectorAll('.pipeline-filter-clear').forEach(function (clearBtn) {
        clearBtn.addEventListener('click', function () {
            var popup = clearBtn.closest('.pipeline-filter-popup');
            if (!popup) {
                return;
            }
            popup.querySelectorAll('input[type="text"], input[type="date"]').forEach(function (field) {
                field.value = '';
            });
            popup.querySelectorAll('select').forEach(function (field) {
                field.value = '';
            });
            popup.querySelectorAll('input[type="radio"]').forEach(function (field) {
                field.checked = field.value === '';
            });
            popup.querySelectorAll('input[type="checkbox"]').forEach(function (field) {
                field.checked = false;
            });
            clearBtn.form.submit();
        });
    });
})();
