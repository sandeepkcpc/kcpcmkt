package com.kcpc.mkt.audit.repository;

import com.kcpc.mkt.audit.domain.SystemAuditLog;
import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SystemAuditLogRepository extends InsertOnlyRepository<SystemAuditLog, UUID> {
    List<SystemAuditLog> findAllByOrderByEventTimestampDesc();

    /** Admin Actions report: bounded to a handful of governed action-type event_types (never the
     * full audit trail), so fetch-then-paginate-in-Java (this codebase's established pagination
     * convention) is safe here - unlike Audit History below, which is genuinely unbounded. */
    List<SystemAuditLog> findByEventTypeInAndEventTimestampBetweenOrderByEventTimestampDesc(
            List<String> eventTypes, Instant from, Instant to);

    /** Audit History: may contain thousands of rows, so this is real server-side pagination - only
     * one page is ever loaded into the JVM/browser, never the whole table. Every filter is
     * optional (null = "no filter on this dimension"); {@code targetEntityId}/{@code eventType}/
     * {@code eventCategory} are only ever real backend-supported values already stored on
     * SystemAuditLog rows, never invented ones. */
    @Query("select a from SystemAuditLog a where "
            + "(:actorId is null or a.actor.id = :actorId) "
            + "and (:eventType is null or a.eventType = :eventType) "
            + "and (:targetEntityId is null or a.targetEntityId = :targetEntityId) "
            + "and (:eventCategory is null or a.eventCategory = :eventCategory) "
            + "and a.eventTimestamp between :from and :to")
    Page<SystemAuditLog> search(@Param("actorId") UUID actorId, @Param("eventType") String eventType,
                                 @Param("targetEntityId") UUID targetEntityId, @Param("eventCategory") String eventCategory,
                                 @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /** Batched "grant reason" lookup for the User Detail Current Granted Permissions table - one
     * PERMISSION_GRANTED audit row per grant id (grant ids are never reused, so this is always
     * exactly 0 or 1 row per id; no need for a "latest wins" reduction). */
    List<SystemAuditLog> findByTargetEntityIdInAndEventType(java.util.Collection<UUID> targetEntityIds, String eventType);

    /** Batched "current reason" lookup for the unified Permission Management table's Reason column
     * (permission-admin-ui final redesign): a grant's displayed reason must reflect its MOST
     * RECENT PERMISSION_GRANTED or PERMISSION_MODIFIED audit row (an inline Update that only
     * changes Expiry/Reason is audited as PERMISSION_MODIFIED, never PERMISSION_GRANTED again -
     * looking up only the grant event would silently keep showing the original reason forever).
     * Callers reduce this to "latest per grant id" themselves (by eventTimestamp). */
    List<SystemAuditLog> findByTargetEntityIdInAndEventTypeIn(java.util.Collection<UUID> targetEntityIds,
                                                                java.util.Collection<String> eventTypes);

    /** Distinct actual eventType/eventCategory values currently stored, for the Audit History
     * filter dropdowns - never a hardcoded/guessed vocabulary list. */
    @Query("select distinct a.eventType from SystemAuditLog a order by a.eventType")
    List<String> findDistinctEventTypes();

    @Query("select distinct a.eventCategory from SystemAuditLog a order by a.eventCategory")
    List<String> findDistinctEventCategories();
}
