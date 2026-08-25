package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.ReopenPurpose;
import com.kcpc.mkt.workflow.domain.ReopenRecord;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReopenRecordRepository extends InsertOnlyRepository<ReopenRecord, UUID> {

    /**
     * The latest reopen of a given purpose for this workflow instance - the current cycle's
     * "resume point". {@code PublishingService} uses this (purpose = PUBLISHING_REOPEN) so a
     * repost cycle's Publishing Scope resolution and Publisher checklist only consider events
     * recorded on-or-after this cycle started, never events left over from an earlier cycle.
     */
    Optional<ReopenRecord> findFirstByWorkflowInstanceAndReopenPurposeOrderByReopenedAtDesc(
            WorkflowInstance workflowInstance, ReopenPurpose reopenPurpose);

    /**
     * Every reopen of a given purpose for this plan, oldest first - the KPI Dashboard's governed
     * On-Time Delivery formula enumerates every Publishing cycle (original + each repost), not
     * just the current/latest one.
     */
    List<ReopenRecord> findByWorkflowInstanceAndReopenPurposeOrderByReopenedAtAsc(
            WorkflowInstance workflowInstance, ReopenPurpose reopenPurpose);

    /** Batch variant across every relevant plan at once (avoids N+1) - KPI Dashboard only. */
    List<ReopenRecord> findByWorkflowInstance_IdInAndReopenPurposeOrderByReopenedAtAsc(
            Collection<UUID> workflowInstanceIds, ReopenPurpose reopenPurpose);
}
