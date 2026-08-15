package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {
    List<ReviewCycle> findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(WorkflowInstance workflowInstance, GateType gateType);

    List<ReviewCycle> findByWorkflowInstanceOrderByCycleNumberDesc(WorkflowInstance workflowInstance);

    List<ReviewCycle> findBySubmittedByAndDecidedAtIsNotNullOrderByDecidedAtDesc(User submittedBy);
}
