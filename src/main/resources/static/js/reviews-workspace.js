/**
 * Manager Reviews Workspace - AJAX partial-fragment-swap tab switching/queue selection/pagination/
 * search/filter, mirroring pipeline-dashboard.js's/idea-queue-dashboard.js's own loadX() pattern
 * verbatim (X-Requested-With: fetch, #reviewsDynamicRegion innerHTML swap, history.pushState,
 * AbortController for in-flight requests, real-navigation fallback on genuine fetch failure). No
 * business/validation logic lives here - the Reason-mandatory-for-Reject/Rework rule etc. is never
 * duplicated in JS; a decision POST either succeeds or comes back with the backend's own
 * ApiErrorResponse.message, which this file just displays as-is.
 *
 * Loading state is toggled on #reviewsDynamicRegion only (never the whole page) and is cleared in
 * BOTH the fetch success path and the catch path - this session already hit and fixed the opposite
 * mistake once, in pipeline-dashboard.js (setLoading(false) missing from the success path left the
 * table permanently dimmed), so this file's loadReviews()/submitDecision() are written against that
 * exact failure mode from the start.
 */
(function () {
    var region = document.getElementById('reviewsDynamicRegion');
    if (!region) {
        return;
    }

    var currentRequest = null;

    function setLoading(isLoading) {
        region.classList.toggle('reviews-loading', isLoading);
    }

    function loadReviews(url, pushHistory) {
        if (currentRequest) {
            currentRequest.abort();
        }
        var controller = new AbortController();
        currentRequest = controller;
        setLoading(true);

        fetch(url, {headers: {'X-Requested-With': 'fetch'}, signal: controller.signal})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Reviews request failed: ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                if (controller.signal.aborted) {
                    return;
                }
                region.innerHTML = html;
                setLoading(false);
                if (pushHistory) {
                    history.pushState(null, '', url);
                }
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

    function currentFilterForm() {
        return document.getElementById('reviewsFilterForm');
    }

    function reloadFromForm(pushHistory) {
        var form = currentFilterForm();
        if (!form) {
            return;
        }
        var params = new URLSearchParams(new FormData(form)).toString();
        loadReviews(form.action + (params ? '?' + params : ''), pushHistory);
    }

    // --- Tab switching / row selection / pagination / clear - all plain <a href> links, real GET
    // URLs, intercepted here for the AJAX swap; a click that reaches window.location (JS failed/
    // disabled) still works exactly as a normal link. ------------------------------------------
    region.addEventListener('click', function (event) {
        var link = event.target.closest(
            '.reviews-tab, .reviews-row-link, .pagination-controls a, .reviews-clear, .reviews-sort-link'
        );
        if (link && link.href) {
            event.preventDefault();
            loadReviews(link.href, true);
            return;
        }
        // Clicking anywhere else in a queue row (not the link itself, already handled above)
        // still selects that row, using the same server-built URL the link carries.
        var row = event.target.closest('.reviews-row');
        if (row && row.dataset.rowUrl) {
            loadReviews(row.dataset.rowUrl, true);
        }
    });

    // Filter form submit (Enter in the search box).
    region.addEventListener('submit', function (event) {
        var form = event.target.closest('#reviewsFilterForm');
        if (!form) {
            return;
        }
        event.preventDefault();
        reloadFromForm(true);
    });

    // Mode/Priority/page-size selects and the Delayed-only checkbox re-submit immediately on change.
    region.addEventListener('change', function (event) {
        var id = event.target.id;
        var isFilterControl = /^rv(Idea|Plan|Shoot|Edit)(PageSize)$/.test(id) || event.target.name === 'mode'
            || event.target.name === 'priority' || event.target.name === 'delayedOnly';
        if (isFilterControl) {
            reloadFromForm(true);
        }
    });

    // Search input: debounced re-fetch as the user types.
    var searchDebounce = null;
    region.addEventListener('input', function (event) {
        if (!/^rv(Idea|Plan|Shoot|Edit)Search$/.test(event.target.id)) {
            return;
        }
        if (searchDebounce) {
            clearTimeout(searchDebounce);
        }
        searchDebounce = setTimeout(function () {
            reloadFromForm(true);
        }, 350);
    });

    window.addEventListener('popstate', function () {
        loadReviews(window.location.href, false);
    });

    // --- Character counters (Reason / Decision Reason / Reviewer Comments textareas) -----------
    region.addEventListener('input', function (event) {
        var field = event.target;
        if (field.tagName !== 'TEXTAREA') {
            return;
        }
        var counter = region.querySelector('.char-counter[data-counter-for="' + field.id + '"]');
        if (!counter) {
            return;
        }
        var limit = 500;
        var length = field.value.length;
        counter.textContent = length + ' / ' + limit;
        counter.classList.toggle('char-counter-at-limit', length >= limit);
        counter.classList.toggle('char-counter-near-limit', length >= limit * 0.9 && length < limit);
    });

    // --- Decision submit -------------------------------------------------------------------
    var decisionEndpoints = {
        idea: function (id) { return contextPath() + '/app/reviews/ideas/' + id + '/decision'; },
        planning: function (id) { return contextPath() + '/app/reviews/planning/' + id + '/decision'; },
        shoot: function (id) { return contextPath() + '/app/reviews/shoot/' + id + '/decision'; },
        edit: function (id) { return contextPath() + '/app/reviews/edit/' + id + '/decision'; }
    };

    function contextPath() {
        // Same-origin, path-relative endpoints - derive the app's context path from this script's
        // own src attribute (".../<ctx>/js/reviews-workspace.js") rather than hardcoding it.
        // document.currentScript is only valid during a script's OWN initial synchronous execution
        // (not later, inside an event handler like the decision-submit click this is called from),
        // so this always looks the <script> tag up by src instead.
        var script = document.querySelector('script[src*="reviews-workspace.js"]');
        if (!script) {
            return '';
        }
        var src = script.getAttribute('src');
        var idx = src.indexOf('/js/reviews-workspace.js');
        return idx > 0 ? src.slice(0, idx) : '';
    }

    function showDecisionError(message) {
        var box = document.getElementById('reviewsDecisionError');
        if (!box) {
            return;
        }
        box.textContent = message;
        box.classList.remove('hidden');
    }

    function clearDecisionError() {
        var box = document.getElementById('reviewsDecisionError');
        if (box) {
            box.classList.add('hidden');
            box.textContent = '';
        }
    }

    function setDecisionButtonsDisabled(disabled) {
        region.querySelectorAll('.reviews-decision-btn').forEach(function (btn) {
            btn.disabled = disabled;
        });
    }

    function submitDecision(card, params) {
        var type = card.dataset.reviewType;
        var id = card.dataset.reviewId;
        var endpoint = decisionEndpoints[type](id);

        var csrfInput = document.getElementById('reviewsCsrfToken');
        if (csrfInput) {
            params.set(csrfInput.name, csrfInput.value);
        }

        clearDecisionError();
        setDecisionButtonsDisabled(true);

        fetch(endpoint, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params.toString()
        })
            .then(function (response) {
                if (response.ok) {
                    return {ok: true};
                }
                return response.json().then(function (body) {
                    return {ok: false, message: body.message || 'The decision could not be recorded.'};
                }).catch(function () {
                    return {ok: false, message: 'The decision could not be recorded.'};
                });
            })
            .then(function (result) {
                if (result.ok) {
                    // Re-fetch the same tab/filter/page state - the decided item naturally drops
                    // out of the pending queue server-side, which also auto-selects whatever
                    // pending item is now first (see ReviewsMvcController's selection fallback).
                    loadReviews(window.location.href, false);
                } else {
                    setDecisionButtonsDisabled(false);
                    showDecisionError(result.message);
                }
            })
            .catch(function () {
                setDecisionButtonsDisabled(false);
                showDecisionError('Network error - the decision was not submitted. Please try again.');
            });
    }

    region.addEventListener('click', function (event) {
        var btn = event.target.closest('.reviews-decision-btn');
        if (!btn || btn.disabled) {
            return;
        }
        var card = btn.closest('.reviews-detail-card');
        if (!card) {
            return;
        }
        var type = card.dataset.reviewType;
        var params = new URLSearchParams();

        if (type === 'idea') {
            var decision = btn.dataset.decision;
            params.set('decision', decision);
            var reasonField = document.getElementById('reviewsIdeaReason');
            if (reasonField && reasonField.value.trim()) {
                params.set('reason', reasonField.value.trim());
            }
            var camMark = document.getElementById('reviewsIdeaCameramanMark');
            var edMark = document.getElementById('reviewsIdeaEditorMark');
            if (camMark) {
                params.set('cameramanMark', camMark.value);
            }
            if (edMark) {
                params.set('editorMark', edMark.value);
            }
        } else {
            params.set('approve', btn.dataset.approve);
            var planReasonField = document.getElementById('reviewsPlanReason');
            if (planReasonField && planReasonField.value.trim()) {
                params.set('reason', planReasonField.value.trim());
            }
            region.querySelectorAll('.reviews-qualifying-checkbox:checked').forEach(function (cb) {
                params.append('qualifyingRecipientUserIds', cb.value);
            });
        }

        submitDecision(card, params);
    });
})();
