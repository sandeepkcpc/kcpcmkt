package com.kcpc.mkt.performance.repository;

import com.kcpc.mkt.performance.domain.CreativePerformanceScorecard;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreativePerformanceScorecardRepository extends JpaRepository<CreativePerformanceScorecard, UUID> {
    Optional<CreativePerformanceScorecard> findByObligation(PerformanceObligation obligation);
}
