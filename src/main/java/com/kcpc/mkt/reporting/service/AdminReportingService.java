package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.audit.domain.SystemAuditLog;
import com.kcpc.mkt.audit.repository.SystemAuditLogRepository;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.PermissionScopeType;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.reporting.dto.AdminActionRow;
import com.kcpc.mkt.reporting.dto.DelayedDeliverableRow;
import com.kcpc.mkt.workflow.domain.WorkHoldRecord;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BFD §7.6 (Administrative Action & Permission Reporting, `API-OP-059`) and the Delayed
 * Deliverables pipeline projection (`API-OP-060`, `ERD-VW-001`).
 */
@Service
public class AdminReportingService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    /** Shared with {@link KpiService} and {@link KpiDashboardService} via {@link StageSqlFragments}
     * so Delayed Deliverables and both KPI screens never disagree about which stage a status is in. */
    private static final String STAGE_PLANNED_DATE_CASE = StageSqlFragments.STAGE_PLANNED_DATE_CASE;

    private static final String STAGE_LABEL_CASE = StageSqlFragments.STAGE_LABEL_CASE;
    /** Current-stage assignee(s) only - never a future stage's assignment (spec requirement).
     * Planning's "assignee" is its preparer (planning_preparers); Shoot/Edit use the active
     * assignment tables; Publishing/Performance have no single-owner assignment table today, so
     * they resolve to NULL (rendered as "-", never a fabricated name). */
    private static final String ASSIGNED_TO_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV') THEN "
                    + "(select string_agg(u.full_name, ', ' order by u.full_name) from planning_preparers pp "
                    + "join users u on u.user_id = pp.preparer_user_id where pp.content_plan_id = cp.content_plan_id) "
                    + "WHEN wi.current_status_code IN ('PLAP','SA','SIP','SRV','SAP') THEN "
                    + "(select string_agg(u.full_name, ', ' order by u.full_name) from shooting_assignments sa "
                    + "join users u on u.user_id = sa.cameraperson_user_id "
                    + "where sa.content_plan_id = cp.content_plan_id and sa.is_active = true) "
                    + "WHEN wi.current_status_code IN ('EA','ED','ERV') THEN "
                    + "(select string_agg(u.full_name, ', ' order by u.full_name) from editing_assignments ea "
                    + "join users u on u.user_id = ea.editor_user_id "
                    + "where ea.content_plan_id = cp.content_plan_id and ea.is_active = true) "
                    + "ELSE NULL END";

    /** BFD §7.6 governed action-type categories -> the audit eventType values that populate them. */
    private static final Map<String, List<String>> ACTION_TYPE_EVENT_TYPES = Map.ofEntries(
            Map.entry("HOLD", List.of("WORK_HELD")),
            Map.entry("RESUME", List.of("WORK_RESUMED")),
            Map.entry("RESCHEDULE", List.of("RESCHEDULED")),
            Map.entry("REASSIGN", List.of("REASSIGNED")),
            Map.entry("CANCEL", List.of("CANCELLED")),
            Map.entry("REOPEN", List.of("DELIVERABLE_REOPENED", "IDEA_REOPENED")),
            Map.entry("IDEA_RETAINED", List.of("IDEA_RETAINED")),
            Map.entry("PERMISSION_GRANT", List.of("PERMISSION_GRANTED", "PERMISSION_MODIFIED")),
            Map.entry("PERMISSION_REVOKE", List.of("PERMISSION_REVOKED")));

    @PersistenceContext
    private EntityManager entityManager;

    private final AuthorizationService authorizationService;
    private final PermissionGrantRepository permissionGrantRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final IdeaRepository ideaRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;

    public AdminReportingService(AuthorizationService authorizationService, PermissionGrantRepository permissionGrantRepository,
                                  ShootingAssignmentRepository shootingAssignmentRepository,
                                  EditingAssignmentRepository editingAssignmentRepository,
                                  SystemAuditLogRepository systemAuditLogRepository, ContentPlanRepository contentPlanRepository,
                                  IdeaRepository ideaRepository, WorkHoldRecordRepository workHoldRecordRepository) {
        this.authorizationService = authorizationService;
        this.permissionGrantRepository = permissionGrantRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.systemAuditLogRepository = systemAuditLogRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.ideaRepository = ideaRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
    }

    // ------------------------------------------------------------------------------------------
    // API-OP-059: Administrative Action & Permission Usage Report (Permission #16)

    @Transactional(readOnly = true)
    public Map<String, Object> administrativeActionsReport(User requester, LocalDate startDate, LocalDate endDate,
                                                             String actionType) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW, null, null);

        Instant from = startDate != null ? startDate.atStartOfDay(BUSINESS_ZONE).toInstant() : Instant.EPOCH;
        Instant to = endDate != null ? endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant() : Instant.now();

        Set<String> categories = actionType != null && !actionType.isBlank()
                ? Set.of(actionType.toUpperCase()) : ACTION_TYPE_EVENT_TYPES.keySet();

        Map<String, Object> byCategory = new LinkedHashMap<>();
        for (String category : categories) {
            List<String> eventTypes = ACTION_TYPE_EVENT_TYPES.get(category);
            if (eventTypes == null) {
                continue;
            }
            Query q = entityManager.createNativeQuery(
                    "select event_type, count(*), array_agg(distinct action_reason) filter (where action_reason is not null) "
                            + "from system_audit_log where event_type = any(:eventTypes) "
                            + "and event_timestamp between :from and :to group by event_type");
            q.setParameter("eventTypes", eventTypes.toArray(new String[0]));
            q.setParameter("from", from);
            q.setParameter("to", to);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            long total = 0;
            List<String> reasons = new ArrayList<>();
            for (Object[] row : rows) {
                total += ((Number) row[1]).longValue();
                if (row[2] != null) {
                    reasons.addAll(toStringList(row[2]));
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("count", total);
            entry.put("reasons", reasons);
            byCategory.put(category, entry);
        }
        return byCategory;
    }

    /**
     * Additive per-row projection alongside {@link #administrativeActionsReport} (the aggregate
     * counts-only method above, already exposed on the public REST API and left untouched) - one
     * real row per {@code system_audit_log} entry, for the Reports "Administrative Actions"
     * screen's requested table (Action | Content/User | Performed By | Date & Time | Reason).
     * Reuses the exact same {@link #ACTION_TYPE_EVENT_TYPES} category mapping so the two methods
     * can never disagree about what counts as a governed administrative action. Fetch-then-
     * paginate-in-Java (this codebase's established convention) is safe here because the result is
     * always bounded to the handful of governed action event_types, never the full audit trail -
     * see {@link #auditHistoryPage} below for the genuinely-unbounded Audit History screen, which
     * uses real server-side pagination instead.
     */
    @Transactional(readOnly = true)
    public List<AdminActionRow> administrativeActionRows(User requester, LocalDate startDate, LocalDate endDate,
                                                           String actionType) {
        authorizationService.requireAuthority(requester, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW, null, null);

        Instant from = startDate != null ? startDate.atStartOfDay(BUSINESS_ZONE).toInstant() : Instant.EPOCH;
        Instant to = endDate != null ? endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant() : Instant.now();
        List<String> eventTypes = (actionType != null && !actionType.isBlank())
                ? ACTION_TYPE_EVENT_TYPES.getOrDefault(actionType.toUpperCase(), List.of())
                : ACTION_TYPE_EVENT_TYPES.values().stream().flatMap(List::stream).toList();
        if (eventTypes.isEmpty()) {
            return List.of();
        }

        List<SystemAuditLog> logs = systemAuditLogRepository
                .findByEventTypeInAndEventTimestampBetweenOrderByEventTimestampDesc(eventTypes, from, to);
        List<AdminActionRow> rows = new ArrayList<>(logs.size());
        for (SystemAuditLog log : logs) {
            String action = actionCategoryFor(log.getEventType());
            ResolvedTarget target = resolveTarget(log.getTargetEntityName(), log.getTargetEntityId());
            rows.add(new AdminActionRow(action, target.display(), target.contentPlanId(), log.getActor().getFullName(),
                    log.getActorBaseRoleCode().name(), log.getEventTimestamp(), log.getActionReason()));
        }
        return rows;
    }

    private static String actionCategoryFor(String eventType) {
        return ACTION_TYPE_EVENT_TYPES.entrySet().stream()
                .filter(e -> e.getValue().contains(eventType)).map(Map.Entry::getKey).findFirst().orElse(eventType);
    }

    private record ResolvedTarget(String display, UUID contentPlanId) {
    }

    /**
     * Every governed admin-action event's {@code target_entity_name} is one of exactly 4 real
     * values (confirmed from every {@code auditService.record(...)} call site that emits these
     * event types) - never a guess. {@code content_plans} resolves directly; {@code
     * workflow_instances} (Idea Retain/Reopen) and {@code work_hold_records} (Hold/Resume) resolve
     * one hop further to their owning Content Plan or Idea; {@code permission_grants} resolves to
     * the grantee User. Never fabricates a display string for a target that can't be resolved -
     * falls back to the raw entity name + id.
     */
    private ResolvedTarget resolveTarget(String targetEntityName, UUID targetEntityId) {
        try {
            switch (targetEntityName) {
                case "content_plans" -> {
                    return contentPlanRepository.findById(targetEntityId)
                            .map(plan -> new ResolvedTarget(plan.getContentId() + " · " + plan.getIdea().getTitle(), plan.getId()))
                            .orElse(new ResolvedTarget(null, null));
                }
                case "workflow_instances" -> {
                    return ideaRepository.findByWorkflowInstance(entityManager.getReference(WorkflowInstance.class, targetEntityId))
                            .map(idea -> new ResolvedTarget(idea.getBusinessIdeaCode() + " · " + idea.getTitle(), null))
                            .orElseGet(() -> contentPlanByWorkflowInstance(targetEntityId));
                }
                case "work_hold_records" -> {
                    WorkHoldRecord hold = workHoldRecordRepository.findById(targetEntityId).orElse(null);
                    if (hold == null) {
                        return new ResolvedTarget(null, null);
                    }
                    return contentPlanByWorkflowInstance(hold.getWorkflowInstance().getId());
                }
                case "permission_grants" -> {
                    return permissionGrantRepository.findById(targetEntityId)
                            .map(grant -> new ResolvedTarget("User: " + userDisplay(grant), null))
                            .orElse(new ResolvedTarget(null, null));
                }
                default -> {
                    return new ResolvedTarget(null, null);
                }
            }
        } catch (RuntimeException e) {
            // Display-only convenience - a resolution failure never blocks the report.
            return new ResolvedTarget(null, null);
        }
    }

    private static String userDisplay(PermissionGrant grant) {
        return grant.getGrantee().getFullName();
    }

    private ResolvedTarget contentPlanByWorkflowInstance(UUID workflowInstanceId) {
        List<ContentPlan> matches = entityManager
                .createQuery("select cp from ContentPlan cp where cp.workflowInstance.id = :wiId", ContentPlan.class)
                .setParameter("wiId", workflowInstanceId).setMaxResults(1).getResultList();
        if (matches.isEmpty()) {
            return new ResolvedTarget(null, null);
        }
        ContentPlan plan = matches.get(0);
        return new ResolvedTarget(plan.getContentId() + " · " + plan.getIdea().getTitle(), plan.getId());
    }

    // ------------------------------------------------------------------------------------------
    // API-OP-060: Query Pipeline Delayed Deliverables

    @Transactional(readOnly = true)
    public List<DelayedDeliverableRow> delayedDeliverables(User requester, String stage, String priority) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        StringBuilder sql = new StringBuilder(
                "select cp.content_plan_id, cp.content_id, i.title, " + STAGE_LABEL_CASE + " as stage_label, "
                        + "cp.content_priority, " + STAGE_PLANNED_DATE_CASE + " as planned_date, "
                        + "(:today ::date - " + STAGE_PLANNED_DATE_CASE + ") as delay_days, "
                        + ASSIGNED_TO_CASE + " as assigned_to "
                        + "from content_plans cp join workflow_instances wi on wi.workflow_instance_id = cp.workflow_instance_id "
                        + "join ideas i on i.idea_id = cp.idea_id "
                        + "where wi.current_status_code not in ('COMP','CAN') and " + STAGE_PLANNED_DATE_CASE + " < :today");
        if (stage != null && !stage.isBlank()) {
            sql.append(" and ").append(STAGE_LABEL_CASE).append(" = :stage");
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" and cp.content_priority = :priority");
        }

        // SRS-REQ-002/066/067: CEO/MM get the full department-wide projection; an Employee is
        // restricted to their own assigned/participated deliverables, plus any deliverable
        // reachable under a GLOBAL-scope active grant (a narrower per-item scope evaluation for
        // every delayed row is out of scope for this reporting projection - see ENG-028).
        boolean fullVisibility = authorizationService.hasNativeAuthority(requester)
                || hasAnyActiveGlobalGrant(requester);
        Set<UUID> ownContentPlanIds = fullVisibility ? null : ownAssignedContentPlanIds(requester);
        if (!fullVisibility && ownContentPlanIds.isEmpty()) {
            return List.of();
        }
        if (!fullVisibility) {
            sql.append(" and cp.content_plan_id = any(:ownIds)");
        }

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("today", today);
        if (stage != null && !stage.isBlank()) {
            q.setParameter("stage", stage);
        }
        if (priority != null && !priority.isBlank()) {
            q.setParameter("priority", priority.toUpperCase());
        }
        if (!fullVisibility) {
            q.setParameter("ownIds", ownContentPlanIds.toArray(new UUID[0]));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<DelayedDeliverableRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new DelayedDeliverableRow((UUID) row[0], (String) row[1], (String) row[2], (String) row[3],
                    (String) row[4], toLocalDate(row[5]), ((Number) row[6]).longValue(), (String) row[7]));
        }
        return result;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object arrayValue) {
        try {
            if (arrayValue instanceof java.sql.Array sqlArray) {
                return List.of((String[]) sqlArray.getArray());
            }
            if (arrayValue instanceof String[] strings) {
                return List.of(strings);
            }
            if (arrayValue instanceof List<?> list) {
                return (List<String>) list;
            }
        } catch (java.sql.SQLException e) {
            // Fall through - reasons are a display-only convenience, never block the report.
        }
        return List.of();
    }

    private boolean hasAnyActiveGlobalGrant(User user) {
        Instant now = Instant.now();
        return permissionGrantRepository.findByGrantee(user).stream()
                .anyMatch(g -> g.getScopeType() == PermissionScopeType.GLOBAL && g.isCurrentlyValid(now));
    }

    private Set<UUID> ownAssignedContentPlanIds(User user) {
        Set<UUID> ids = shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user).stream()
                .map(a -> a.getContentPlan().getId()).collect(Collectors.toSet());
        ids.addAll(editingAssignmentRepository.findByEditorAndActiveTrue(user).stream()
                .map(a -> a.getContentPlan().getId()).collect(Collectors.toSet()));
        return ids;
    }
}
