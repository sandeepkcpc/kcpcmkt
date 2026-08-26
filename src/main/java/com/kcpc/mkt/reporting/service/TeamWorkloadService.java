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
import com.kcpc.mkt.reporting.dto.TeamWorkloadResult;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

    private static final Set<WorkflowStatus> PLANNING_WINDOW = EnumSet.of(
            WorkflowStatus.PL, WorkflowStatus.PLRV, WorkflowStatus.PLAP);
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

        List<ContentPlan> activePlans = contentPlanRepository.findAllWithPreparedByOrderByCreatedAtDesc().stream()
                .filter(p -> isActiveStatus(p.getWorkflowInstance().getCurrentStatusCode()))
                .filter(p -> inDateRange(p.getPlannedLiveDate(), dateFrom, dateTo))
                .toList();

        Map<String, Long> stageCounts = new LinkedHashMap<>();
        stageCounts.put("Planning", countInWindow(activePlans, PLANNING_WINDOW, delayedOnly, today));
        stageCounts.put("Shoot", countInWindow(activePlans, SHOOT_WINDOW, delayedOnly, today));
        stageCounts.put("Edit", countInWindow(activePlans, EDIT_WINDOW, delayedOnly, today));
        stageCounts.put("Publishing", countInWindow(activePlans, PUBLISHING_WINDOW, delayedOnly, today));
        stageCounts.put("Performance", countInWindow(activePlans, PERFORMANCE_WINDOW, delayedOnly, today));
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
        // fabricated one (Planning uses ContentPlan#preparedBy - the same authoritative provenance
        // PlanningPreparer/the self-review-conflict guard already relies on - since Planning has no
        // ShootingAssignment-style table; Performance has no assignee concept in this codebase at
        // all, so selecting it intentionally yields zero rows rather than inventing one).
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
                rows.add(rowFromAssignments(u, roleName, entry.getValue(), ShootingAssignment::getContentPlan,
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
                rows.add(rowFromAssignments(u, roleName, entry.getValue(), EditingAssignment::getContentPlan,
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
                rows.add(rowFromAssignments(u, roleName, entry.getValue(), PublishingAssignment::getContentPlan,
                        PUBLISHING_WINDOW, ContentPlan::getPlannedLiveDate, dateFrom, dateTo, delayedOnly, onHoldInstanceIds, today));
            }
        }
        // Model remains talent scheduling, deliberately NOT converted to the permission-driven
        // executor model (spec §8.4/§18 - "do not merge Model talent scheduling with executor
        // assignments") - still Business-Role-name filtered, unchanged.
        if (wantsAllStages && (!wantsRole || "Model".equals(businessRole))) {
            for (User u : userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Model")) {
                rows.add(modelRow(u, dateFrom, dateTo, delayedOnly, onHoldInstanceIds, today));
            }
        }
        if ("Planning".equalsIgnoreCase(stageFilter)) {
            rows.addAll(planningRows(activePlans, businessRole, wantsRole, delayedOnly, onHoldInstanceIds, today));
        }

        if (employeeId != null) {
            rows = rows.stream().filter(r -> employeeId.equals(r.getUserId())).toList();
        }
        if (delayedOnly) {
            rows = rows.stream().filter(r -> r.getDelayedTasks() > 0).toList();
        }

        long totalActive = rows.stream().mapToLong(AssigneeLoadRow::getActiveTasks).sum();
        long totalDelayed = rows.stream().mapToLong(AssigneeLoadRow::getDelayedTasks).sum();
        long totalOnHold = rows.stream().mapToLong(AssigneeLoadRow::getOnHoldTasks).sum();

        return new TeamWorkloadResult(stageCounts, totalActiveByStage, rows, totalActive, totalDelayed, totalOnHold,
                Instant.now());
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

    private static long countInWindow(List<ContentPlan> plans, Set<WorkflowStatus> window, boolean delayedOnly,
                                       LocalDate today) {
        return plans.stream()
                .filter(p -> window.contains(p.getWorkflowInstance().getCurrentStatusCode()))
                .filter(p -> !delayedOnly || PipelineDashboardService.delayDays(
                        p.getWorkflowInstance().getCurrentStatusCode(), p, today) != null)
                .count();
    }

    /** Shared by Camera Person/Video Editor/Publisher - the same shape, just a different assignment type. */
    private <A> AssigneeLoadRow rowFromAssignments(User u, String roleName, List<A> assignments,
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
        for (A a : assignments) {
            ContentPlan plan = planOf.apply(a);
            WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
            if (!window.contains(status) || !inDateRange(plan.getPlannedLiveDate(), dateFrom, dateTo)) {
                continue;
            }
            boolean isDelayed = PipelineDashboardService.delayDays(status, plan, today) != null;
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
        }
        return new AssigneeLoadRow(u.getId(), u.getFullName(), roleName, active, delayed, onHold);
    }

    /**
     * Model has no execution/review gate of its own (BRS-REQ: talent participation, not a
     * workflow-assignable stage) - "active" here is simply "still linked to a not-yet-closed
     * plan," never a fabricated Start/Review task, matching the explicit "do not invent Model
     * execution tasks" instruction.
     */
    private AssigneeLoadRow modelRow(User u, LocalDate dateFrom, LocalDate dateTo, boolean delayedOnly,
                                      Set<UUID> onHoldInstanceIds, LocalDate today) {
        long active = 0;
        long delayed = 0;
        long onHold = 0;
        for (ContentPlanTalentEntry entry : talentEntryRepository.findByTalentUser(u)) {
            ContentPlan plan = entry.getContentPlan();
            WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
            if (!isActiveStatus(status) || !inDateRange(plan.getPlannedLiveDate(), dateFrom, dateTo)) {
                continue;
            }
            boolean isDelayed = PipelineDashboardService.delayDays(status, plan, today) != null;
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
        }
        return new AssigneeLoadRow(u.getId(), u.getFullName(), "Model", active, delayed, onHold);
    }

    /**
     * Planning has no Shoot/Edit/Publishing-style assignment table - {@link ContentPlan#getPreparedBy()}
     * (the same authoritative preparer reference {@link com.kcpc.mkt.planning.domain.PlanningPreparer}
     * / the self-review-conflict guard already use) is the one real "who did the planning work"
     * concept this backend supports, so it - never a fabricated Planning task/assignment - is what
     * Stage=Planning shows in Assignee Load. {@code plans} is already the outer method's
     * date-range-filtered {@code activePlans}, so no date check is repeated here (same convention
     * {@link #countInWindow} already uses for the stage-summary panel).
     */
    private List<AssigneeLoadRow> planningRows(List<ContentPlan> plans, String businessRole, boolean wantsRole,
                                                boolean delayedOnly, Set<UUID> onHoldInstanceIds, LocalDate today) {
        Map<UUID, List<ContentPlan>> planningByPreparer = plans.stream()
                .filter(p -> PLANNING_WINDOW.contains(p.getWorkflowInstance().getCurrentStatusCode()))
                .filter(p -> p.getPreparedBy() != null)
                .collect(Collectors.groupingBy(p -> p.getPreparedBy().getId()));

        List<AssigneeLoadRow> rows = new ArrayList<>();
        for (List<ContentPlan> preparedPlans : planningByPreparer.values()) {
            User preparer = preparedPlans.get(0).getPreparedBy();
            String roleName = preparer.getBusinessRole() != null ? preparer.getBusinessRole().getRoleName() : null;
            if (wantsRole && !businessRole.equals(roleName)) {
                continue;
            }
            long active = 0;
            long delayed = 0;
            long onHold = 0;
            for (ContentPlan plan : preparedPlans) {
                WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
                boolean isDelayed = PipelineDashboardService.delayDays(status, plan, today) != null;
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
            }
            rows.add(new AssigneeLoadRow(preparer.getId(), preparer.getFullName(), roleName, active, delayed, onHold));
        }
        rows.sort(java.util.Comparator.comparing(AssigneeLoadRow::getAssigneeName));
        return rows;
    }
}
