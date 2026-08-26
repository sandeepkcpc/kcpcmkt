package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.reporting.dto.UserActiveTaskCount;
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

    /** Model(s)-picker workload display: one grouped COUNT query, no per-candidate lookup - "active"
     * here mirrors TeamWorkloadService.isActiveStatus (see AssigneeActiveWindows.CLOSED_OUT): still
     * linked to a not-yet-closed-out Content Plan, never a fabricated Model execution task. */
    @Query("select e.talentUser.id as userId, count(e) as activeCount from ContentPlanTalentEntry e "
            + "where e.talentUser is not null and e.contentPlan.workflowInstance.currentStatusCode not in :closedOut "
            + "group by e.talentUser.id")
    List<UserActiveTaskCount> countActiveGroupedByTalentUser(@Param("closedOut") Collection<WorkflowStatus> closedOut);
}
