package com.kcpc.mkt.marks.repository;

import com.kcpc.mkt.marks.domain.PredefinedRoleMarks;
import com.kcpc.mkt.planning.domain.ContentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PredefinedRoleMarksRepository extends JpaRepository<PredefinedRoleMarks, UUID> {
    Optional<PredefinedRoleMarks> findByContentPlan(ContentPlan contentPlan);
}
