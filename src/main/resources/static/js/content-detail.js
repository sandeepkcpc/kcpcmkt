/**
 * ENG-082: CEO/MM Content Detail page - tab switching is already handled by my-work-tabs.js
 * (reused as-is, see the .my-work-tab/.my-work-tab-panel markup in deliverable-detail.jsp). This
 * file covers four things specific to this page's redesign:
 *   1. Back to Pipeline - restores the Pipeline's exact last-seen filter/sort/page state (written
 *      to sessionStorage by pipeline-dashboard.js) instead of resetting to page 1/default filters.
 *   2. The Action Center - reveals each action's own real form (same endpoints/fields the old
 *      admin-actions bar and stage review-decision forms always used, just relocated here) instead
 *      of submitting anything itself; no workflow/authorization logic is duplicated here, every
 *      form still POSTs to the same DeliverableMvcController endpoint it always did.
 *   3. Publishing tab platform popovers - adapted from pipeline-dashboard.js's own popover logic
 *      (same positioning/viewport-clamping math), duplicated rather than shared to avoid touching
 *      that already-shipped, already-tested file for this page's sake (see ENG-082 plan).
 *   4. "View full timeline" (Overview tab's Timeline/Activity preview card) - switches to the
 *      existing Timeline tab by clicking its own .my-work-tab button, reusing my-work-tabs.js's
 *      click-driven show/hide exactly as a user clicking that tab directly would, rather than
 *      duplicating any tab-switch logic here.
 */
(function () {
    var page = document.getElementById('contentDetailPage');
    if (!page) {
        return;
    }

    // --- Back to Pipeline -----------------------------------------------------------------------
    var backLink = document.getElementById('contentDetailBackLink');
    if (backLink) {
        try {
            var lastPipelineUrl = sessionStorage.getItem('kcpcPipelineUrl');
            if (lastPipelineUrl) {
                backLink.href = lastPipelineUrl;
            }
        } catch (e) {
            // Storage unavailable - the link already has its default /app/pipeline href.
        }
    }

    // --- Overview tab's "View full timeline" -------------------------------------------------
    var viewFullTimelineBtn = document.getElementById('contentDetailViewFullTimeline');
    if (viewFullTimelineBtn) {
        viewFullTimelineBtn.addEventListener('click', function () {
            var timelineTab = page.querySelector('.my-work-tab[data-tab="timeline"]');
            if (timelineTab) {
                timelineTab.click();
            }
        });
    }

    // --- Action Center ----------------------------------------------------------------------------
    var actionForms = page.querySelectorAll('.content-detail-action-form');

    function hideAllActionForms() {
        actionForms.forEach(function (form) {
            form.classList.add('hidden');
        });
    }

    page.querySelectorAll('.content-detail-action-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var key = btn.getAttribute('data-action-key');
            var form = page.querySelector('.content-detail-action-form[data-action-key="' + key + '"]');
            if (!form) {
                return;
            }
            if (btn.getAttribute('data-requires-reason') === 'false') {
                // Approve/Resume - no extra fields to collect, submit straight away.
                form.submit();
                return;
            }
            var alreadyOpen = !form.classList.contains('hidden');
            hideAllActionForms();
            if (!alreadyOpen) {
                form.classList.remove('hidden');
                form.scrollIntoView({behavior: 'smooth', block: 'nearest'});
            }
        });
    });

    // --- Publishing tab platform popovers ---------------------------------------------------------
    // Same portal/positioning pattern as pipeline-dashboard.js's Platforms column (ENG-076/081):
    // popovers move to <body> once so the tab panel's own layout/overflow can never clip them.
    function portalizePlatformPopovers() {
        page.querySelectorAll('.pipeline-platform-popover').forEach(function (popover) {
            document.body.appendChild(popover);
        });
    }

    var openPlatformPopover = null;
    var openPlatformTrigger = null;

    function positionPlatformPopover(popover, trigger) {
        var triggerRect = trigger.getBoundingClientRect();
        var popoverRect = popover.getBoundingClientRect();
        var margin = 6;
        var viewportWidth = document.documentElement.clientWidth;
        var viewportHeight = document.documentElement.clientHeight;

        var top = triggerRect.bottom + margin;
        if (top + popoverRect.height > viewportHeight - margin && triggerRect.top - popoverRect.height - margin > 0) {
            top = triggerRect.top - popoverRect.height - margin;
        }
        top = Math.max(margin, Math.min(top, viewportHeight - popoverRect.height - margin));

        var left = triggerRect.left;
        left = Math.max(margin, Math.min(left, viewportWidth - popoverRect.width - margin));

        popover.style.top = top + 'px';
        popover.style.left = left + 'px';
    }

    function repositionOpenPlatformPopover() {
        if (openPlatformPopover && openPlatformTrigger) {
            positionPlatformPopover(openPlatformPopover, openPlatformTrigger);
        }
    }

    function closeOpenPlatformPopover() {
        if (openPlatformPopover) {
            openPlatformPopover.classList.add('hidden');
            if (openPlatformTrigger) {
                openPlatformTrigger.setAttribute('aria-expanded', 'false');
            }
            openPlatformPopover = null;
            openPlatformTrigger = null;
            window.removeEventListener('scroll', repositionOpenPlatformPopover, true);
            window.removeEventListener('resize', repositionOpenPlatformPopover);
        }
    }

    function togglePlatformPopover(trigger) {
        var popover = document.getElementById(trigger.getAttribute('data-popup-target'));
        if (!popover) {
            return;
        }
        var alreadyOpen = popover === openPlatformPopover;
        closeOpenPlatformPopover();
        if (!alreadyOpen) {
            popover.classList.remove('hidden');
            positionPlatformPopover(popover, trigger);
            trigger.setAttribute('aria-expanded', 'true');
            openPlatformPopover = popover;
            openPlatformTrigger = trigger;
            window.addEventListener('scroll', repositionOpenPlatformPopover, true);
            window.addEventListener('resize', repositionOpenPlatformPopover);
        }
    }

    page.addEventListener('click', function (event) {
        var chipTrigger = event.target.closest('.pipeline-platform-chip[data-popup-target]');
        if (chipTrigger) {
            event.stopPropagation();
            togglePlatformPopover(chipTrigger);
        }
    });
    document.addEventListener('click', function (event) {
        if (openPlatformPopover && openPlatformPopover.contains(event.target)) {
            return;
        }
        closeOpenPlatformPopover();
    });
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeOpenPlatformPopover();
        }
    });

    portalizePlatformPopovers();
})();
