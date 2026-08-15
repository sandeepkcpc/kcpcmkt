package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.ReopenRecord;

import java.util.UUID;

public interface ReopenRecordRepository extends InsertOnlyRepository<ReopenRecord, UUID> {
}
