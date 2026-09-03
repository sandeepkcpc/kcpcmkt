package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.planning.domain.ContentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPlanRepository extends JpaRepository<ContentPlan, UUID> {
    Optional<ContentPlan> findByIdea(Idea idea);

    Optional<ContentPlan> findByContentId(String contentId);

    List<ContentPlan> findAllByOrderByCreatedAtDesc();

    /** Content Pipeline dashboard: preparedBy is LAZY, so join-fetch it here rather than risk a
     * LazyInitializationException in the (non-transactional) MVC view layer. */
    @Query("select cp from ContentPlan cp left join fetch cp.preparedBy order by cp.createdAt desc")
    List<ContentPlan> findAllWithPreparedByOrderByCreatedAtDesc();

    /** ENG-094: CategoryService's delete-vs-deactivate check - whether any Content Plan currently
     * references this category name (case-insensitive, matching how categoryText is validated). */
    boolean existsByCategoryTextIgnoreCase(String categoryText);
}
