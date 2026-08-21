/**
 * ENG-088: Idea Queue dashboard - AJAX partial-fragment-swap filtering/sorting/pagination,
 * mirroring pipeline-dashboard.js's/team-workload-dashboard.js's own loadX() pattern verbatim
 * (X-Requested-With: fetch, #ideaQueueDynamicRegion innerHTML swap, history.pushState,
 * AbortController for in-flight requests, real-navigation fallback on genuine fetch failure). No
 * business/status logic lives here - every row/status/action comes from the server-rendered
 * response as-is; this file only decides WHEN to re-fetch and swaps the HTML in. Plain
 * <a>/<form method="get"> still works with JS disabled (progressive enhancement).
 */
(function () {
    var region = document.getElementById('ideaQueueDynamicRegion');
    if (!region) {
        return;
    }

    var currentRequest = null;

    // Remembers the Idea Queue's current full URL (filters/search/page/size/sort) in
    // sessionStorage on every render, so Idea Detail's "Back to Idea Queue" link (idea-detail.js)
    // can restore this exact state instead of resetting to page 1/default filters - same
    // convention pipeline-dashboard.js already established for "Back to Pipeline".
    function rememberIdeaQueueUrl() {
        try {
            sessionStorage.setItem('kcpcIdeaQueueUrl', window.location.href);
        } catch (e) {
            // Storage unavailable (private browsing, quota) - Back to Idea Queue just falls back to
            // its default /app/ideas href, no functional loss.
        }
    }

    function loadQueue(url, pushHistory) {
        if (currentRequest) {
            currentRequest.abort();
        }
        var controller = new AbortController();
        currentRequest = controller;
        region.classList.add('idea-queue-loading');

        fetch(url, {headers: {'X-Requested-With': 'fetch'}, signal: controller.signal})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Idea Queue request failed: ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                if (controller.signal.aborted) {
                    return;
                }
                region.innerHTML = html;
                region.classList.remove('idea-queue-loading');
                if (pushHistory) {
                    history.pushState(null, '', url);
                }
                rememberIdeaQueueUrl();
                currentRequest = null;
            })
            .catch(function (err) {
                if (err && err.name === 'AbortError') {
                    return;
                }
                region.classList.remove('idea-queue-loading');
                currentRequest = null;
                window.location.href = url;
            });
    }

    // Filter form: Search/Status/Submitted By/Date Range/page size (associated via form="..." on
    // the per-page <select>, which lives outside the <form> tag but is still collected by
    // FormData) all submit together - a fresh submit always resets to page 1 (no hidden page
    // field in the form), matching "filters/search should reset to page 1."
    region.addEventListener('submit', function (event) {
        var form = event.target.closest('#ideaQueueFilterForm');
        if (!form) {
            return;
        }
        event.preventDefault();
        var params = new URLSearchParams(new FormData(form)).toString();
        loadQueue(form.action + (params ? '?' + params : ''), true);
    });

    // Status/Submitted By/Date Range/page-size selects and date inputs re-submit immediately on
    // change - no separate "Apply" click needed.
    region.addEventListener('change', function (event) {
        var target = event.target;
        if (target.id === 'iqStatus' || target.id === 'iqSubmittedBy' || target.id === 'iqDateFrom'
                || target.id === 'iqDateTo' || target.id === 'iqPageSize') {
            var form = document.getElementById('ideaQueueFilterForm');
            if (form) {
                var params = new URLSearchParams(new FormData(form)).toString();
                loadQueue(form.action + (params ? '?' + params : ''), true);
            }
        }
    });

    // Search input: debounced re-fetch as the user types, so it behaves like a live filter rather
    // than requiring Enter - still just re-submits the same form, no separate search endpoint.
    var searchDebounce = null;
    region.addEventListener('input', function (event) {
        if (event.target.id !== 'iqSearch') {
            return;
        }
        if (searchDebounce) {
            clearTimeout(searchDebounce);
        }
        searchDebounce = setTimeout(function () {
            var form = document.getElementById('ideaQueueFilterForm');
            if (form) {
                var params = new URLSearchParams(new FormData(form)).toString();
                loadQueue(form.action + (params ? '?' + params : ''), true);
            }
        }, 350);
    });

    // Clear Filters, Idea ID sort toggle, and pagination prev/page-number/next links all just
    // navigate to a fully server-built URL - intercept and AJAX-load instead of a full reload.
    region.addEventListener('click', function (event) {
        var link = event.target.closest('.idea-queue-clear, .idea-queue-sort-link, .idea-queue-pagination a');
        if (!link) {
            return;
        }
        event.preventDefault();
        loadQueue(link.href, true);
    });

    window.addEventListener('popstate', function () {
        loadQueue(window.location.href, false);
    });

    // Initial render (full page load, not an AJAX swap) still needs its URL remembered.
    rememberIdeaQueueUrl();
})();
