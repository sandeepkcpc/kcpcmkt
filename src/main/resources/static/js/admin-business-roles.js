// ENG-091: Business Roles - client-side search/status-filter/pagination over the already fully
// server-rendered role list (the backend has no server-side pagination/filtering for this screen,
// so this never fetches anything - it only shows/hides rows already present in the DOM). No form
// action, route, or validation is touched.
(function () {
    'use strict';

    var table = document.getElementById('rolesTable');
    if (!table) {
        return;
    }
    var allRows = Array.prototype.slice.call(table.querySelectorAll('tbody tr[data-role-row]'));
    var searchInput = document.getElementById('roleSearch');
    var segmentedButtons = Array.prototype.slice.call(document.querySelectorAll('#rolesToolbar .segmented-btn'));
    var summaryEl = document.getElementById('rolesPaginationSummary');
    var controlsEl = document.getElementById('rolesPaginationControls');
    var PAGE_SIZE = 12;
    var state = { query: '', status: 'all', page: 1 };

    function matches(row) {
        if (state.status !== 'all' && row.getAttribute('data-status') !== state.status) {
            return false;
        }
        if (state.query && row.getAttribute('data-name').indexOf(state.query) === -1) {
            return false;
        }
        return true;
    }

    function render() {
        var filtered = allRows.filter(matches);
        var totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
        state.page = Math.min(state.page, totalPages);
        var start = (state.page - 1) * PAGE_SIZE;
        var pageRows = filtered.slice(start, start + PAGE_SIZE);

        allRows.forEach(function (row) { row.classList.add('hidden'); });
        pageRows.forEach(function (row) { row.classList.remove('hidden'); });

        if (filtered.length === 0) {
            summaryEl.textContent = 'Showing 0 of 0 roles';
        } else {
            summaryEl.textContent = 'Showing ' + (start + 1) + ' to ' + Math.min(start + PAGE_SIZE, filtered.length)
                + ' of ' + filtered.length + ' roles';
        }

        controlsEl.innerHTML = '';
        var prev = document.createElement('a');
        prev.href = '#';
        prev.textContent = '‹';
        prev.className = state.page <= 1 ? 'page-disabled' : '';
        prev.addEventListener('click', function (e) {
            e.preventDefault();
            if (state.page > 1) { state.page -= 1; render(); }
        });
        controlsEl.appendChild(prev);

        for (var p = 1; p <= totalPages; p += 1) {
            (function (pageNum) {
                var el = document.createElement('a');
                el.href = '#';
                el.textContent = String(pageNum);
                el.className = pageNum === state.page ? 'page-current' : '';
                el.addEventListener('click', function (e) {
                    e.preventDefault();
                    state.page = pageNum;
                    render();
                });
                controlsEl.appendChild(el);
            })(p);
        }

        var next = document.createElement('a');
        next.href = '#';
        next.textContent = '›';
        next.className = state.page >= totalPages ? 'page-disabled' : '';
        next.addEventListener('click', function (e) {
            e.preventDefault();
            if (state.page < totalPages) { state.page += 1; render(); }
        });
        controlsEl.appendChild(next);
    }

    if (searchInput) {
        searchInput.addEventListener('input', function () {
            state.query = searchInput.value.trim().toLowerCase();
            state.page = 1;
            render();
        });
    }
    segmentedButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            segmentedButtons.forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            state.status = btn.getAttribute('data-filter');
            state.page = 1;
            render();
        });
    });

    render();
})();
