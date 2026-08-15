package com.kcpc.mkt.planning.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** ERD-TBL-041: multi-selection Models/Talent entries per Content Plan. */
@Entity
@Table(name = "content_plan_talent_entries")
@AttributeOverride(name = "id", column = @Column(name = "entry_id"))
public class ContentPlanTalentEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @Column(name = "talent_name", nullable = false, length = 100)
    private String talentName;

    protected ContentPlanTalentEntry() {
    }

    public ContentPlanTalentEntry(ContentPlan contentPlan, String talentName) {
        this.contentPlan = contentPlan;
        this.talentName = talentName;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public String getTalentName() {
        return talentName;
    }
}
