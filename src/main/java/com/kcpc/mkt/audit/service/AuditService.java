package com.kcpc.mkt.audit.service;

import com.kcpc.mkt.audit.domain.SystemAuditLog;
import com.kcpc.mkt.audit.repository.SystemAuditLogRepository;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** ERD-TBL-025: append-only system-wide audit trail (SAD-DES-006). */
@Service
public class AuditService {

    private final SystemAuditLogRepository repository;

    public AuditService(SystemAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(User actor, Optional<PermissionGrant> actingGrant, String eventCategory, String eventType,
                        String targetEntityName, UUID targetEntityId, String actionReason) {
        repository.save(new SystemAuditLog(actor, actor.resolvedAccessClass(), actingGrant.orElse(null),
                eventCategory, eventType, targetEntityName, targetEntityId, null, null, actionReason, null));
    }
}
