package com.kcpc.mkt.workflow.repository;

import com.kcpc.mkt.common.repository.InsertOnlyRepository;
import com.kcpc.mkt.workflow.domain.RescheduleRecord;

import java.util.UUID;

public interface RescheduleRecordRepository extends InsertOnlyRepository<RescheduleRecord, UUID> {
}
