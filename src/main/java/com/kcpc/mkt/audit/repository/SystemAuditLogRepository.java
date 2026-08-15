package com.kcpc.mkt.audit.repository;

import com.kcpc.mkt.audit.domain.SystemAuditLog;
import com.kcpc.mkt.common.repository.InsertOnlyRepository;

import java.util.List;
import java.util.UUID;

public interface SystemAuditLogRepository extends InsertOnlyRepository<SystemAuditLog, UUID> {
    List<SystemAuditLog> findAllByOrderByEventTimestampDesc();
}
