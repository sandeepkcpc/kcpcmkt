package com.kcpc.mkt.audit.dto;

import com.kcpc.mkt.audit.domain.SystemAuditLog;

import java.time.Instant;
import java.util.UUID;

/**
 * API-OP-061: a deliberately narrow projection - never the raw User entity graph.
 *
 * <p>Plain class with {@code getX()} accessors, not a {@code record}: read directly by JSP EL
 * (the Audit-History Viewer screen) - see {@link com.kcpc.mkt.reporting.dto.KpiValue}'s class doc
 * for why a record's no-prefix accessors break classic JSP EL property resolution (ENG-031).
 */
public class AuditLogResponse {

    private final UUID auditId;
    private final Instant eventTimestamp;
    private final UUID actorUserId;
    private final String actorFullName;
    private final String actorBaseRoleCode;
    private final String eventCategory;
    private final String eventType;
    private final String targetEntityName;
    private final UUID targetEntityId;
    private final String actionReason;

    public AuditLogResponse(UUID auditId, Instant eventTimestamp, UUID actorUserId, String actorFullName,
                             String actorBaseRoleCode, String eventCategory, String eventType,
                             String targetEntityName, UUID targetEntityId, String actionReason) {
        this.auditId = auditId;
        this.eventTimestamp = eventTimestamp;
        this.actorUserId = actorUserId;
        this.actorFullName = actorFullName;
        this.actorBaseRoleCode = actorBaseRoleCode;
        this.eventCategory = eventCategory;
        this.eventType = eventType;
        this.targetEntityName = targetEntityName;
        this.targetEntityId = targetEntityId;
        this.actionReason = actionReason;
    }

    public static AuditLogResponse from(SystemAuditLog log) {
        return new AuditLogResponse(log.getId(), log.getEventTimestamp(), log.getActor().getId(),
                log.getActor().getFullName(), log.getActorBaseRoleCode().name(), log.getEventCategory(),
                log.getEventType(), log.getTargetEntityName(), log.getTargetEntityId(), log.getActionReason());
    }

    public UUID getAuditId() {
        return auditId;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorFullName() {
        return actorFullName;
    }

    public String getActorBaseRoleCode() {
        return actorBaseRoleCode;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTargetEntityName() {
        return targetEntityName;
    }

    public UUID getTargetEntityId() {
        return targetEntityId;
    }

    public String getActionReason() {
        return actionReason;
    }
}
