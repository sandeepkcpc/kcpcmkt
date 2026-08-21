package com.kcpc.mkt.production.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.domain.EditingExecutionParticipant;

import java.util.List;
import java.util.UUID;

public interface EditingExecutionParticipantRepository extends InsertOnlyRepository<EditingExecutionParticipant, UUID> {
    List<EditingExecutionParticipant> findByContentPlan(ContentPlan contentPlan);
}
