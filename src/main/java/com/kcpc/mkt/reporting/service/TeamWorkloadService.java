package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.reporting.dto.AssigneeLoadRow;
import com.kcpc.mkt.reporting.dto.EmployeeWorkloadRow;
import com.kcpc.mkt.reporting.dto.TeamWorkloadResult;
import com.kcpc.mkt.reporting.dto.WorkloadContentItem;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ENG-087: Team Workload management dashboard - deliberately two SEPARATE aggregations sharing
 * only the same filtered active-plan set and "today"/hold data, never one derived from the other:
 * <ul>
 *   <li>{@code stageCounts} - "how much active content is in each lifecycle stage right now,"
 *   one Content ID counted in exactly one of Planning/Shoot/Edit/Publishing/Performance, derived
 *   purely from {@link WorkflowStatus} (a Camera Person already being selected during Planning
 *   never moves a plan into the Shoot bucket - only the plan's own current status decides that).</li>
 *   <li>{@code assigneeRows} - "how much actionable work does each employee actually have,"
 *   counting real {@link ShootingAssignment}/{@link EditingAssignment}/{@link PublishingAssignment}/
 *   {@link ContentPlanTalentEntry} rows, gated by the SAME "is this stage still this role's own
 *   active window" rule {@code LandingMvcController} already uses for My Work's Active-vs-Completed
 *   split (an assignment whose plan has already moved past that role's stage doesn't count as
 *   "active" for them, even if the assignment row itself was never explicitly ended).</li>
 * </ul>
 * Delayed/On-Hold are never recomputed here - Delayed reuses
 * {@link PipelineDashboardService#delayDays} verbatim (the same rule Pipeline/My Work already use),
 * On-Hold reuses {@link WorkHoldRecordRepository#findByResumedAtIsNull()} verbatim (the same
 * "currently open hold" query {@code LandingMvcController} already uses) - no new delay/hold logic
 * is introduced anywhere in this class.
 */
@Service
public class TeamWorkloadService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    // Shoot/Edit/Publishing windows now live in AssigneeActiveWindows (single source of truth also
    // used by AssigneeWorkloadCountService's assignee-picker task counts - see that class's javadoc).
    private static final Set<WorkflowStatus> SHOOT_WINDOW = AssigneeActiveWindows.SHOOT;
    private static final Set<WorkflowStatus> EDIT_WINDOW = AssigneeActiveWindows.EDIT;
    private static final Set<WorkflowStatus> PUBLISHING_WINDOW = AssigneeActiveWindows.PUBLISHING;
    private static final Set<WorkflowStatus> PERFORMANCE_WINDOW = EnumSet.of(
            WorkflowStatus.PP, WorkflowStatus.PFUP);

    private final ContentPlanRepository contentPlanRepository;
    private final UserRepository userRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;
    private final AuthorizationService authorizationService;

    public TeamWorkloadService(ContentPlanRepository contentPlanRepository, UserRepository userRepository,
                                ShootingAssignmentRepository shootingAssignmentRepository,
                                EditingAssignmentRepository editingAssignmentRepository,
                                PublishingAssignmentRepository publishingAssignmentRepository,
                                ContentPlanTalentEntryRepository talentEntryRepository,
                                WorkHoldRecordRepository workHoldRecordRepository,
                                AuthorizationService authorizationService) {
        this.contentPlanRepository = contentPlanRepository;
        this.userRepository = userRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public TeamWorkloadResult teamWorkloadDashboard(User requester, String businessRole, UUID employeeId,
                                                      String stage, LocalDate dateFrom, LocalDate dateTo,
                                                      boolean delayedOnly) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW, null, null);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        // Date Range fix: each stage's own applicable planned date, never plannedLiveDate
        // universally (Shoot -> plannedShootDate, Edit -> plannedEditDate, Publishing ->
        // plannedLiveDate) - so the date filter is applied INSIDE countInWindowByStageDate per
        // stage below, not once upfront here the way it incorrectly was before this fix.
        List<ContentPlan> allActivePlans = contentPlanRepository.findAllWithPreparedByOrderByCreatedAtDesc().stream()
                .filter(p -> isActiveStatus(p.getWorkflowInstance().getCurrentStatusCode()))
                .toList();

        Map<String, Long> stageCounts = new LinkedHashMap<>();
        stageCounts.put("Shoot", countInWindowByStageDate(allActivePlans, SHOOT_WINDOW,
                ContentPlan::getPlannedShootDate, dateFrom, dateTo, delayedOnly, today));
        stageCounts.put("Edit", countInWindowByStageDate(allActivePlans, EDIT_WINDOW,
                ContentPlan::getPlannedEditDate, dateFrom, dateTo, delayedOnly, today));
        stageCounts.put("Publishing", countInWindowByStageDate(allActivePlans, PUBLISHING_WINDOW,
                ContentPlan::getPlannedLiveDate, dateFrom, dateTo, delayedOnly, today));
        // Performance is deliberately EXCLUDED from the per-stage date mapping and the delayed-task
        // exemption above - kept byte-for-byte equivalent to this screen's pre-fix behavior (still
        // pre-filtered by plannedLiveDate, upfront, exactly as every stage used to be) per the
        // explicit instruction not to change this screen's own Performance handling. This is a
        // different "Performance" concept from the separate Team -> Performance screen
        // (/reports/team-kpis, KpiService#teamKpis) - that screen is untouched by this class either way.
        List<ContentPlan> performancePlans = allActivePlans.stream()
                .filter(p -> inDateRange(p.getPlannedLiveDate(), dateFrom, dateTo))
                .toList();
        stageCounts.put("Performance", countInWindow(performancePlans, PERFORMANCE_WINDOW, delayedOnly, today));
        long totalActiveByStage = stageCounts.values().stream().mapToLong(Long::longValue).sum();
        // Active Tasks by Stage is a lifecycle summary ("how much active content is in each
        // stage right now") - the Stage dropdown must never filter/recompute it down to one row;
        // that dropdown scopes Assignee Load only (below), a completely different reporting
        // concept ("who currently has workload for the selected stage").

        Set<UUID> onHoldInstanceIds = workHoldRecordRepository.findByResumedAtIsNull().stream()
                .map(h -> h.getWorkflowInstance().getId()).collect(Collectors.toSet());

        Map<UUID, List<ShootingAssignment>> shootByUser = shootingAssignmentRepository.findByActiveTrue().stream()
                .collect(Collectors.groupingBy(a -> a.getCameraperson().getId()));
        Map<UUID, List<EditingAssignment>> editByUser = editingAssignmentRepository.findByActiveTrue().stream()
                .collect(Collectors.groupingBy(a -> a.getEditor().getId()));
        Map<UUID, List<PublishingAssignment>> publishByUser = publishingAssignmentRepository.findByActiveTrue().stream()
                .collect(Collectors.groupingBy(a -> a.getPublisher().getId()));

        // Assignee Load ("who currently has workload for the selected stage") is the ONE panel
        // the Stage dropdown scopes - ALL (or blank) keeps today's normal combined view unchanged;
        // a specific stage restricts rows to that stage's own real assignment concept, never a
        // fabricated one (Performance has no assignee concept in this codebase at all, so selecting
        // it intentionally yields zero rows rather than inventing one).
        String stageFilter = (stage == null || stage.isBlank()) ? "ALL" : stage;
        boolean wantsAllStages = "ALL".equalsIgnoreCase(stageFilter);

        List<AssigneeLoadRow> rows = new ArrayList<>();
        boolean wantsRole = businessRole != null && !businessRole.isBlank() && !"ALL".equalsIgnoreCase(businessRole);
        // Permission-driven multi-function workflow: population is now assignment-based (every real
        // active assignee, whatever their Business Role) - Business Role remains available only as
        // a post-hoc display/filter dimension on the resulting rows, never a gate on which real
        // assignees are even considered (an HR Manager with a real active ShootingAssignment now
        // correctly appears here, matching KPI-006/legacy teamWorkload()'s existing role-agnostic
        // behavior instead of silently diverging from it - spec §21).
        if (wantsAllStages || "Shoot".equalsIgnoreCase(stageFilter)) {
            for (var entry : shootByUser.entrySet()) {
                User u = entry.getValue().get(0).getCameraperson();
                String roleName = displayRoleName(u);
                if (wantsRole && !businessRole.equals(roleName)) {
                    continue;
                }
                rows.add(rowFromAssignments(u, roleName, "Shoot", entry.getValue(), ShootingAssignment::getContentPlan,
                        SHOOT_WINDOW, ContentPlan::getPlannedShootDate, dateFrom, dateTo, delayedOnly, onHoldInstanceIds, today));
            }
        }
        if (wantsAllStages || "Edit".equalsIgnoreCase(stageFilter)) {
            for (var entry : editByUser.entrySet()) {
                User u = entry.getValue().get(0).getEditor();
                String roleName = displayRoleName(u);
                if (wantsRole && !businessRole.equals(roleName)) {
                    continue;
                }
                rows.add(rowFromAssignments(u, roleName, "Edit", entry.getValue(), EditingAssignment::getContentPlan,
                        EDIT_WINDOW, ContentPlan::getPlannedEditDate, dateFrom, dateTo, delayedOnly, onHoldInstanceIds, today));
            }
        }
        if (wantsAllStages || "Publishing".equalsIgnoreCase(stageFilter)) {
            for (var entry : publishByUser.entrySet()) {
                User u = entry.getValue().get(0).getPublisher();
                String roleName = displayRoleName(u);
                if (wantsRole && !businessRole.equals(roleName)) {
                    continue;
                }
                rows.add(rowFromAssignments(u, roleName, "Publishing", entry.getValue(), PublishingAssignment::getContentPlan,
                        PUBLISHING_WINDOW, ContentPlan::getPlannedLiveDate, dateFrom, dateTo, delayedOnly, onHoldInstanceIds, today));
            }
        }
        // Model remains talent scheduling, deliberately NOT converted to the permission-driven
        // executor model (spec §8.4/§18 - "do not merge Model talent scheduling with executor
        // assignments") - still Business-Role-name filtered, unchanged. "Model" is also now an
        // explicitly selectable Stage filter value (parallel to Shoot/Edit/Publishing) - previously
        // Model rows only ever appeared under "All Stages", with no way to isolate them the same
        // way the other three stages can be isolated.
        if ((wantsAllStages || "Model".equalsIgnoreCase(stageFilter)) && (!wantsRole || "Model".equals(businessRole))) {
            for (User u : userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Model")) {
                rows.add(modelRow(u, dateFrom, dateTo, delayedOnly, onHoldInstanceIds, today));
            }
        }
        if (employeeId != null) {
            rows = rows.stream().filter(r -> employeeId.equals(r.getUserId())).toList();
        }
        if (delayedOnly) {
            rows = rows.stream().filter(r -> r.getDelayedTasks() > 0).toList();
        }

        // Employee-wise UI update: the main table shows ONE row per employee (grouped by userId,
        // insertion order preserved so ties/ordering stay deterministic before the final name
        // sort). Delayed/On Hold are still a genuine SUM over the employee's own per-(stage) rows
        // above, never a second, independently-computed total - unchanged.
        //
        // Active Tasks is deliberately NOT a sum of the stage rows: one real-world unit of work is
        // "an employee currently has actionable work on this Content ID," and the same Content ID
        // can carry more than one of this employee's roles at once (e.g. Model + Cameraperson, both
        // riding the same SHOOT_WINDOW) - counting each role's own stage row would double-count that
        // single Content ID. Instead, Active Tasks is the number of DISTINCT Content Plan ids across
        // every item already surviving into this employee's stage rows' own `items` (every item is,
        // by construction, one the per-stage active-window filter already let through - see
        // AssigneeLoadRow's own javadoc: items.size() == that row's own activeTasks) - reusing the
        // exact ContentPlan already carried on WorkloadContentItem, never a new query and never a
        // second, independently-computed Content ID source. This is why the employee's own main-row
        // Active Tasks can legitimately be LESS than the sum of the stage-breakdown rows shown when
        // expanded (Delayed/On Hold do not have this property - they stay a plain sum, unchanged).
        Map<UUID, List<AssigneeLoadRow>> byEmployee = rows.stream()
                .collect(Collectors.groupingBy(AssigneeLoadRow::getUserId, LinkedHashMap::new, Collectors.toList()));
        List<EmployeeWorkloadRow> employeeRows = new ArrayList<>();
        for (Map.Entry<UUID, List<AssigneeLoadRow>> entry : byEmployee.entrySet()) {
            List<AssigneeLoadRow> stageRows = entry.getValue();
            long empActive = stageRows.stream()
                    .flatMap(r -> r.getItems().stream())
                    .map(WorkloadContentItem::getContentPlanId)
                    .distinct()
                    .count();
            long empDelayed = stageRows.stream().mapToLong(AssigneeLoadRow::getDelayedTasks).sum();
            long empOnHold = stageRows.stream().mapToLong(AssigneeLoadRow::getOnHoldTasks).sum();
            // Spec §10: "only show stages where the employee actually has relevant workload" - a
            // stage row contributing zero to every count is omitted from the drill-down (it would
            // add nothing worth expanding into), never from the employee's own main-row totals.
            List<AssigneeLoadRow> breakdown = stageRows.stream()
                    .filter(r -> r.getActiveTasks() > 0 || r.getDelayedTasks() > 0 || r.getOnHoldTasks() > 0)
                    .toList();
            AssigneeLoadRow first = stageRows.get(0);
            employeeRows.add(new EmployeeWorkloadRow(entry.getKey(), first.getAssigneeName(),
                    first.getBusinessRoleName(), empActive, empDelayed, empOnHold, breakdown));
        }
        employeeRows.sort(Comparator.comparing(EmployeeWorkloadRow::getAssigneeName, String.CASE_INSENSITIVE_ORDER));

        long totalActive = employeeRows.stream().mapToLong(EmployeeWorkloadRow::getActiveTasks).sum();
        long totalDelayed = employeeRows.stream().mapToLong(EmployeeWorkloadRow::getDelayedTasks).sum();
        long totalOnHold = employeeRows.stream().mapToLong(EmployeeWorkloadRow::getOnHoldTasks).sum();

        return new TeamWorkloadResult(stageCounts, totalActiveByStage, employeeRows, totalActive, totalDelayed,
                totalOnHold, Instant.now());
    }

    /** Display-only Business Role label for an Assignee Load row - never an eligibility filter. */
    private static String displayRoleName(User u) {
        return u.getBusinessRole() == null ? null : u.getBusinessRole().getRoleName();
    }

    private static boolean isActiveStatus(WorkflowStatus status) {
        return !AssigneeActiveWindows.CLOSED_OUT.contains(status);
    }

    private static boolean inDateRange(LocalDate date, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    /**
     * Date Range fix ("DELAYED TASK HANDLING"): a currently-delayed task must remain visible even
     * when its applicable stage date falls BEFORE the selected From date - a manager must never
     * lose sight of currently-delayed work just because its planned date has since fallen outside
     * the selected window. This exemption is scoped EXACTLY as specified: it only overrides the
     * "date is before From" exclusion. A date falling AFTER the selected To date is excluded
     * regardless of delay status - the spec never asked for that direction to be exempted, and
     * generalizing this into "delayed tasks skip the date filter entirely" would be inventing a
     * broader rule than the one actually requested.
     */
    private static boolean withinRangeOrDelayedException(LocalDate date, LocalDate from, LocalDate to,
                                                           boolean isDelayed) {
        if (from == null && to == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return isDelayed;
        }
        return true;
    }

    /** Performance's own stage count - unchanged from this screen's pre-fix behavior (the caller
     * pre-filters {@code plans} by plannedLiveDate upfront; no per-stage date, no delayed
     * exemption - see the "Performance is deliberately EXCLUDED" comment at its call site). */
    private static long countInWindow(List<ContentPlan> plans, Set<WorkflowStatus> window, boolean delayedOnly,
                                       LocalDate today) {
        return plans.stream()
                .filter(p -> window.contains(p.getWorkflowInstance().getCurrentStatusCode()))
                .filter(p -> !delayedOnly || PipelineDashboardService.delayDays(
                        p.getWorkflowInstance().getCurrentStatusCode(), p, today) != null)
                .count();
    }

    /** Shoot/Edit/Publishing's own "Active Tasks by Stage" count - the applicable stage planned
     * date (never plannedLiveDate universally) plus the same delayed-task exemption
     * {@link #rowFromAssignments} applies, so the stage card and Assignee Load can never disagree
     * about which plans are eligible for the selected range (spec §17/§18). */
    private static long countInWindowByStageDate(List<ContentPlan> plans, Set<WorkflowStatus> window,
                                                  Function<ContentPlan, LocalDate> stageDateOf, LocalDate dateFrom,
                                                  LocalDate dateTo, boolean delayedOnly, LocalDate today) {
        long count = 0;
        for (ContentPlan p : plans) {
            WorkflowStatus status = p.getWorkflowInstance().getCurrentStatusCode();
            if (!window.contains(status)) {
                continue;
            }
            boolean isDelayed = PipelineDashboardService.delayDays(status, p, today) != null;
            if (!withinRangeOrDelayedException(stageDateOf.apply(p), dateFrom, dateTo, isDelayed)) {
                continue;
            }
            if (delayedOnly && !isDelayed) {
                continue;
            }
            count++;
        }
        return count;
    }

    /** Shared by Camera Person/Video Editor/Publisher - the same shape, just a different assignment
     * type. {@code stageDateOf} is now actually used (Date Range fix) - previously accepted but
     * silently ignored, with every stage incorrectly filtered on plannedLiveDate regardless of
     * which stage's own date should have governed it. Content ID drill-down: {@code items} is
     * built from the SAME surviving records the counts above come from - one
     * {@link WorkloadContentItem} per record that reaches {@code active++}, never a second query
     * and never deduplicated (see this method's own investigation note at the call sites on why a
     * genuine duplicate Content ID here would be a real, not a spurious, workload unit). */
    private <A> AssigneeLoadRow rowFromAssignments(User u, String roleName, String stage, List<A> assignments,
                                                     Function<A, ContentPlan> planOf, Set<WorkflowStatus> window,
                                                     Function<ContentPlan, LocalDate> stageDateOf,
                                                     LocalDate dateFrom, LocalDate dateTo, boolean delayedOnly,
                                                     Set<UUID> onHoldInstanceIds, LocalDate today) {
        if (assignments == null) {
            assignments = List.of();
        }
        long active = 0;
        long delayed = 0;
        long onHold = 0;
        List<WorkloadContentItem> items = new ArrayList<>();
        for (A a : assignments) {
            ContentPlan plan = planOf.apply(a);
            WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
            if (!window.contains(status)) {
                continue;
            }
            Integer delayDays = PipelineDashboardService.delayDays(status, plan, today);
            boolean isDelayed = delayDays != null;
            LocalDate applicableDate = stageDateOf.apply(plan);
            if (!withinRangeOrDelayedException(applicableDate, dateFrom, dateTo, isDelayed)) {
                continue;
            }
            if (delayedOnly && !isDelayed) {
                continue;
            }
            active++;
            if (isDelayed) {
                delayed++;
            }
            if (onHoldInstanceIds.contains(plan.getWorkflowInstance().getId())) {
                onHold++;
            }
            items.add(new WorkloadContentItem(plan.getId(), plan.getContentId(), delayDays, applicableDate));
        }
        return new AssigneeLoadRow(u.getId(), u.getFullName(), roleName, stage, active, delayed, onHold, items);
    }

    /**
     * Model has no execution/review gate of its own (BRS-REQ: talent participation, not a
     * workflow-assignable stage) - never a fabricated Start/Review task, matching the explicit
     * "do not invent Model execution tasks" instruction; still sourced straight from
     * {@link ContentPlanTalentEntry}, never converted into an assignment type of its own.
     * "Active," however, is NOT "still linked to a not-yet-closed plan" (that's {@code CLOSED_OUT}
     * / {@link #isActiveStatus}, deliberately not used here) - the explicit business rule is
     * "Model's work is tied to the Shoot stage. As soon as the Shoot work is completed [or
     * skipped], the Model's work is also considered completed," so a Model's Active Tasks count
     * uses the exact same {@code SHOOT_WINDOW} ({@link AssigneeActiveWindows#SHOOT}) Cameraperson's
     * own Shoot row already uses - once the plan's status moves past Shoot (Edit, Publishing,
     * Performance, Completed, or skipped straight to Edit) the Model's task stops counting as
     * active, exactly like a Cameraperson's Shoot assignment already does. Date basis follows the
     * same rule: {@code plannedShootDate}, the same date Shoot itself uses, never
     * {@code plannedLiveDate}. The universal delayed-task exemption (spec §4/Part 1, stated with no
     * stage carve-out) is still applied here, since that rule was never scoped to only
     * Shoot/Edit/Publishing.
     */
    private AssigneeLoadRow modelRow(User u, LocalDate dateFrom, LocalDate dateTo, boolean delayedOnly,
                                      Set<UUID> onHoldInstanceIds, LocalDate today) {
        long active = 0;
        long delayed = 0;
        long onHold = 0;
        List<WorkloadContentItem> items = new ArrayList<>();
        for (ContentPlanTalentEntry entry : talentEntryRepository.findByTalentUser(u)) {
            ContentPlan plan = entry.getContentPlan();
            WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
            if (!SHOOT_WINDOW.contains(status)) {
                continue;
            }
            Integer delayDays = PipelineDashboardService.delayDays(status, plan, today);
            boolean isDelayed = delayDays != null;
            LocalDate applicableDate = plan.getPlannedShootDate();
            if (!withinRangeOrDelayedException(applicableDate, dateFrom, dateTo, isDelayed)) {
                continue;
            }
            if (delayedOnly && !isDelayed) {
                continue;
            }
            active++;
            if (isDelayed) {
                delayed++;
            }
            if (onHoldInstanceIds.contains(plan.getWorkflowInstance().getId())) {
                onHold++;
            }
            items.add(new WorkloadContentItem(plan.getId(), plan.getContentId(), delayDays, applicableDate));
        }
        return new AssigneeLoadRow(u.getId(), u.getFullName(), "Model", "Model", active, delayed, onHold, items);
    }

}
