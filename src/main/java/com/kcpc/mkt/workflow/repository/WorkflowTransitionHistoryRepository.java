package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkflowTransitionHistoryRepository extends InsertOnlyRepository<WorkflowTransitionHistory, UUID> {
    List<WorkflowTransitionHistory> findByWorkflowInstanceOrderByTransitionTimestampAsc(WorkflowInstance workflowInstance);

    /** My Work: batch-loads across every relevant plan at once (avoids N+1 - ENG-057). */
    List<WorkflowTransitionHistory> findByWorkflowInstance_IdInOrderByTransitionTimestampAsc(Collection<UUID> workflowInstanceIds);
}
