package com.kcpc.mkt.idea.repository;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.identity.domain.User;
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

    /** ENG-059: "My Ideas" - an Employee's own submissions only, own-source for both KPI counts and the table. */
    List<Idea> findBySubmittedByOrderBySubmittedAtDesc(User submittedBy);
}
