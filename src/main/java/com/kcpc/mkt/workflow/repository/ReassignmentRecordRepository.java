package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.ReassignmentRecord;

import java.util.UUID;

public interface ReassignmentRecordRepository extends InsertOnlyRepository<ReassignmentRecord, UUID> {
}
