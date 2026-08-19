package com.kcpc.mkt.planning.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
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

    /**
     * ENG-067: nullable - the frozen REST contract (API-OP-017/018) still accepts plain talent
     * name strings with no user behind them, but every entry created through the MVC Model(s)
     * picker (which only ever offers real Model-Business-Role users) populates this, giving "My
     * Shoots" a reliable, backend-enforced way to find a Model's own shoots instead of matching on
     * the free-text name.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "talent_user_id")
    private User talentUser;

    protected ContentPlanTalentEntry() {
    }

    public ContentPlanTalentEntry(ContentPlan contentPlan, String talentName, User talentUser) {
        this.contentPlan = contentPlan;
        this.talentName = talentName;
        this.talentUser = talentUser;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public String getTalentName() {
        return talentName;
    }

    public User getTalentUser() {
        return talentUser;
    }
}
