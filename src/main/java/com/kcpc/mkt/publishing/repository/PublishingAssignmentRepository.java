package com.kcpc.mkt.publishing.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import com.kcpc.mkt.reporting.dto.UserActiveTaskCount;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublishingAssignmentRepository extends JpaRepository<PublishingAssignment, UUID> {
    List<PublishingAssignment> findByContentPlanAndActiveTrue(ContentPlan contentPlan);

    Optional<PublishingAssignment> findByContentPlanAndPublisherAndActiveTrue(ContentPlan contentPlan, User publisher);

    List<PublishingAssignment> findByPublisherAndActiveTrue(User publisher);

    /** ENG-087: Team Workload's Assignee Load - every currently active Publishing assignment, batch-loaded once. */
    List<PublishingAssignment> findByActiveTrue();

    /** Assignee-picker workload display: one grouped COUNT query, no per-candidate lookup - see
     * AssigneeWorkloadCountService/AssigneeActiveWindows. */
    @Query("select a.publisher.id as userId, count(a) as activeCount from PublishingAssignment a "
            + "where a.active = true and a.contentPlan.workflowInstance.currentStatusCode in :activeWindow "
            + "group by a.publisher.id")
    List<UserActiveTaskCount> countActiveGroupedByPublisher(@Param("activeWindow") Collection<WorkflowStatus> activeWindow);
}
