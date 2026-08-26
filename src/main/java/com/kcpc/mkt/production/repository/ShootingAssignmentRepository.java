package com.kcpc.mkt.production.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.reporting.dto.UserActiveTaskCount;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShootingAssignmentRepository extends JpaRepository<ShootingAssignment, UUID> {
    List<ShootingAssignment> findByContentPlanAndActiveTrue(ContentPlan contentPlan);

    List<ShootingAssignment> findByContentPlan(ContentPlan contentPlan);

    List<ShootingAssignment> findByCamerapersonAndActiveTrue(User cameraperson);

    /** ENG-087: Team Workload's Assignee Load - every currently active Shoot assignment, batch-loaded once. */
    List<ShootingAssignment> findByActiveTrue();

    List<ShootingAssignment> findByContentPlan_IdInAndActiveTrue(Collection<UUID> contentPlanIds);

    Optional<ShootingAssignment> findByContentPlanAndCamerapersonAndActiveTrue(ContentPlan contentPlan, User cameraperson);

    /** Assignee-picker workload display: one grouped COUNT query, no per-candidate lookup - see
     * AssigneeWorkloadCountService/AssigneeActiveWindows. */
    @Query("select a.cameraperson.id as userId, count(a) as activeCount from ShootingAssignment a "
            + "where a.active = true and a.contentPlan.workflowInstance.currentStatusCode in :activeWindow "
            + "group by a.cameraperson.id")
    List<UserActiveTaskCount> countActiveGroupedByCameraperson(@Param("activeWindow") Collection<WorkflowStatus> activeWindow);
}
