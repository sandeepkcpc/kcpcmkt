package com.kcpc.mkt.publishing.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.publishing.domain.PublicationTargetNaRecord;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PublicationTargetNaRecordRepository extends InsertOnlyRepository<PublicationTargetNaRecord, UUID> {
    List<PublicationTargetNaRecord> findByPlannedOutput(PlannedOutput plannedOutput);

    /** Pipeline dashboard: batch-loads across every plan's outputs at once (avoids N+1). */
    List<PublicationTargetNaRecord> findByPlannedOutput_IdIn(Collection<UUID> plannedOutputIds);
}
