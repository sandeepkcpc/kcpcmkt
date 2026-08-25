/**
 * CEO/MM Reports Workspace - AJAX partial-fragment-swap filtering/pagination for the 4 tabs that
 * have one (KPI Dashboard/Delayed Deliverables/Administrative Actions/Audit History - Export is a
 * plain synchronous GET download, no AJAX needed), mirroring team-workload-dashboard.js's own
 * loadX() pattern verbatim (X-Requested-With: fetch, AbortController, history.pushState,
 * real-navigation fallback on genuine fetch failure). Only one of the 4 region ids exists on any
 * given page load; setupReportsRegion() is a no-op if its region isn't present. No business/report
 * logic lives here - every row/value/count comes from the server-rendered response as-is.
 *
 * Loading state is toggled on the region only (never the whole page) and cleared in BOTH the fetch
 * success path and the catch path - this session already hit and fixed the opposite mistake once,
 * in pipeline-dashboard.js (setLoading(false) missing from the success path left the table
 * permanently dimmed), so every loadX() below is written and reviewed against that exact failure
 * mode from the start.
 */
(function () {
    function setupReportsRegion(regionId, formId) {
        var region = document.getElementById(regionId);
        if (!region) {
            return;
        }

        var currentRequest = null;

        function setLoading(isLoading) {
            region.classList.toggle('reports-loading', isLoading);
        }

        function load(url, pushHistory) {
            if (currentRequest) {
                currentRequest.abort();
            }
            var controller = new AbortController();
            currentRequest = controller;
            setLoading(true);

            fetch(url, {headers: {'X-Requested-With': 'fetch'}, signal: controller.signal})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('Reports request failed: ' + response.status);
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
                    window.location.href = url;
                });
        }

        function reloadFromForm() {
            var form = document.getElementById(formId);
            if (!form) {
                return;
            }
            var params = new URLSearchParams(new FormData(form)).toString();
            load(form.action + (params ? '?' + params : ''), true);
        }

        region.addEventListener('click', function (event) {
            // .kpi-attention-link/.kpi-view-link deliberately excluded - they navigate to a
            // DIFFERENT module (Reviews/Team Workload/Delayed Deliverables), which returns a full
            // page, not this region's AJAX partial shape; only .kpi-view-tab stays within this
            // same /app/reports/kpis region.
            var link = event.target.closest(
                '.reports-tab, .pagination-controls a, .reports-clear, .reviews-sort-link, .kpi-view-tab'
            );
            if (link && link.href) {
                event.preventDefault();
                load(link.href, true);
            }
        });

        region.addEventListener('submit', function (event) {
            var form = event.target.closest('#' + formId);
            if (!form) {
                return;
            }
            event.preventDefault();
            reloadFromForm();
        });

        // Selects/date inputs/page-size re-submit immediately on change - no separate Apply click
        // needed (Audit History keeps its own explicit "Filter" button per spec; its selects still
        // benefit from immediate page-size changes).
        region.addEventListener('change', function (event) {
            var el = event.target;
            var isPageSize = /PageSize$/.test(el.id);
            var isAutoSubmitSelect = el.tagName === 'SELECT' && el.closest('#' + formId) && formId !== 'auditFilterForm';
            if (isPageSize || isAutoSubmitSelect) {
                reloadFromForm();
            }
        });

        // Debounced search-as-you-type for the free-text search fields.
        var searchDebounce = null;
        region.addEventListener('input', function (event) {
            if (event.target.tagName !== 'INPUT' || event.target.type !== 'text') {
                return;
            }
            if (searchDebounce) {
                clearTimeout(searchDebounce);
            }
            searchDebounce = setTimeout(reloadFromForm, 350);
        });

        window.addEventListener('popstate', function () {
            load(window.location.href, false);
        });
    }

    setupReportsRegion('reportsKpiDynamicRegion', 'reportsKpiFilterForm');
    setupReportsRegion('reportsDelayedDynamicRegion', 'reportsDelayedFilterForm');
    setupReportsRegion('reportsAdminActionsDynamicRegion', 'reportsAdminActionsFilterForm');
    setupReportsRegion('auditHistoryDynamicRegion', 'auditFilterForm');

    // --- Export screen: Select All per group + selected-count/summary line -------------------
    // Purely cosmetic client-side state over real checkboxes that already carry the real table
    // names as their submitted values - no export/permission logic is duplicated here.
    var exportForm = document.getElementById('exportForm');
    if (exportForm) {
        var formatSelect = document.getElementById('exportFormat');
        var countLabel = document.getElementById('exportSelectedCount');
        var summaryLabel = document.getElementById('exportSummary');
        var scopeCheckboxes = Array.prototype.slice.call(exportForm.querySelectorAll('.export-scope-checkbox'));
        var selectAllCheckboxes = Array.prototype.slice.call(exportForm.querySelectorAll('.export-select-all-checkbox'));

        function scopeCheckboxesForGroup(group) {
            return scopeCheckboxes.filter(function (cb) { return cb.dataset.group === group; });
        }

        function updateSummary() {
            var checked = scopeCheckboxes.filter(function (cb) { return cb.checked; });
            countLabel.textContent = checked.length + (checked.length === 1 ? ' scope selected' : ' scopes selected');
            if (checked.length === 0) {
                summaryLabel.textContent = 'Select a format and at least one scope to export.';
            } else {
                summaryLabel.textContent = formatSelect.value + ' · ' + checked.length
                    + (checked.length === 1 ? ' scope selected' : ' scopes selected');
            }

            selectAllCheckboxes.forEach(function (allCb) {
                var groupBoxes = scopeCheckboxesForGroup(allCb.dataset.group);
                var allChecked = groupBoxes.length > 0 && groupBoxes.every(function (cb) { return cb.checked; });
                var anyChecked = groupBoxes.some(function (cb) { return cb.checked; });
                allCb.checked = allChecked;
                allCb.indeterminate = anyChecked && !allChecked;
            });
        }

        selectAllCheckboxes.forEach(function (allCb) {
            allCb.addEventListener('change', function () {
                scopeCheckboxesForGroup(allCb.dataset.group).forEach(function (cb) {
                    cb.checked = allCb.checked;
                });
                updateSummary();
            });
        });
        scopeCheckboxes.forEach(function (cb) {
            cb.addEventListener('change', updateSummary);
        });
        formatSelect.addEventListener('change', updateSummary);
        updateSummary();
    }
})();
