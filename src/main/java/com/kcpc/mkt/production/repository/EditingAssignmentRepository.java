package com.kcpc.mkt.production.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.reporting.dto.UserContentPlanRef;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EditingAssignmentRepository extends JpaRepository<EditingAssignment, UUID> {
    List<EditingAssignment> findByContentPlanAndActiveTrue(ContentPlan contentPlan);

    List<EditingAssignment> findByContentPlan(ContentPlan contentPlan);

    List<EditingAssignment> findByEditorAndActiveTrue(User editor);

    /** ENG-087: Team Workload's Assignee Load - every currently active Edit assignment, batch-loaded once. */
    List<EditingAssignment> findByActiveTrue();

    List<EditingAssignment> findByContentPlan_IdInAndActiveTrue(Collection<UUID> contentPlanIds);

    Optional<EditingAssignment> findByContentPlanAndEditorAndActiveTrue(ContentPlan contentPlan, User editor);

    /** Assignee-picker workload display - see ShootingAssignmentRepository's equivalent. */
    @Query("select a.editor.id as userId, a.contentPlan.id as contentPlanId from EditingAssignment a "
            + "where a.active = true and a.contentPlan.workflowInstance.currentStatusCode in :activeWindow")
    List<UserContentPlanRef> findActiveContentPlanRefsByEditor(@Param("activeWindow") Collection<WorkflowStatus> activeWindow);
}
