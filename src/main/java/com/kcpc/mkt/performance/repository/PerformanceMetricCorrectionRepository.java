package com.kcpc.mkt.performance.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.performance.domain.CreativePerformanceScorecard;
import com.kcpc.mkt.performance.domain.PerformanceMetricCorrection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PerformanceMetricCorrectionRepository extends InsertOnlyRepository<PerformanceMetricCorrection, UUID> {

    /**
     * Join-fetches {@code correctedBy} so callers outside a transaction (e.g. the
     * non-{@code @Transactional} deliverable-detail view, per open-in-view:false) can safely
     * render "By: <name>" in a per-scorecard Correction History without a LazyInitializationException.
     */
    @Query("select c from PerformanceMetricCorrection c join fetch c.correctedBy where c.scorecard = :scorecard "
            + "order by c.correctedAt desc")
    List<PerformanceMetricCorrection> findByScorecardOrderByCorrectedAtDesc(@Param("scorecard") CreativePerformanceScorecard scorecard);

    /** Batch variant across every relevant scorecard at once (avoids N+1) - KPI Dashboard only. */
    List<PerformanceMetricCorrection> findByScorecard_IdInOrderByCorrectedAtDesc(Collection<UUID> scorecardIds);
}
