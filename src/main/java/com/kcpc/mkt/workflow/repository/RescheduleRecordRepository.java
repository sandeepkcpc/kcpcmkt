package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.RescheduleRecord;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RescheduleRecordRepository extends InsertOnlyRepository<RescheduleRecord, UUID> {

    /**
     * Every reschedule ever made on this plan, any stageContext, oldest first - the KPI Dashboard's
     * governed original/repost Publishing-cycle deadline reconstruction walks this list directly
     * (stageContext never restricts which date fields a given reschedule actually changed).
     */
    List<RescheduleRecord> findByWorkflowInstanceOrderByRescheduledAtAsc(WorkflowInstance workflowInstance);

    /** Batch variant across every relevant plan at once (avoids N+1) - KPI Dashboard only. */
    List<RescheduleRecord> findByWorkflowInstance_IdInOrderByRescheduledAtAsc(Collection<UUID> workflowInstanceIds);
}
