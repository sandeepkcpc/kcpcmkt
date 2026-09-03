/**
 * Reports -> KPI Dashboard -> Overview -> Current Work Ownership -> "Open" drill-down. A read-only
 * reporting drawer (rendered as the shared .kcpc-modal-* shell) fetching one employee's current
 * work on demand (ReportingMvcController#ownershipDrilldown) - never pre-loaded for every employee
 * on the Overview page load. Uses document-level event delegation throughout (not a
 * reports-workspace.js-style "re-wire after AJAX swap" hook) so it keeps working correctly no
 * matter how many times #reportsKpiDynamicRegion's content is replaced when switching KPI
 * views/date ranges - the modal markup itself lives inside reports-kpi-overview.jspf and is only
 * ever present in the DOM while Overview is the active view, which is also the only place the
 * "Open" trigger buttons exist.
 */
(function () {
    function contextPath() {
        var script = document.querySelector('script[src*="reports-kpi-ownership-drilldown.js"]');
        if (!script) {
            return '';
        }
        var src = script.getAttribute('src');
        var idx = src.indexOf('/js/reports-kpi-ownership-drilldown.js');
        return idx > 0 ? src.slice(0, idx) : '';
    }

    function openDrilldown(employeeId) {
        var overlay = document.getElementById('kpiOwnershipDrilldownOverlay');
        var body = document.getElementById('kpiOwnershipDrilldownBody');
        if (!overlay || !body || !employeeId) {
            return;
        }
        body.innerHTML = '<p class="muted">Loading&hellip;</p>';
        overlay.classList.remove('hidden');
        fetch(contextPath() + '/app/reports/kpis/ownership-drilldown?employeeId=' + encodeURIComponent(employeeId),
            {headers: {'X-Requested-With': 'fetch'}})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Drill-down request failed: ' + response.status);
                }
                return response.text();
            })
            .then(function (html) {
                body.innerHTML = html;
            })
            .catch(function () {
                body.innerHTML = '<p class="muted">Could not load this employee’s work right now. Please try again.</p>';
            });
    }

    function closeDrilldown() {
        var overlay = document.getElementById('kpiOwnershipDrilldownOverlay');
        if (overlay) {
            overlay.classList.add('hidden');
        }
    }

    document.addEventListener('click', function (event) {
        var openBtn = event.target.closest('.kpi-ownership-open');
        if (openBtn) {
            openDrilldown(openBtn.getAttribute('data-employee-id'));
            return;
        }
        if (event.target.closest('#kpiOwnershipDrilldownClose')) {
            closeDrilldown();
            return;
        }
        if (event.target.id === 'kpiOwnershipDrilldownOverlay') {
            closeDrilldown(); // clicking the backdrop itself closes it
            return;
        }
        var tabBtn = event.target.closest('.kpi-drilldown-tab');
        if (tabBtn) {
            var targetPanel = tabBtn.getAttribute('data-drilldown-tab');
            document.querySelectorAll('.kpi-drilldown-tab').forEach(function (b) {
                b.classList.toggle('active', b === tabBtn);
            });
            document.querySelectorAll('.kpi-drilldown-panel').forEach(function (p) {
                p.classList.toggle('hidden', p.getAttribute('data-drilldown-panel') !== targetPanel);
            });
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeDrilldown();
        }
    });
})();
