package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContentPlanTalentEntryRepository extends JpaRepository<ContentPlanTalentEntry, UUID> {
    List<ContentPlanTalentEntry> findByContentPlan(ContentPlan contentPlan);

    void deleteByContentPlan(ContentPlan contentPlan);

    List<ContentPlanTalentEntry> findByContentPlan_IdIn(Collection<UUID> contentPlanIds);

    /** ENG-067: "My Shoots" (Model employee screen) - every talent entry actually linked to this User. */
    List<ContentPlanTalentEntry> findByTalentUser(User talentUser);
}
