package com.kcpc.mkt.publishing.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.PublicationEventType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActualPublicationEventRepository extends InsertOnlyRepository<ActualPublicationEvent, UUID> {
    List<ActualPublicationEvent> findByContentPlan(ContentPlan contentPlan);

    List<ActualPublicationEvent> findByPlannedOutputAndEventType(PlannedOutput plannedOutput, PublicationEventType eventType);

    List<ActualPublicationEvent> findByContentPlan_IdIn(Collection<UUID> contentPlanIds);
}
