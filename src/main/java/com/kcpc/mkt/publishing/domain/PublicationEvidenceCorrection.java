package com.kcpc.mkt.publishing.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** ERD-TBL-027: append-only Publication Evidence URL correction history under Permission #8 (SAD-DES-022). */
@Entity
@Table(name = "publication_evidence_corrections")
@AttributeOverride(name = "id", column = @Column(name = "correction_id"))
public class PublicationEvidenceCorrection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private ActualPublicationEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_correction_id")
    private PublicationEvidenceCorrection supersedesCorrection;

    @Column(name = "prior_evidence_url", nullable = false, columnDefinition = "text")
    private String priorEvidenceUrl;

    @Column(name = "corrected_evidence_url", nullable = false, columnDefinition = "text")
    private String correctedEvidenceUrl;

    @Column(name = "mandatory_reason", nullable = false, columnDefinition = "text")
    private String mandatoryReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by_user_id", nullable = false)
    private User correctedBy;

    @CreationTimestamp
    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected PublicationEvidenceCorrection() {
    }

    public PublicationEvidenceCorrection(ActualPublicationEvent event, PublicationEvidenceCorrection supersedesCorrection,
                                          String priorEvidenceUrl, String correctedEvidenceUrl, String mandatoryReason,
                                          User correctedBy, PermissionGrant actingPermissionGrant) {
        this.event = event;
        this.supersedesCorrection = supersedesCorrection;
        this.priorEvidenceUrl = priorEvidenceUrl;
        this.correctedEvidenceUrl = correctedEvidenceUrl;
        this.mandatoryReason = mandatoryReason;
        this.correctedBy = correctedBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }

    public ActualPublicationEvent getEvent() {
        return event;
    }

    public PublicationEvidenceCorrection getSupersedesCorrection() {
        return supersedesCorrection;
    }

    public String getPriorEvidenceUrl() {
        return priorEvidenceUrl;
    }

    public String getCorrectedEvidenceUrl() {
        return correctedEvidenceUrl;
    }

    public String getMandatoryReason() {
        return mandatoryReason;
    }

    public User getCorrectedBy() {
        return correctedBy;
    }

    public Instant getCorrectedAt() {
        return correctedAt;
    }
}
