package com.kcpc.mkt.idea.repository;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {
    Optional<Idea> findByWorkflowInstance(WorkflowInstance workflowInstance);

    long countByBusinessIdeaCodeStartingWith(String prefix);

    List<Idea> findByWorkflowInstance_CurrentStatusCodeOrderBySubmittedAtAsc(WorkflowStatus status);

    List<Idea> findAllByOrderBySubmittedAtDesc();
}
