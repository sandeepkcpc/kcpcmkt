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
 * Self-service "Forgot Password" token registry - mirrors {@link UserSession}'s own pattern: one
 * row per issued reset token, keyed by SHA-256(raw token). The raw token is never persisted, only
 * ever held transiently in memory long enough to email/log it and to validate a later presented
 * value against this hash. Single-use ({@link #markUsed()} is a one-way transition) and time-
 * limited ({@link #isUsable(Instant)}), both checked in {@code PasswordResetService}, never here.
 */
@Entity
@Table(name = "password_reset_tokens")
@AttributeOverride(name = "id", column = @Column(name = "reset_token_id"))
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "requested_ip")
    private String requestedIp;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt, String requestedIp) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.requestedIp = requestedIp;
    }

    /** One-way: a used token can never be un-used. */
    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public String getRequestedIp() {
        return requestedIp;
    }
}
