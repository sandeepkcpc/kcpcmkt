package com.kcpc.mkt.production.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
