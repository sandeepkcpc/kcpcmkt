package com.kcpc.mkt.identity.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

/**
 * ERD-TBL-001 users. References exactly one {@link BusinessRole}; the resolved
 * {@link AccessClass} is {@code businessRole.accessClassCode} (BRS-REQ-001/002).
 */
@Entity
@Table(name = "users")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // EAGER: the resolved access class is read on essentially every authenticated request
    // (JwtAuthenticationFilter builds KcpcUserPrincipal outside any open Hibernate session by
    // the time the controller runs, since open-in-view is disabled) - avoids
    // LazyInitializationException without needing OSIV.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "business_role_id", nullable = false)
    private BusinessRole businessRole;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String fullName, String email, String passwordHash, BusinessRole businessRole) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.businessRole = businessRole;
    }

    public AccessClass resolvedAccessClass() {
        return businessRole.getAccessClassCode();
    }

    /** BRS-REQ-003 / SRS-REQ-005: soft deactivation, immediate effect, history preserved. */
    public void deactivate() {
        this.active = false;
        this.deactivatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.deactivatedAt = null;
    }

    public void reassignBusinessRole(BusinessRole newRole) {
        this.businessRole = newRole;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    /** Never serialized - a credential hash must never leave the server in any API response. */
    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public BusinessRole getBusinessRole() {
        return businessRole;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
