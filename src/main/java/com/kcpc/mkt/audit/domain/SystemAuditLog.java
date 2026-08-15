package com.kcpc.mkt.audit.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * ERD-TBL-025 system_audit_log: system-wide append-only audit trail (SAD-DES-006).
 * Insert-only at the repository layer; see docs/IMPLEMENTATION_DECISIONS.md "DB-001" for the
 * interim (pre-Phase-14) enforcement posture.
 */
@Entity
@Table(name = "system_audit_log")
@AttributeOverride(name = "id", column = @Column(name = "audit_id"))
public class SystemAuditLog extends BaseEntity {

    @CreationTimestamp
    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp;

    // EAGER: the Audit-History Viewer and Admin-Action Report read this directly in MVC/JSP views
    // outside any open transaction (open-in-view is disabled) - see ENG-005/ENG-024 precedent.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_base_role_code", nullable = false, length = 30)
    private AccessClass actorBaseRoleCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    @Column(name = "event_category", nullable = false, length = 50)
    private String eventCategory;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "target_entity_name", nullable = false, length = 50)
    private String targetEntityName;

    @Column(name = "target_entity_id", nullable = false)
    private java.util.UUID targetEntityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state_snapshot")
    private String previousStateSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state_snapshot")
    private String newStateSnapshot;

    @Column(name = "action_reason", columnDefinition = "text")
    private String actionReason;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    protected SystemAuditLog() {
    }

    public SystemAuditLog(User actor, AccessClass actorBaseRoleCode, PermissionGrant actingPermissionGrant,
                           String eventCategory, String eventType, String targetEntityName,
                           java.util.UUID targetEntityId, String previousStateSnapshot, String newStateSnapshot,
                           String actionReason, String ipAddress) {
        this.actor = actor;
        this.actorBaseRoleCode = actorBaseRoleCode;
        this.actingPermissionGrant = actingPermissionGrant;
        this.eventCategory = eventCategory;
        this.eventType = eventType;
        this.targetEntityName = targetEntityName;
        this.targetEntityId = targetEntityId;
        this.previousStateSnapshot = previousStateSnapshot;
        this.newStateSnapshot = newStateSnapshot;
        this.actionReason = actionReason;
        this.ipAddress = ipAddress;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public User getActor() {
        return actor;
    }

    public AccessClass getActorBaseRoleCode() {
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

    public java.util.UUID getTargetEntityId() {
        return targetEntityId;
    }

    public String getActionReason() {
        return actionReason;
    }
}
