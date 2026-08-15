package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
}
