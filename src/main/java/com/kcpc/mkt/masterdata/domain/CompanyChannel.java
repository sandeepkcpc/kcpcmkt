package com.kcpc.mkt.masterdata.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** ERD-TBL-018: governed Company Channel / Account master catalogue (8 seeds). */
@Entity
@Table(name = "company_channels")
@AttributeOverride(name = "id", column = @Column(name = "channel_id"))
public class CompanyChannel extends BaseEntity {

    @Column(name = "channel_handle", nullable = false, unique = true, length = 100)
    private String channelHandle;

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

    protected CompanyChannel() {
    }

    public CompanyChannel(String channelHandle) {
        this.channelHandle = channelHandle;
    }

    public void deactivate() {
        this.active = false;
        this.deactivatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.deactivatedAt = null;
    }

    public void rename(String channelHandle) {
        this.channelHandle = channelHandle;
    }

    public String getChannelHandle() {
        return channelHandle;
    }

    public boolean isActive() {
        return active;
    }
}
