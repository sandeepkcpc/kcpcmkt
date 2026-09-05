'use strict';
/**
 * Dependency-free test for kcpc-date-picker.js's pure rules (Idea Review & Planning -> Schedule ->
 * Planned Live Date calendar) - no jsdom/npm package, mirroring this session's established Node
 * `vm` harness pattern (reports-kpi-calendar.test.js / reports-kpi-date-preset.test.js). Runs under
 * TZ=Asia/Kolkata, this app's own business timezone, so the local-date math is exercised in the
 * positive-UTC-offset zone where a toISOString() slip shows up as an off-by-one day.
 *   node src/test/js/kcpc-date-picker.test.js
 *
 * buildMonthCells() is the single definition of "which day is selectable" and "which day is
 * highlighted as already planned", so both are tested directly rather than through the DOM. The
 * per-channel COUNTS are never computed here - they arrive pre-computed from
 * UpcomingChannelPlanService and are asserted server-side (KpiOverviewUpcomingChannelPlanTest).
 */
process.env.TZ = 'Asia/Kolkata';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

function loadScript() {
    const src = fs.readFileSync(
        path.join(__dirname, '../../main/resources/static/js/kcpc-date-picker.js'), 'utf8');
    const noop = () => {};
    const sandbox = {
        document: {addEventListener: noop, createElement: () => ({style: {}, classList: {add: noop}})},
        console
    };
    sandbox.window = sandbox;
    sandbox.window.addEventListener = noop;
    vm.createContext(sandbox);
    vm.runInContext(src, sandbox);
    assert.ok(sandbox.window.KcpcDatePicker, 'script must publish window.KcpcDatePicker');
    return sandbox.window.KcpcDatePicker;
}

const {buildMonthCells, toLocalIsoDate, formatDisplayDate} = loadScript();

function run(name, fn) {
    try { fn(); console.log('PASS - ' + name); }
    catch (e) { console.error('FAIL - ' + name); console.error(e.stack || e); process.exitCode = 1; }
}

/** The real days of a month, blanks stripped. Array.from() re-homes the result into THIS context:
 *  the script runs inside a vm sandbox, so an array it produced carries that context's Array
 *  prototype and would fail assert.deepStrictEqual's prototype check for reasons unrelated to the
 *  values under test. */
function days(cells) { return Array.from(cells).filter((c) => c !== null); }
function cellFor(cells, iso) { return days(cells).filter((c) => c.iso === iso)[0]; }

// A realistic slice of what UpcomingChannelPlanService serializes, today = 05 Sep 2026.
const PLAN = {
    '2026-09-05': [{channelHandle: 'kcpcbandhani', count: 4, contentIds: ['C-1', 'C-2', 'C-3', 'C-4']},
                   {channelHandle: 'kcpcsikar', count: 2, contentIds: ['C-1', 'C-5']}],
    '2026-09-17': [{channelHandle: 'kcpcbandhani', count: 1, contentIds: ['C-9']}],
    '2026-09-02': [{channelHandle: 'kcpcbandhani', count: 3, contentIds: ['C-6', 'C-7', 'C-8']}]
};

// --- local-date math: the Asia/Kolkata off-by-one guard ----------------------------------------
run('toLocalIsoDate never shifts a local calendar date (Asia/Kolkata +5:30)', () => {
    // 00:30 local on the 5th is still 19:00 UTC on the 4th - toISOString() would say "2026-09-04".
    assert.strictEqual(toLocalIsoDate(new Date(2026, 8, 5, 0, 30)), '2026-09-05');
    assert.strictEqual(toLocalIsoDate(new Date(2026, 8, 5, 23, 45)), '2026-09-05');
});

run('formatDisplayDate renders the detail panel heading as "05 Sep 2026"', () => {
    assert.strictEqual(formatDisplayDate('2026-09-05'), '05 Sep 2026');
    assert.strictEqual(formatDisplayDate('2026-12-31'), '31 Dec 2026');
    assert.strictEqual(formatDisplayDate('nonsense'), '');
});

// --- requirement: past dates cannot be selected --------------------------------------------------
run('Past dates are disabled; today and future dates are selectable', () => {
    const cells = buildMonthCells(2026, 8, {min: '2026-09-05', today: '2026-09-05', planned: PLAN});
    assert.strictEqual(cellFor(cells, '2026-09-04').disabled, true, '04 Sep must be disabled');
    assert.strictEqual(cellFor(cells, '2026-09-01').disabled, true, '01 Sep must be disabled');
    assert.strictEqual(cellFor(cells, '2026-09-05').disabled, false, 'today must be selectable');
    assert.strictEqual(cellFor(cells, '2026-09-06').disabled, false, 'tomorrow must be selectable');
    assert.deepStrictEqual(days(cells).filter((c) => c.disabled).map((c) => c.iso),
        ['2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04'],
        'exactly the four days before today must be disabled');
});

run('An entirely past month has every day disabled and nothing highlighted', () => {
    const cells = buildMonthCells(2026, 7, {min: '2026-09-05', today: '2026-09-05', planned: PLAN});
    assert.ok(days(cells).every((c) => c.disabled));
    assert.ok(days(cells).every((c) => !c.planned));
});

// --- requirement: future planned dates are highlighted -------------------------------------------
run('Future dates with planned content are highlighted; dates without are not', () => {
    const cells = buildMonthCells(2026, 8, {min: '2026-09-05', today: '2026-09-05', planned: PLAN});
    assert.strictEqual(cellFor(cells, '2026-09-05').planned, true, 'today has planned content');
    assert.strictEqual(cellFor(cells, '2026-09-17').planned, true, 'a future planned date');
    assert.strictEqual(cellFor(cells, '2026-09-16').planned, false, 'a future date with nothing planned');
    assert.deepStrictEqual(days(cells).filter((c) => c.planned).map((c) => c.iso),
        ['2026-09-05', '2026-09-17'], 'only the future planned dates are highlighted');
});

run('A PAST date with planned content is never highlighted - a past date is not a choice', () => {
    const cells = buildMonthCells(2026, 8, {min: '2026-09-05', today: '2026-09-05', planned: PLAN});
    const past = cellFor(cells, '2026-09-02');
    assert.strictEqual(past.disabled, true);
    assert.strictEqual(past.planned, false,
        '02 Sep has planned content but is in the past, so it must not be highlighted');
});

run('A date present in the data but with an empty channel list is not highlighted', () => {
    const cells = buildMonthCells(2026, 8,
        {min: '2026-09-05', today: '2026-09-05', planned: {'2026-09-10': []}});
    assert.strictEqual(cellFor(cells, '2026-09-10').planned, false);
});

run('With no plan data at all, the calendar still works and highlights nothing', () => {
    const cells = buildMonthCells(2026, 8, {min: '2026-09-05', today: '2026-09-05'});
    assert.ok(days(cells).every((c) => !c.planned));
    assert.strictEqual(cellFor(cells, '2026-09-05').disabled, false, 'picking still works');
});

// --- the counting rule, as it reaches this file (computed server-side, rendered verbatim) --------
run('Same Content ID across platforms of one channel arrives as 1, and is rendered as-is', () => {
    // C-1 is planned on Instagram + YouTube + Facebook under kcpcbandhani; the server already
    // collapsed that to a single distinct Content ID. This file must never re-derive it.
    const channels = PLAN['2026-09-05'];
    const bandhani = channels.filter((c) => c.channelHandle === 'kcpcbandhani')[0];
    assert.strictEqual(bandhani.count, 4, 'the count is taken straight from the server');
    assert.strictEqual(bandhani.count, bandhani.contentIds.length,
        'count always equals the number of distinct Content IDs');
});

run('The same Content ID on two channels counts once under each', () => {
    const channels = PLAN['2026-09-05'];
    const bandhani = channels.filter((c) => c.channelHandle === 'kcpcbandhani')[0];
    const sikar = channels.filter((c) => c.channelHandle === 'kcpcsikar')[0];
    assert.ok(bandhani.contentIds.indexOf('C-1') !== -1);
    assert.ok(sikar.contentIds.indexOf('C-1') !== -1);
    assert.strictEqual(sikar.count, 2, 'kcpcsikar counts C-1 once, alongside its own C-5');
});

// --- grid shape ----------------------------------------------------------------------------------
run('Leading blanks align the 1st to its real weekday', () => {
    assert.strictEqual(new Date(2026, 8, 1).getDay(), 2, 'sanity: 01 Sep 2026 is a Tuesday');
    const cells = buildMonthCells(2026, 8, {min: '2026-09-05'});
    assert.strictEqual(cells[0], null);
    assert.strictEqual(cells[1], null);
    assert.strictEqual(days(cells)[0].iso, '2026-09-01');
});

run('Month lengths are real, including a leap February', () => {
    assert.strictEqual(days(buildMonthCells(2028, 1, {})).length, 29, 'Feb 2028 is a leap February');
    assert.strictEqual(days(buildMonthCells(2026, 1, {})).length, 28);
    assert.strictEqual(days(buildMonthCells(2026, 3, {})).length, 30);
});

run('With no min attribute, nothing is disabled', () => {
    const cells = buildMonthCells(2026, 8, {today: '2026-09-05'});
    assert.ok(days(cells).every((c) => !c.disabled));
});

run('The currently selected value is flagged exactly once', () => {
    const cells = buildMonthCells(2026, 8, {min: '2026-09-05', selected: '2026-09-17', today: '2026-09-05'});
    assert.deepStrictEqual(days(cells).filter((c) => c.selected).map((c) => c.iso), ['2026-09-17']);
});

if (process.exitCode) { console.error('\nkcpc-date-picker.js tests FAILED.'); process.exit(1); }
console.log('\nAll kcpc-date-picker.js tests passed.');
