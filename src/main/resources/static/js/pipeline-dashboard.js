/**
 * ENG-081: Content Pipeline - filtering, sorting, stage tabs, pagination, and the per-page select
 * are all still real server-side GET requests against the existing /app/pipeline route (same
 * LandingMvcController#pipeline method, same PipelineDashboardService query/filter/sort/pagination
 * logic, same authorization) - this script only changes HOW the browser gets there: instead of a
 * full navigation, it intercepts the form submit / link clicks / select change, fetches the same
 * URL with an X-Requested-With: fetch header (LandingMvcController#pipeline responds with just the
 * dynamic-region HTML in that case - see fragments/pipeline-content.jspf), and swaps that HTML into
 * #pipelineDynamicRegion. No business/filter/sort logic is duplicated here - every URL fetched is
 * either a plain <a href> already fully built server-side, or the filter <form>'s own fields.
 *
 * Progressive enhancement: every link/form/select this script intercepts is a completely normal,
 * fully-functional GET link or form field - with this script absent/failed, the browser just
 * navigates/submits normally (the exact pre-AJAX behavior). preventDefault() only ever happens
 * inside a listener that goes on to do the equivalent fetch, and any fetch failure falls back to
 * a real navigation rather than leaving the user stuck.
 */
(function () {
    var region = document.getElementById('pipelineDynamicRegion');
    if (!region) {
        return;
    }

    // --- Sticky column offset (SKU sits right after the sticky Content ID column) --------------
    // Content ID's column width is left auto/content-driven (not fixed), so SKU's sticky `left`
    // can't be a hardcoded pixel value without either leaving a gap or overlapping Content ID -
    // it's computed from Content ID's actual rendered width instead, via this custom property
    // (see .pipeline-col-sku in app.css). Recomputed on resize AND after every AJAX content swap
    // (the table - and Content ID's rendered width with it - is a brand new element each time).
    function syncStickyColumnOffset() {
        var table = document.getElementById('pipelineTable');
        var idHeader = table && table.querySelector('thead th.pipeline-col-id');
        if (table && idHeader) {
            table.style.setProperty('--pipeline-sku-left', idHeader.getBoundingClientRect().width + 'px');
        }
    }
    window.addEventListener('resize', syncStickyColumnOffset);

    // --- Platforms column popovers ------------------------------------------------------------
    // ENG-076: one popover per active (at-least-one-channel-published) Platform chip, listing each
    // Channel's real publication status. Rendered as a <body>-level fixed-position overlay (moved
    // there by portalizePlatformPopovers() below) instead of positioned relative to its own table
    // cell - the table's own .pipeline-scroll (overflow-x:auto) container, and any stacking context
    // a sticky cell creates, would otherwise clip the popover or bury it behind later rows.
    // ENG-081: portalizePlatformPopovers()/removeAllPopovers() now also run around every AJAX
    // content swap, not just once at page load - #pipelineDynamicRegion's innerHTML is replaced on
    // every filter/sort/pagination interaction, which would otherwise (a) leave the PREVIOUS
    // render's already-portalled popovers orphaned as stale duplicates directly under <body>, since
    // they're no longer inside the swapped region by the time it's replaced, and (b) leave the NEW
    // render's popovers un-portalled (still sitting inline in the fresh table markup).
    function removeAllPopovers() {
        document.querySelectorAll('.pipeline-platform-popover').forEach(function (popover) {
            popover.remove();
        });
    }
    function portalizePlatformPopovers() {
        region.querySelectorAll('.pipeline-platform-popover').forEach(function (popover) {
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
            // Not enough room below - open upward instead.
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

    document.addEventListener('click', function (event) {
        // A click on the popover's own content (e.g. an "Open" link) shouldn't close it first -
        // target="_blank" links still navigate normally regardless of this.
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

    // --- AJAX navigation (filters/search/sort/stage tabs/pagination/per-page) ------------------
    // ENG-081: one shared loader - fetches the same URL a normal click/submit would have navigated
    // to, with the header LandingMvcController#pipeline uses to return just the dynamic-region
    // partial, and swaps it into #pipelineDynamicRegion. A newer request aborts any still-in-flight
    // older one (both prevents a slow, superseded response from clobbering a newer one, and means a
    // user clicking several filters/pages in a row never piles up duplicate in-flight requests).
    var currentRequest = null;

    // ENG-082: remember the Pipeline's current full URL (filters/search/page/size/sort/stage) in
    // sessionStorage on every render, so Content Detail's "Back to Pipeline" link (content-detail.js)
    // can restore this exact state instead of resetting to page 1/default filters. Read at the
    // moment the user clicks Content ID to leave (a plain, non-intercepted navigation - see the
    // click handler below), so it's always the URL the row they clicked was actually visible on.
    function rememberPipelineUrl() {
        try {
            sessionStorage.setItem('kcpcPipelineUrl', window.location.href);
        } catch (e) {
            // Storage unavailable (private browsing, quota) - Back to Pipeline just falls back to
            // its default /app/pipeline href, no functional loss.
        }
    }

    function setLoading(isLoading) {
        var scroll = region.querySelector('.pipeline-scroll');
        if (scroll) {
            scroll.classList.toggle('pipeline-loading', isLoading);
        }
    }

    // Sort/filter/pagination/page-size updates used to do a flat region.innerHTML = html, which
    // destroys and recreates .pipeline-scroll (the overflow-x:auto container the horizontal
    // scrollbar actually lives on) as a brand new element every time - a fresh element always
    // starts at scrollLeft 0, which is why the table visibly jumped back to the far left on every
    // AJAX update. Fix: keep the SAME .pipeline-scroll DOM node across the swap (only its
    // thead+tbody content - which includes the sort-indicator icons - is replaced wholesale from
    // the fetched fragment) instead of replacing the wrapper itself; everything else in the
    // fragment (filter bar/hidden inputs, per-page select, pagination controls, footer note) is
    // still fully re-rendered from the server response exactly as before.
    function applyPipelineHtml(html) {
        var temp = document.createElement('div');
        temp.innerHTML = html;

        var oldScroll = region.querySelector('.pipeline-scroll');
        var newScroll = temp.querySelector('.pipeline-scroll');
        if (oldScroll && newScroll) {
            oldScroll.innerHTML = newScroll.innerHTML;
            newScroll.replaceWith(oldScroll);
        }

        region.innerHTML = '';
        while (temp.firstChild) {
            region.appendChild(temp.firstChild);
        }
    }

    // Belt-and-suspenders on top of applyPipelineHtml's node-preserving swap above: explicitly
    // capture the scroll container's scrollLeft right before the fetch, then re-apply it once
    // after the swap and again on the next animation frame (some browsers can still reset scroll
    // offsets on an element that's briefly reparented, e.g. during the .pipeline-scroll relocation
    // above), so the restore is guaranteed even if the node-preservation trick alone weren't enough.
    function captureScrollLeft() {
        var scroll = region.querySelector('.pipeline-scroll');
        return scroll ? scroll.scrollLeft : 0;
    }

    function restoreScrollLeft(value) {
        var scroll = region.querySelector('.pipeline-scroll');
        if (!scroll) {
            return;
        }
        scroll.scrollLeft = value;
        requestAnimationFrame(function () {
            scroll.scrollLeft = value;
        });
    }

    function loadPipeline(url, pushHistory) {
        if (currentRequest) {
            currentRequest.abort();
        }
        var controller = new AbortController();
        currentRequest = controller;
        setLoading(true);
        var savedScrollLeft = captureScrollLeft();

        fetch(url, {headers: {'X-Requested-With': 'fetch'}, signal: controller.signal})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Pipeline request failed: ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                if (controller.signal.aborted) {
                    return;
                }
                closeOpenPlatformPopover();
                removeAllPopovers();
                applyPipelineHtml(html);
                setLoading(false);
                portalizePlatformPopovers();
                syncStickyColumnOffset();
                restoreScrollLeft(savedScrollLeft);
                if (pushHistory) {
                    history.pushState(null, '', url);
                }
                rememberPipelineUrl();
                currentRequest = null;
            })
            .catch(function (err) {
                if (err && err.name === 'AbortError') {
                    return;
                }
                setLoading(false);
                currentRequest = null;
                // Genuine fetch failure (offline, 5xx, etc.) - fall back to a real navigation
                // instead of leaving the user stuck looking at stale/dimmed content.
                window.location.href = url;
            });
    }

    // Filter form: Search, Priority, Platform, Date Range, Delayed only, and Apply Filters all
    // submit together as this one form - a single submit listener covers all of them.
    region.addEventListener('submit', function (event) {
        var form = event.target.closest('#pipelineFilterForm');
        if (!form) {
            return;
        }
        event.preventDefault();
        var params = new URLSearchParams(new FormData(form)).toString();
        loadPipeline(form.action + '?' + params, true);
    });

    // Records-per-page: the <select>'s data-base-url is the exact same server-built query string
    // every other link on this page reuses - this only ever appends the chosen value to it.
    region.addEventListener('change', function (event) {
        if (event.target.id !== 'pipelinePerPage') {
            return;
        }
        var select = event.target;
        loadPipeline(select.dataset.baseUrl + select.value, true);
    });

    // Platform chips (popover open/close) and every other in-page navigation link (stage tabs,
    // Content ID/date-column sort links, pagination prev/next/page-number, Clear) share one
    // delegated click listener. Everything else in the table - the Content ID link itself, the
    // Drive Link icon, the Reference Link, the Performance link, a platform popover's "Open ↗"
    // evidence link - intentionally does NOT match any of these selectors, so it keeps navigating
    // normally (the "navigation exception": these open Content Detail or an external URL, not an
    // in-place pipeline table update).
    region.addEventListener('click', function (event) {
        var chipTrigger = event.target.closest('.pipeline-platform-chip[data-popup-target]');
        if (chipTrigger) {
            event.stopPropagation();
            togglePlatformPopover(chipTrigger);
            return;
        }
        var navLink = event.target.closest(
            '.pipeline-stage-tab, .pipeline-sort-link, .pagination-controls a, .pipeline-filter-bar .btn-outline'
        );
        if (navLink && navLink.href) {
            event.preventDefault();
            loadPipeline(navLink.href, true);
        }
    });

    // Back/Forward: re-fetch and re-render for whatever URL the browser just navigated to, without
    // pushing a new history entry (the browser already moved the history pointer itself).
    window.addEventListener('popstate', function () {
        loadPipeline(window.location.href, false);
    });

    // Initial render (full page load, not an AJAX swap) still needs the same one-time setup.
    syncStickyColumnOffset();
    portalizePlatformPopovers();
    rememberPipelineUrl();
})();
