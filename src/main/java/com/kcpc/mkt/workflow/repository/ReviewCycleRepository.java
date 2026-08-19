package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {
    List<ReviewCycle> findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(WorkflowInstance workflowInstance, GateType gateType);

    List<ReviewCycle> findByWorkflowInstanceOrderByCycleNumberDesc(WorkflowInstance workflowInstance);

    List<ReviewCycle> findBySubmittedByAndDecidedAtIsNotNullOrderByDecidedAtDesc(User submittedBy);

    /** CEO Pipeline dashboard "Actual Shoot/Edit Date" columns: batch-loads across all plans at once (avoids N+1). */
    List<ReviewCycle> findByWorkflowInstance_IdInAndGateTypeInAndDecision(
            Collection<UUID> workflowInstanceIds, Collection<GateType> gateTypes, String decision);

    /** My Work: batch-loads every review cycle across every relevant plan at once (avoids N+1 - ENG-057). */
    List<ReviewCycle> findByWorkflowInstance_IdIn(Collection<UUID> workflowInstanceIds);
}
