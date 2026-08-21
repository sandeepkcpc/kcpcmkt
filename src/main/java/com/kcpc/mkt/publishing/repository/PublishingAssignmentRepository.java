package com.kcpc.mkt.publishing.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublishingAssignmentRepository extends JpaRepository<PublishingAssignment, UUID> {
    List<PublishingAssignment> findByContentPlanAndActiveTrue(ContentPlan contentPlan);

    Optional<PublishingAssignment> findByContentPlanAndPublisherAndActiveTrue(ContentPlan contentPlan, User publisher);

    List<PublishingAssignment> findByPublisherAndActiveTrue(User publisher);

    /** ENG-087: Team Workload's Assignee Load - every currently active Publishing assignment, batch-loaded once. */
    List<PublishingAssignment> findByActiveTrue();
}
