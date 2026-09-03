/**
 * Shared "Platform chip -> popover" interaction: the icon+count chip (rendered by
 * fragments/pipeline-platform-chip.jspf) that opens a <body>-level fixed-position popover listing
 * each Channel's real publication status. Originally Content Pipeline-only (pipeline-dashboard.js,
 * ENG-076); extracted here unchanged so My Work -> Dashboard -> Upcoming Tasks' own Platforms
 * column (which renders the identical chip/popover markup via the same fragment) can reuse the
 * EXACT SAME open/close/position/outside-click/Escape/reposition-on-scroll behavior rather than a
 * second implementation. Every caller wires its own container via {@code wireClicks(container)};
 * the outside-click/Escape listeners are page-global and installed once regardless of how many
 * containers are wired (harmless/no-op if only one page's chips exist).
 */
(function () {
    var openPopover = null;
    var openTrigger = null;
    var globalListenersInstalled = false;

    function positionPopover(popover, trigger) {
        var triggerRect = trigger.getBoundingClientRect();
        var popoverRect = popover.getBoundingClientRect();
        var margin = 6;
        var viewportWidth = document.documentElement.clientWidth;
        var viewportHeight = document.documentElement.clientHeight;

        var top = triggerRect.bottom + margin;
        if (top + popoverRect.height > viewportHeight - margin && triggerRect.top - popoverRect.height - margin > 0) {
            // Not enough room below - open upward instead.
            top = triggerRect.top - popoverRect.height - margin;
        }
        top = Math.max(margin, Math.min(top, viewportHeight - popoverRect.height - margin));

        var left = triggerRect.left;
        left = Math.max(margin, Math.min(left, viewportWidth - popoverRect.width - margin));

        popover.style.top = top + 'px';
        popover.style.left = left + 'px';
    }

    function repositionOpen() {
        if (openPopover && openTrigger) {
            positionPopover(openPopover, openTrigger);
        }
    }

    function closeOpen() {
        if (openPopover) {
            openPopover.classList.add('hidden');
            if (openTrigger) {
                openTrigger.setAttribute('aria-expanded', 'false');
            }
            openPopover = null;
            openTrigger = null;
            window.removeEventListener('scroll', repositionOpen, true);
            window.removeEventListener('resize', repositionOpen);
        }
    }

    function togglePopover(trigger) {
        var popover = document.getElementById(trigger.getAttribute('data-popup-target'));
        if (!popover) {
            return;
        }
        var alreadyOpen = popover === openPopover;
        closeOpen();
        if (!alreadyOpen) {
            popover.classList.remove('hidden');
            positionPopover(popover, trigger);
            trigger.setAttribute('aria-expanded', 'true');
            openPopover = popover;
            openTrigger = trigger;
            window.addEventListener('scroll', repositionOpen, true);
            window.addEventListener('resize', repositionOpen);
        }
    }

    function installGlobalListenersOnce() {
        if (globalListenersInstalled) {
            return;
        }
        globalListenersInstalled = true;
        document.addEventListener('click', function (event) {
            // A click on the popover's own content (e.g. an "Open" link) shouldn't close it first -
            // target="_blank" links still navigate normally regardless of this.
            if (openPopover && openPopover.contains(event.target)) {
                return;
            }
            closeOpen();
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeOpen();
            }
        });
    }

    /** Moves every .pipeline-platform-popover currently inside `container` to <body> - the same
     * table-scroll/sticky-cell clipping concern pipeline-dashboard.js's own portalizePlatformPopovers
     * documented originally, unchanged. */
    function portalize(container) {
        container.querySelectorAll('.pipeline-platform-popover').forEach(function (popover) {
            document.body.appendChild(popover);
        });
    }

    /** Removes every portalled popover on the page - used before a caller replaces its own table
     * markup (e.g. an AJAX re-render), so the previous render's popovers never linger as orphans. */
    function removeAll() {
        document.querySelectorAll('.pipeline-platform-popover').forEach(function (popover) {
            popover.remove();
        });
    }

    /** Wires one delegated click listener on `container` for its own .pipeline-platform-chip
     * triggers, and installs the page-global outside-click/Escape-to-close listeners (once). Call
     * once per container after its chip markup exists in the DOM. */
    function wireClicks(container) {
        installGlobalListenersOnce();
        container.addEventListener('click', function (event) {
            var chipTrigger = event.target.closest('.pipeline-platform-chip[data-popup-target]');
            if (chipTrigger) {
                event.stopPropagation();
                togglePopover(chipTrigger);
            }
        });
    }

    window.PlatformChipPopover = {
        wireClicks: wireClicks,
        portalize: portalize,
        removeAll: removeAll,
        closeOpen: closeOpen
    };
})();
