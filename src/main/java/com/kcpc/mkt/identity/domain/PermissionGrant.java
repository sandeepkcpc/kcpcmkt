package com.kcpc.mkt.identity.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
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

import java.time.Instant;

// NOTE: OperationalPermission uses OperationalPermissionConverter (autoApply), NOT @Enumerated -
// the permission_number column is INTEGER, and the enum's number() (not ordinal/name) is the DB value.

/**
 * ERD-TBL-005 permission_grants: CEO-granted permission assignment with temporal + scope
 * bounds. Modify/expire/revoke is soft (ERD-CON-042: revoked_at IS NOT NULL invalidates for
 * authorization, but the row is never deleted) - API-OP-065.
 */
@Entity
@Table(name = "permission_grants")
@AttributeOverride(name = "id", column = @Column(name = "grant_id"))
public class PermissionGrant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grantee_user_id", nullable = false)
    private User grantee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grantor_user_id", nullable = false)
    private User grantor;

    @Column(name = "permission_number", nullable = false)
    private OperationalPermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PermissionScopeType scopeType;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PermissionGrant() {
    }

    public PermissionGrant(User grantee, User grantor, OperationalPermission permission,
                            PermissionScopeType scopeType, Instant effectiveFrom, Instant effectiveUntil) {
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effective_until must be null or after effective_from (ERD-CON-025)");
        }
        this.grantee = grantee;
        this.grantor = grantor;
        this.permission = permission;
        this.scopeType = scopeType;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
    }

    public void revoke(Instant when) {
        this.active = false;
        this.revokedAt = when;
    }

    public void modifyExpiry(Instant newEffectiveUntil) {
        if (newEffectiveUntil != null && !newEffectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effective_until must be null or after effective_from (ERD-CON-025)");
        }
        this.effectiveUntil = newEffectiveUntil;
    }

    /** SRS-REQ-009 / ERD-CON-042: real-time validity check, independent of scope-context evaluation. */
    public boolean isCurrentlyValid(Instant now) {
        if (!active || revokedAt != null) {
            return false;
        }
        if (now.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveUntil == null || now.isBefore(effectiveUntil);
    }

    public User getGrantee() {
        return grantee;
    }

    public User getGrantor() {
        return grantor;
    }

    public OperationalPermission getPermission() {
        return permission;
    }

    public PermissionScopeType getScopeType() {
        return scopeType;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveUntil() {
        return effectiveUntil;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
