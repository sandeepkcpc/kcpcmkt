package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.identity.domain.PermissionScopeType;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.reporting.dto.DelayedDeliverableRow;
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

    private static final String STAGE_PLANNED_DATE_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV','PLAP','SA','SIP','SRV') THEN cp.planned_shoot_date "
                    + "WHEN wi.current_status_code IN ('SAP','EA','ED','ERV') THEN cp.planned_edit_date "
                    + "WHEN wi.current_status_code IN ('EAP','RFP','PUBG','PP','PFUP') THEN cp.planned_live_date "
                    + "ELSE NULL END";
    private static final String STAGE_LABEL_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV','PLAP','SA','SIP','SRV') THEN 'Shoot' "
                    + "WHEN wi.current_status_code IN ('SAP','EA','ED','ERV') THEN 'Edit' "
                    + "WHEN wi.current_status_code IN ('EAP','RFP','PUBG','PP','PFUP') THEN 'Publish' "
                    + "ELSE 'Other' END";

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

    public AdminReportingService(AuthorizationService authorizationService, PermissionGrantRepository permissionGrantRepository,
                                  ShootingAssignmentRepository shootingAssignmentRepository,
                                  EditingAssignmentRepository editingAssignmentRepository) {
        this.authorizationService = authorizationService;
        this.permissionGrantRepository = permissionGrantRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
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

    // ------------------------------------------------------------------------------------------
    // API-OP-060: Query Pipeline Delayed Deliverables

    @Transactional(readOnly = true)
    public List<DelayedDeliverableRow> delayedDeliverables(User requester, String stage, String priority) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        StringBuilder sql = new StringBuilder(
                "select cp.content_plan_id, cp.content_id, " + STAGE_LABEL_CASE + " as stage_label, "
                        + "cp.content_priority, " + STAGE_PLANNED_DATE_CASE + " as planned_date, "
                        + "(:today ::date - " + STAGE_PLANNED_DATE_CASE + ") as delay_days "
                        + "from content_plans cp join workflow_instances wi on wi.workflow_instance_id = cp.workflow_instance_id "
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
                    toLocalDate(row[4]), ((Number) row[5]).longValue()));
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
