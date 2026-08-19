package com.kcpc.mkt.production.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.production.domain.ShootingExecutionParticipant;

import java.util.List;
import java.util.UUID;

public interface ShootingExecutionParticipantRepository extends InsertOnlyRepository<ShootingExecutionParticipant, UUID> {
    List<ShootingExecutionParticipant> findByContentPlan(ContentPlan contentPlan);

    /** ENG-062: My Work "My Review Feedback" - every plan this Cameraperson was ever a recorded shoot participant on. */
    List<ShootingExecutionParticipant> findByCameraperson(User cameraperson);
}
