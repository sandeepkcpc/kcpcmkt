/**
 * ENG-087: Team Workload dashboard - AJAX partial-fragment-swap filtering, mirroring
 * pipeline-dashboard.js's own loadPipeline() pattern verbatim (X-Requested-With: fetch,
 * #teamWorkloadDynamicRegion innerHTML swap, history.pushState, AbortController for in-flight
 * requests, real-navigation fallback on genuine fetch failure). No business/aggregation logic
 * lives here - every count comes from the server-rendered response as-is; this file only decides
 * WHEN to re-fetch (form submit, a filter field changing, Clear, Refresh) and swaps the HTML in.
 * Plain <a>/<form method="get"> still works with JS disabled (progressive enhancement).
 */
(function () {
    var region = document.getElementById('teamWorkloadDynamicRegion');
    if (!region) {
        return;
    }

    var currentRequest = null;

    // Active Tasks by Stage's own numbers never depend on the Stage filter any more (that filter
    // now scopes Assignee Load only) - so when Stage is the ONLY query param that changed since
    // the last render, only the Assignee Load card's markup is swapped in, leaving the Stage card
    // (and the rest of the page - filter form, "Data as of" header) untouched instead of
    // redrawing something whose data provably didn't change.
    function onlyStageParamChanged(oldSearch, newSearch) {
        var oldParams = new URLSearchParams(oldSearch);
        var newParams = new URLSearchParams(newSearch);
        var oldKeys = Array.from(oldParams.keys()).filter(function (k) { return k !== 'stage'; });
        var newKeys = Array.from(newParams.keys()).filter(function (k) { return k !== 'stage'; });
        if (oldKeys.length !== newKeys.length) {
            return false;
        }
        for (var i = 0; i < newKeys.length; i++) {
            var key = newKeys[i];
            if (oldParams.get(key) !== newParams.get(key)) {
                return false;
            }
        }
        return oldParams.get('stage') !== newParams.get('stage');
    }

    function applyWorkloadHtml(html, assigneeCardOnly) {
        if (assigneeCardOnly) {
            var currentAssigneeCard = region.querySelector('.team-workload-card-assignee');
            if (currentAssigneeCard) {
                var temp = document.createElement('div');
                temp.innerHTML = html;
                var newAssigneeCard = temp.querySelector('.team-workload-card-assignee');
                if (newAssigneeCard) {
                    currentAssigneeCard.replaceWith(newAssigneeCard);
                    syncTeamWorkloadCardHeights();
                    return;
                }
            }
        }
        region.innerHTML = html;
        syncTeamWorkloadCardHeights();
    }

    function loadWorkload(url, pushHistory) {
        if (currentRequest) {
            currentRequest.abort();
        }
        var controller = new AbortController();
        currentRequest = controller;
        region.classList.add('team-workload-loading');
        var oldSearch = window.location.search;
        var newSearch = url.indexOf('?') === -1 ? '' : url.slice(url.indexOf('?'));
        var assigneeCardOnly = onlyStageParamChanged(oldSearch, newSearch);

        fetch(url, {headers: {'X-Requested-With': 'fetch'}, signal: controller.signal})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Team Workload request failed: ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                if (controller.signal.aborted) {
                    return;
                }
                applyWorkloadHtml(html, assigneeCardOnly);
                region.classList.remove('team-workload-loading');
                if (pushHistory) {
                    history.pushState(null, '', url);
                }
                currentRequest = null;
            })
            .catch(function (err) {
                if (err && err.name === 'AbortError') {
                    return;
                }
                region.classList.remove('team-workload-loading');
                currentRequest = null;
                window.location.href = url;
            });
    }

    // Active Tasks by Stage is always exactly 6 fixed rows (5 stages + Total) - its rendered
    // height is the one both cards must match. CSS alone can't stretch Assignee Load to that
    // height and then clip its overflow (a Grid/Flex row's height is set by its tallest content,
    // which would just be Assignee Load itself) - so this measures the Stage card and applies it
    // as an explicit height on the Assignee card, whose own .team-workload-table-scroll (flex:1,
    // overflow-y:auto) turns any overflow into an internal scrollbar instead of growing the
    // card/page. A ResizeObserver on the Stage card - rather than calling this once per known
    // mutation point (initial load, each AJAX swap, window resize) - means it re-applies
    // automatically no matter what changes the Stage card's size (including causes those specific
    // call sites can't anticipate, e.g. web font swap-in reflowing row heights after first paint),
    // so this can never silently go stale the way a call-site-based approach can.
    var stageResizeObserver = null;

    function syncTeamWorkloadCardHeights() {
        var stageCard = region.querySelector('.team-workload-card-stage');
        var assigneeCard = region.querySelector('.team-workload-card-assignee');
        if (stageResizeObserver) {
            stageResizeObserver.disconnect();
            stageResizeObserver = null;
        }
        if (!stageCard || !assigneeCard) {
            return;
        }

        function apply() {
            assigneeCard.style.height = '';
            if (window.matchMedia('(max-width: 1100px)').matches) {
                return; // stacked single-column layout below this width - no sync needed
            }
            assigneeCard.style.height = stageCard.offsetHeight + 'px';
        }

        apply();
        if (window.ResizeObserver) {
            stageResizeObserver = new ResizeObserver(apply);
            stageResizeObserver.observe(stageCard);
        }
    }

    window.addEventListener('resize', function () {
        clearTimeout(window.__teamWorkloadResizeTimer);
        window.__teamWorkloadResizeTimer = setTimeout(syncTeamWorkloadCardHeights, 150);
    });
    syncTeamWorkloadCardHeights();

    // Date Range fix (spec §15): From > To must be validated with a clear message and must never
    // silently submit/produce an empty result. Kept in this already-Workload-dedicated file
    // (never the shared reports-workspace.js, which this screen doesn't even use) so the fix can
    // never affect any other report screen - same reasoning reports-admin-actions.js was kept
    // separate from reports-workspace.js for the same class of fix. From = To is explicitly valid
    // (a single calendar day); either side may be blank (open-ended range, backend-supported).
    function isDateRangeValid() {
        var fromField = document.getElementById('twDateFrom');
        var toField = document.getElementById('twDateTo');
        var errorBox = document.getElementById('twDateRangeError');
        if (!fromField || !toField) {
            return true;
        }
        if (fromField.value && toField.value && fromField.value > toField.value) {
            if (errorBox) {
                errorBox.textContent = 'From date cannot be after To date.';
                errorBox.classList.remove('hidden');
            }
            return false;
        }
        if (errorBox) {
            errorBox.textContent = '';
            errorBox.classList.add('hidden');
        }
        return true;
    }

    region.addEventListener('input', function (event) {
        if (event.target.id === 'twDateFrom' || event.target.id === 'twDateTo') {
            isDateRangeValid(); // re-validates and clears/refreshes the message as the user types
        }
    });

    // Filter form: Business Role/Employee/Stage/Date Range/Delayed Only all submit together.
    region.addEventListener('submit', function (event) {
        var form = event.target.closest('#teamWorkloadFilterForm');
        if (!form) {
            return;
        }
        event.preventDefault();
        if (!isDateRangeValid()) {
            return;
        }
        var params = new URLSearchParams(new FormData(form)).toString();
        loadWorkload(form.action + (params ? '?' + params : ''), true);
    });

    // Business Role/Employee/Stage selects and the Delayed Only checkbox re-submit immediately on
    // change (no separate "Apply" click needed) - this is also how the Employee dropdown's options
    // end up scoped to the newly-chosen Business Role, since the server rebuilds that list itself.
    region.addEventListener('change', function (event) {
        var target = event.target;
        if (target.id === 'twBusinessRole' || target.id === 'twEmployee' || target.id === 'twStage'
                || target.name === 'delayedOnly' || target.id === 'twDateFrom' || target.id === 'twDateTo') {
            if (!isDateRangeValid()) {
                return; // never auto-submit an inverted range - same gate the explicit Filter/submit path uses
            }
            var form = document.getElementById('teamWorkloadFilterForm');
            if (form) {
                // Changing Business Role starts a fresh Employee search - stale selection would
                // otherwise silently resubmit a now-irrelevant employeeId.
                if (target.id === 'twBusinessRole') {
                    var employeeSelect = document.getElementById('twEmployee');
                    if (employeeSelect) {
                        employeeSelect.value = '';
                    }
                }
                var params = new URLSearchParams(new FormData(form)).toString();
                loadWorkload(form.action + (params ? '?' + params : ''), true);
            }
        }
    });

    // Clear link and Refresh button - both just re-fetch (Clear's href already has no query
    // string; Refresh re-fetches the current URL). Employee-wise UI update: "Open" toggles that
    // one employee's stage-wise breakdown panel (already server-rendered, hidden by default - see
    // team-workload-content.jspf) and closes any other panel that was open, so at most one
    // breakdown is visible at a time, matching the reference design.
    region.addEventListener('click', function (event) {
        var clearLink = event.target.closest('.team-workload-clear');
        if (clearLink) {
            event.preventDefault();
            loadWorkload(clearLink.href, true);
            return;
        }
        var refreshBtn = event.target.closest('#teamWorkloadRefresh');
        if (refreshBtn) {
            event.preventDefault();
            loadWorkload(window.location.href, false);
            return;
        }
        var openBtn = event.target.closest('.team-workload-open-btn');
        if (openBtn) {
            var targetId = openBtn.getAttribute('data-employee-id');
            var targetPanel = document.getElementById('workloadBreakdown-' + targetId);
            var alreadyOpen = targetPanel && !targetPanel.classList.contains('hidden');
            region.querySelectorAll('.team-workload-breakdown').forEach(function (panel) {
                panel.classList.add('hidden');
            });
            region.querySelectorAll('.team-workload-open-btn').forEach(function (btn) {
                btn.setAttribute('aria-expanded', 'false');
            });
            if (targetPanel && !alreadyOpen) {
                targetPanel.classList.remove('hidden');
                openBtn.setAttribute('aria-expanded', 'true');
                targetPanel.scrollIntoView({behavior: 'smooth', block: 'nearest'});
            }
        }
    });

    window.addEventListener('popstate', function () {
        loadWorkload(window.location.href, false);
    });
})();
