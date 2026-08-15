package com.kcpc.mkt.identity.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * ERD-TBL-002 user_sessions: the JWT token registry (R3.5 / SAD-ADR-001). One row per issued
 * JWT, keyed by SHA-256(jti). Never stores the raw JWT or raw jti. Rows are revoked, never
 * physically deleted.
 */
@Entity
@Table(name = "user_sessions")
@AttributeOverride(name = "id", column = @Column(name = "session_id"))
public class UserSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "session_token_hash", nullable = false, unique = true, length = 64)
    private String sessionTokenHash;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked = false;

    protected UserSession() {
    }

    public UserSession(User user, String sessionTokenHash, String ipAddress, String userAgent, Instant expiresAt) {
        this.user = user;
        this.sessionTokenHash = sessionTokenHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
    }

    /** Logout / deactivation: revoke, never delete (KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md §"Security rules"). */
    public void revoke() {
        this.revoked = true;
    }

    public boolean isUsable(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    public User getUser() {
        return user;
    }

    public String getSessionTokenHash() {
        return sessionTokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
