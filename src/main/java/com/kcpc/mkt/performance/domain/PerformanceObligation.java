package com.kcpc.mkt.performance.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** ERD-TBL-023: 1:1 per Actual Publication Event. Due date is immutable and non-reschedulable (ERD-CON-048). */
@Entity
@Table(name = "performance_obligations")
@AttributeOverride(name = "id", column = @Column(name = "obligation_id"))
public class PerformanceObligation extends BaseEntity {

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private ActualPublicationEvent event;

    @Column(name = "performance_due_date", nullable = false)
    private LocalDate performanceDueDate;

    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PerformanceObligation() {
    }

    public PerformanceObligation(ActualPublicationEvent event) {
        this.event = event;
        // ERD-CON-016: performance_due_date MUST equal actual_publication_date + 2 days.
        this.performanceDueDate = event.getActualPublicationTimestamp().atZone(java.time.ZoneId.of("Asia/Kolkata"))
                .toLocalDate().plusDays(2);
    }

    public void markCompleted() {
        this.completed = true;
    }

    public ActualPublicationEvent getEvent() {
        return event;
    }

    public LocalDate getPerformanceDueDate() {
        return performanceDueDate;
    }

    public boolean isCompleted() {
        return completed;
    }
}
