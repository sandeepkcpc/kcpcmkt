package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlannedOutputRepository extends JpaRepository<PlannedOutput, UUID> {
    List<PlannedOutput> findByContentPlan(ContentPlan contentPlan);
}
