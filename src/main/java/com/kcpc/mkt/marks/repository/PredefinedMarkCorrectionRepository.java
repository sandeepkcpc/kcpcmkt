package com.kcpc.mkt.marks.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.marks.domain.PredefinedMarkCorrection;
import com.kcpc.mkt.marks.domain.PredefinedRoleMarks;

import java.util.List;
import java.util.UUID;

public interface PredefinedMarkCorrectionRepository extends InsertOnlyRepository<PredefinedMarkCorrection, UUID> {
    List<PredefinedMarkCorrection> findByPredefinedMarkOrderByCorrectedAtDesc(PredefinedRoleMarks predefinedMark);
}
