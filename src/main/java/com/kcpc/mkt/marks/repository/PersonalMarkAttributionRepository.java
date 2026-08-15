package com.kcpc.mkt.marks.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.marks.domain.PersonalMarkAttribution;
import com.kcpc.mkt.planning.domain.ContentPlan;

import java.util.List;
import java.util.UUID;

public interface PersonalMarkAttributionRepository extends InsertOnlyRepository<PersonalMarkAttribution, UUID> {
    List<PersonalMarkAttribution> findByContentPlan(ContentPlan contentPlan);

    List<PersonalMarkAttribution> findByRecipient(User recipient);
}
