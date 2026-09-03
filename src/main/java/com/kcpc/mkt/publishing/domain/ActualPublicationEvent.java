package com.kcpc.mkt.publishing.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
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

/** ERD-TBL-021: immutable actual publication event record. One Content ID may have many. */
@Entity
@Table(name = "actual_publication_events")
@AttributeOverride(name = "id", column = @Column(name = "event_id"))
public class ActualPublicationEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "planned_output_id", nullable = false)
    private PlannedOutput plannedOutput;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "publication_target_id", nullable = false)
    private PublicationTarget publicationTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private PublicationEventType eventType;

    @Column(name = "actual_publication_timestamp", nullable = false)
    private Instant actualPublicationTimestamp;

    @Column(name = "evidence_url", nullable = false, columnDefinition = "text")
    private String evidenceUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_by_user_id", nullable = false)
    private User publishedBy;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ActualPublicationEvent() {
    }

    public ActualPublicationEvent(ContentPlan contentPlan, PlannedOutput plannedOutput, PublicationTarget publicationTarget,
                                   PublicationEventType eventType, Instant actualPublicationTimestamp,
                                   String evidenceUrl, User publishedBy) {
        this.contentPlan = contentPlan;
        this.plannedOutput = plannedOutput;
        this.publicationTarget = publicationTarget;
        this.eventType = eventType;
        this.actualPublicationTimestamp = actualPublicationTimestamp;
        this.evidenceUrl = evidenceUrl;
        this.publishedBy = publishedBy;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public PlannedOutput getPlannedOutput() {
        return plannedOutput;
    }

    public PublicationTarget getPublicationTarget() {
        return publicationTarget;
    }

    public PublicationEventType getEventType() {
        return eventType;
    }

    public Instant getActualPublicationTimestamp() {
        return actualPublicationTimestamp;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public User getPublishedBy() {
        return publishedBy;
    }

    /**
     * System-assigned insertion time - immutable and never user-suppliable (unlike
     * {@link #getActualPublicationTimestamp()}, which the recorder freely enters). Used to
     * determine which Publishing cycle an event belongs to (a repost cycle only counts events
     * recorded on-or-after that cycle's own Reopen record), since the business timestamp could in
     * principle be backdated.
     */
    public Instant getRecordedAt() {
        return recordedAt;
    }
}
