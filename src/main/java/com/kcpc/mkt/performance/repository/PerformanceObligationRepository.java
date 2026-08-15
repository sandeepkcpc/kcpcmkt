package com.kcpc.mkt.performance.repository;

import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceObligationRepository extends JpaRepository<PerformanceObligation, UUID> {
    Optional<PerformanceObligation> findByEvent(ActualPublicationEvent event);

    List<PerformanceObligation> findByEvent_ContentPlan_Id(UUID contentPlanId);
}
