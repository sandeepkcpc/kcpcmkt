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
    private final String contentId;

    public AuditLogResponse(UUID auditId, Instant eventTimestamp, UUID actorUserId, String actorFullName,
                             String actorBaseRoleCode, String eventCategory, String eventType,
                             String targetEntityName, UUID targetEntityId, String actionReason, String contentId) {
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
        this.contentId = contentId;
    }

    public static AuditLogResponse from(SystemAuditLog log) {
        return from(log, null);
    }

    /** Content ID column (Logs page enhancement): {@code contentId} is resolved by the caller
     * (see {@link com.kcpc.mkt.audit.service.AuditContentIdResolver}), never computed here - this
     * DTO stays a plain projection of what's already known. {@code null} for any log that has no
     * content relationship; the JSP renders that as "-". */
    public static AuditLogResponse from(SystemAuditLog log, String contentId) {
        return new AuditLogResponse(log.getId(), log.getEventTimestamp(), log.getActor().getId(),
                log.getActor().getFullName(), log.getActorBaseRoleCode().name(), log.getEventCategory(),
                log.getEventType(), log.getTargetEntityName(), log.getTargetEntityId(), log.getActionReason(),
                contentId);
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

    public String getContentId() {
        return contentId;
    }
}
