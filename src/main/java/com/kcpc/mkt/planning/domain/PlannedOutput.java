package com.kcpc.mkt.planning.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.common.util.UuidV7;
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
import java.util.UUID;

/** ERD-TBL-011: 1..N Planned Outputs under a single Content ID. */
@Entity
@Table(name = "planned_outputs")
@AttributeOverride(name = "id", column = @Column(name = "planned_output_id"))
public class PlannedOutput extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_type", nullable = false, length = 30)
    private OutputType outputType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reel_type", length = 20)
    private ReelType reelType;

    @Column(name = "title_description", length = 200)
    private String titleDescription;

    /**
     * Groups Planned Outputs that were created together as one REEL "+ Add Output" submission
     * (one row per selected Reel Type - VERY_SHORT/SHORT/LONG - all sharing this id) so they can
     * be displayed as a single row and made to share one common Publication Target set. A Planned
     * Output created alone (including every non-REEL output) is simply a "group of one": this
     * defaults to a fresh id of its own unless a caller explicitly shares one across a batch via
     * {@link #setReelGroupId}.
     */
    @Column(name = "reel_group_id", nullable = false)
    private UUID reelGroupId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlannedOutput() {
    }

    public PlannedOutput(ContentPlan contentPlan, OutputType outputType, ReelType reelType, String titleDescription) {
        this.contentPlan = contentPlan;
        this.titleDescription = titleDescription;
        this.reelGroupId = UuidV7.generate();
        setTypeAndReelType(outputType, reelType);
    }

    /** ERD-CON-008/054: Reel Type mandatory only for REEL, must be NULL otherwise. */
    public final void setTypeAndReelType(OutputType outputType, ReelType reelType) {
        if (outputType == OutputType.REEL && reelType == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Reel Type is mandatory when output type is Reel (ERD-CON-008/AC-024.1)");
        }
        if (outputType != OutputType.REEL && reelType != null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Reel Type must be blank for non-Reel output types (ERD-CON-008)");
        }
        this.outputType = outputType;
        this.reelType = reelType;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public ReelType getReelType() {
        return reelType;
    }

    public String getTitleDescription() {
        return titleDescription;
    }

    public void setTitleDescription(String titleDescription) {
        this.titleDescription = titleDescription;
    }

    public UUID getReelGroupId() {
        return reelGroupId;
    }

    public void setReelGroupId(UUID reelGroupId) {
        this.reelGroupId = reelGroupId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
