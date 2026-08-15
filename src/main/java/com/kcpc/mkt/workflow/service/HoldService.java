package com.kcpc.mkt.workflow.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.workflow.domain.WorkHoldRecord;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * BR-063 / SRS-REQ-091 / SAD-DES-027: Hold and Resume are administrative actions restricted to
 * native CEO/MM authority, permitted only while the deliverable is Shoot In Progress or Editing.
 * The primary workflow status is never touched (ERD-CON-061) - this is a parallel record.
 */
@Service
public class HoldService {

    private final WorkHoldRecordRepository holdRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public HoldService(WorkHoldRecordRepository holdRepository, AuthorizationService authorizationService,
                        AuditService auditService) {
        this.holdRepository = holdRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public WorkHoldRecord placeHold(User actor, WorkflowInstance workflowInstance, String reason) {
        authorizationService.requireNativeAuthority(actor, "Hold");
        WorkflowStatus status = workflowInstance.getCurrentStatusCode();
        if (status != WorkflowStatus.SIP && status != WorkflowStatus.ED) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Hold is only permitted while Shoot In Progress or Editing (ERD-CON-061)");
        }
        if (holdRepository.findByWorkflowInstanceAndResumedAtIsNull(workflowInstance).isPresent()) {
            throw DomainException.conflict(ErrorCode.CONFLICT_STALE_UPDATE,
                    "An open Hold already exists for this deliverable (ERD-CON-062)");
        }
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A hold reason is mandatory");
        }
        WorkHoldRecord record = holdRepository.save(new WorkHoldRecord(workflowInstance, status, actor, reason));
        auditService.record(actor, Optional.empty(), "ADMIN_ACTION", "WORK_HELD", "work_hold_records",
                record.getId(), reason);
        return record;
    }

    @Transactional
    public WorkHoldRecord resume(User actor, WorkflowInstance workflowInstance) {
        authorizationService.requireNativeAuthority(actor, "Resume");
        WorkHoldRecord record = holdRepository.findByWorkflowInstanceAndResumedAtIsNull(workflowInstance)
                .orElseThrow(() -> DomainException.notFound("No open Hold exists for this deliverable"));
        record.resume(actor, Instant.now());
        holdRepository.save(record);
        auditService.record(actor, Optional.empty(), "ADMIN_ACTION", "WORK_RESUMED", "work_hold_records",
                record.getId(), null);
        return record;
    }

    /** Used by Shooting/Editing services to block progression while an open Hold exists. */
    public void requireNoOpenHold(WorkflowInstance workflowInstance) {
        if (holdRepository.findByWorkflowInstanceAndResumedAtIsNull(workflowInstance).isPresent()) {
            throw new DomainException(ErrorCode.WORKFLOW_ACTIVE_HOLD_BLOCKS_ACTION, HttpStatus.CONFLICT,
                    "This deliverable is currently on Hold; Resume before continuing");
        }
    }
}
