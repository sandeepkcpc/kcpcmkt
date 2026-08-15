package com.kcpc.mkt.planning.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** ERD-TBL-020: intended publication scope mapping per Planned Output (BRS-REQ-025). */
@Entity
@Table(name = "planned_output_publication_target_mappings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"planned_output_id", "publication_target_id"}))
@AttributeOverride(name = "id", column = @Column(name = "mapping_id"))
public class PlannedOutputPublicationTargetMapping extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planned_output_id", nullable = false)
    private PlannedOutput plannedOutput;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "publication_target_id", nullable = false)
    private PublicationTarget publicationTarget;

    protected PlannedOutputPublicationTargetMapping() {
    }

    public PlannedOutputPublicationTargetMapping(PlannedOutput plannedOutput, PublicationTarget publicationTarget) {
        this.plannedOutput = plannedOutput;
        this.publicationTarget = publicationTarget;
    }

    public PlannedOutput getPlannedOutput() {
        return plannedOutput;
    }

    public PublicationTarget getPublicationTarget() {
        return publicationTarget;
    }
}
