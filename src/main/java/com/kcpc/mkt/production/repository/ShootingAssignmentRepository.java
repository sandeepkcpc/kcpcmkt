package com.kcpc.mkt.production.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShootingAssignmentRepository extends JpaRepository<ShootingAssignment, UUID> {
    List<ShootingAssignment> findByContentPlanAndActiveTrue(ContentPlan contentPlan);

    List<ShootingAssignment> findByContentPlan(ContentPlan contentPlan);

    List<ShootingAssignment> findByCamerapersonAndActiveTrue(User cameraperson);

    List<ShootingAssignment> findByContentPlan_IdInAndActiveTrue(Collection<UUID> contentPlanIds);
}
