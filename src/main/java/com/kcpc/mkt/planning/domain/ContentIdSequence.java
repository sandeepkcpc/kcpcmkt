package com.kcpc.mkt.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** ERD-TBL-042: concurrency-safe monthly Content ID sequence (C-MMYY-NNNN), keyed by IST business month. */
@Entity
@Table(name = "content_id_sequences")
public class ContentIdSequence {

    @Id
    @Column(name = "business_month_mmyy", length = 4)
    private String businessMonthMmyy;

    @Column(name = "last_sequence_number", nullable = false)
    private int lastSequenceNumber;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContentIdSequence() {
    }

    public ContentIdSequence(String businessMonthMmyy, int lastSequenceNumber) {
        this.businessMonthMmyy = businessMonthMmyy;
        this.lastSequenceNumber = lastSequenceNumber;
    }

    public int nextSequence() {
        this.lastSequenceNumber += 1;
        return this.lastSequenceNumber;
    }

    public String getBusinessMonthMmyy() {
        return businessMonthMmyy;
    }

    public int getLastSequenceNumber() {
        return lastSequenceNumber;
    }
}
