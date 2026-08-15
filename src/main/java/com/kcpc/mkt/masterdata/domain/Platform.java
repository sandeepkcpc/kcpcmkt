package com.kcpc.mkt.masterdata.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** ERD-TBL-017: governed Platform master catalogue (6 seeds). */
@Entity
@Table(name = "platforms")
@AttributeOverride(name = "id", column = @Column(name = "platform_id"))
public class Platform extends BaseEntity {

    @Column(name = "platform_name", nullable = false, unique = true, length = 50)
    private String platformName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Platform() {
    }

    public Platform(String platformName) {
        this.platformName = platformName;
    }

    public void deactivate() {
        this.active = false;
        this.deactivatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.deactivatedAt = null;
    }

    public void rename(String platformName) {
        this.platformName = platformName;
    }

    public String getPlatformName() {
        return platformName;
    }

    public boolean isActive() {
        return active;
    }
}
