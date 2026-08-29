package com.kcpc.mkt.idea.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.domain.IdeaDescriptionCorrection;

import java.util.List;
import java.util.UUID;

public interface IdeaDescriptionCorrectionRepository extends InsertOnlyRepository<IdeaDescriptionCorrection, UUID> {
    List<IdeaDescriptionCorrection> findByIdeaOrderByCorrectedAtDesc(Idea idea);
}
