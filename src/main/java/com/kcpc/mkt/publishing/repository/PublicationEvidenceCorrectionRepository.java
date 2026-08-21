package com.kcpc.mkt.publishing.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.PublicationEvidenceCorrection;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PublicationEvidenceCorrectionRepository extends InsertOnlyRepository<PublicationEvidenceCorrection, UUID> {
    List<PublicationEvidenceCorrection> findByEventOrderByCorrectedAtDesc(ActualPublicationEvent event);

    /** ENG-075: Content Pipeline dashboard's per-channel effective Evidence URL - batch-loaded across every plan's events. */
    List<PublicationEvidenceCorrection> findByEvent_IdIn(Collection<UUID> eventIds);
}
