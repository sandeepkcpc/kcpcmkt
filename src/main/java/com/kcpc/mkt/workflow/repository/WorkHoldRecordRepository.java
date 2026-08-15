package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.workflow.domain.WorkHoldRecord;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkHoldRecordRepository extends JpaRepository<WorkHoldRecord, UUID> {
    Optional<WorkHoldRecord> findByWorkflowInstanceAndResumedAtIsNull(WorkflowInstance workflowInstance);

    List<WorkHoldRecord> findByResumedAtIsNull();

    List<WorkHoldRecord> findByWorkflowInstanceOrderByHeldAtDesc(WorkflowInstance workflowInstance);
}
