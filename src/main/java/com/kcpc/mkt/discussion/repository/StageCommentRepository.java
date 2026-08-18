package com.kcpc.mkt.discussion.repository;

import com.kcpc.mkt.discussion.domain.StageComment;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.planning.domain.ContentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StageCommentRepository extends JpaRepository<StageComment, java.util.UUID> {
    List<StageComment> findByContentPlanAndStageOrderByCreatedAtAsc(ContentPlan contentPlan, LifecycleStage stage);
}
