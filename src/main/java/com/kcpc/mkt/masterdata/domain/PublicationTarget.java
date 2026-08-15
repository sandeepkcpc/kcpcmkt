package com.kcpc.mkt.masterdata.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** ERD-TBL-019: configurable Platform &lt;-&gt; Company Channel destination. */
@Entity
@Table(name = "publication_targets")
@AttributeOverride(name = "id", column = @Column(name = "publication_target_id"))
public class PublicationTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private CompanyChannel channel;

    @Column(name = "target_name", nullable = false, length = 150)
    private String targetName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected PublicationTarget() {
    }

    public PublicationTarget(Platform platform, CompanyChannel channel, String targetName) {
        this.platform = platform;
        this.channel = channel;
        this.targetName = targetName;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public Platform getPlatform() {
        return platform;
    }

    public CompanyChannel getChannel() {
        return channel;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isActive() {
        return active;
    }
}
