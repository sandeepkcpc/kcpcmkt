package com.kcpc.mkt.production.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.domain.EditingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EditingAssignmentRepository extends JpaRepository<EditingAssignment, UUID> {
    List<EditingAssignment> findByContentPlanAndActiveTrue(ContentPlan contentPlan);

    List<EditingAssignment> findByContentPlan(ContentPlan contentPlan);

    List<EditingAssignment> findByEditorAndActiveTrue(User editor);

    List<EditingAssignment> findByContentPlan_IdInAndActiveTrue(Collection<UUID> contentPlanIds);
}
