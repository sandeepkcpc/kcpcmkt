package com.kcpc.mkt.workflow.service;

import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory;
import com.kcpc.mkt.workflow.repository.WorkflowInstanceRepository;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The single sanctioned entry point for workflow status changes (ERD-CON-033/034): every
 * transition is paired atomically with a WorkflowTransitionHistory row, in the same
 * transaction as the calling business command.
 */
@Service
public class WorkflowTransitionService {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTransitionHistoryRepository historyRepository;

    public WorkflowTransitionService(WorkflowInstanceRepository instanceRepository,
                                      WorkflowTransitionHistoryRepository historyRepository) {
        this.instanceRepository = instanceRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public WorkflowInstance createInstance(WorkflowStatus initialStatus) {
        return instanceRepository.save(new WorkflowInstance(initialStatus));
    }

    @Transactional
    public void transition(WorkflowInstance instance, WorkflowStatus to, User actor,
                            Optional<PermissionGrant> actingGrant, String triggerCommand, String reason) {
        WorkflowStatus from = instance.getCurrentStatusCode();
        instance.transitionTo(to);
        instanceRepository.save(instance);
        historyRepository.save(new WorkflowTransitionHistory(instance, from, to, actor, actingGrant.orElse(null),
                triggerCommand, reason));
    }
}
