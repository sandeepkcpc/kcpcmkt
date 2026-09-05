package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.reporting.dto.UserContentPlanRef;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContentPlanTalentEntryRepository extends JpaRepository<ContentPlanTalentEntry, UUID> {
    List<ContentPlanTalentEntry> findByContentPlan(ContentPlan contentPlan);

    void deleteByContentPlan(ContentPlan contentPlan);

    List<ContentPlanTalentEntry> findByContentPlan_IdIn(Collection<UUID> contentPlanIds);

    /** ENG-067: "My Shoots" (Model employee screen) - every talent entry actually linked to this User. */
    List<ContentPlanTalentEntry> findByTalentUser(User talentUser);

    /** Model(s)-picker workload display. Gated by the SHOOT window - NOT the broad "not yet closed
     * out" set - because a Model's work is tied to the Shoot stage, exactly as
     * TeamWorkloadService#modelRow already gates it (see AssigneeActiveWindows.CLOSED_OUT's javadoc). */
    @Query("select e.talentUser.id as userId, e.contentPlan.id as contentPlanId from ContentPlanTalentEntry e "
            + "where e.talentUser is not null and e.contentPlan.workflowInstance.currentStatusCode in :activeWindow")
    List<UserContentPlanRef> findActiveContentPlanRefsByTalentUser(@Param("activeWindow") Collection<WorkflowStatus> activeWindow);
}
