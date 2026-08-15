package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.ReassignmentAssignee;

import java.util.UUID;

public interface ReassignmentAssigneeRepository extends InsertOnlyRepository<ReassignmentAssignee, UUID> {
}
