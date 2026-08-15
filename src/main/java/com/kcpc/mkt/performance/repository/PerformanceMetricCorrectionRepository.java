package com.kcpc.mkt.performance.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.performance.domain.CreativePerformanceScorecard;
import com.kcpc.mkt.performance.domain.PerformanceMetricCorrection;

import java.util.List;
import java.util.UUID;

public interface PerformanceMetricCorrectionRepository extends InsertOnlyRepository<PerformanceMetricCorrection, UUID> {
    List<PerformanceMetricCorrection> findByScorecardOrderByCorrectedAtDesc(CreativePerformanceScorecard scorecard);
}
