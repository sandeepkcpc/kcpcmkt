package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.CancellationRecord;

import java.util.UUID;

public interface CancellationRecordRepository extends InsertOnlyRepository<CancellationRecord, UUID> {
}
