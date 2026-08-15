package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlanningPreparer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanningPreparerRepository extends JpaRepository<PlanningPreparer, UUID> {
    List<PlanningPreparer> findByContentPlan(ContentPlan contentPlan);
}
