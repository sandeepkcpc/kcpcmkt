package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.planning.domain.ContentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPlanRepository extends JpaRepository<ContentPlan, UUID> {
    Optional<ContentPlan> findByIdea(Idea idea);

    Optional<ContentPlan> findByContentId(String contentId);

    List<ContentPlan> findAllByOrderByCreatedAtDesc();
}
