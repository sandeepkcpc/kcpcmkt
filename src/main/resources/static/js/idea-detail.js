/**
 * Idea Detail / Review redesign - presentation-only glue, mirroring content-detail.js's own
 * pattern for its "Back to Pipeline" link:
 *   1. Back to Idea Queue - restores the Idea Queue's exact last-seen filter/sort/page state
 *      (written to sessionStorage by idea-queue-dashboard.js) instead of resetting to page 1/
 *      default filters.
 *   2. Review Decision form - purely cosmetic dynamic label text on the Reason field as Decision
 *      changes, plus a live character counter (same mechanics as idea-submit.js's counter). Never
 *      sets/removes the `required` attribute here - the mandatory-for-Reject rule stays enforced
 *      only by IdeaService.decide server-side (see the Reason label's own static text, which
 *      already states the exact rule); this script only updates helper wording, so there is no
 *      duplicated validation logic to drift out of sync with the backend.
 */
(function () {
    // --- Back to Idea Queue -----------------------------------------------------------------
    var backLink = document.getElementById('ideaDetailBackLink');
    if (backLink) {
        try {
            var lastQueueUrl = sessionStorage.getItem('kcpcIdeaQueueUrl');
            if (lastQueueUrl) {
                backLink.href = lastQueueUrl;
            }
        } catch (e) {
            // Storage unavailable - the link already has its default /app/ideas href.
        }
    }

    // --- Review Decision form ----------------------------------------------------------------
    var form = document.getElementById('idea-review-form');
    if (!form) {
        return;
    }
    var decisionField = document.getElementById('idea-review-decision');
    var reasonField = document.getElementById('idea-review-reason');
    var reasonLabel = document.getElementById('idea-review-reason-label');

    var REASON_HELP_TEXT = {
        '': 'Reason (mandatory for Reject; optional for Retain)',
        APPROVE: 'Reason (optional; not used for Approve)',
        REJECT: 'Reason * (mandatory for Reject)',
        RETAIN: 'Reason (optional for Retain)'
    };

    function updateReasonLabel() {
        if (!reasonLabel) {
            return;
        }
        var text = REASON_HELP_TEXT[decisionField.value] || REASON_HELP_TEXT[''];
        reasonLabel.textContent = text;
    }

    if (decisionField) {
        decisionField.addEventListener('change', updateReasonLabel);
    }

    function updateReasonCounter() {
        var counter = form.querySelector('.char-counter[data-counter-for="idea-review-reason"]');
        if (!counter || !reasonField) {
            return;
        }
        var limit = 500;
        var length = reasonField.value.length;
        counter.textContent = length + ' / ' + limit;
        counter.classList.toggle('char-counter-at-limit', length >= limit);
        counter.classList.toggle('char-counter-near-limit', length >= limit * 0.9 && length < limit);
    }

    if (reasonField) {
        reasonField.addEventListener('input', updateReasonCounter);
    }

    form.addEventListener('reset', function () {
        // Native reset runs after this handler returns; re-sync the label/counter on the next tick.
        window.setTimeout(function () {
            updateReasonLabel();
            updateReasonCounter();
        }, 0);
    });
})();
