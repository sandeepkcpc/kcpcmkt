package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.audit.dto.AuditLogResponse;
import com.kcpc.mkt.audit.repository.SystemAuditLogRepository;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** API-OP-061: Query System Audit Log (Permission #16 Action) - strictly read-only, no edit/delete affordance. */
@RestController
public class AuditRestController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final SystemAuditLogRepository auditLogRepository;
    private final AuthorizationService authorizationService;

    public AuditRestController(SystemAuditLogRepository auditLogRepository, AuthorizationService authorizationService) {
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/v1/audit/logs")
    public List<AuditLogResponse> logs(@RequestParam(required = false) String actionType,
                                        @RequestParam(required = false) UUID userId,
                                        @RequestParam(required = false) String entityType,
                                        @RequestParam(required = false) LocalDate startDate,
                                        @RequestParam(required = false) LocalDate endDate,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        authorizationService.requireAuthority(principal.user(), OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW, null, null);

        Instant from = startDate != null ? startDate.atStartOfDay(BUSINESS_ZONE).toInstant() : null;
        Instant to = endDate != null ? endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant() : null;

        return auditLogRepository.findAllByOrderByEventTimestampDesc().stream()
                .filter(log -> actionType == null || actionType.isBlank() || log.getEventType().equalsIgnoreCase(actionType))
                .filter(log -> userId == null || log.getActor().getId().equals(userId))
                .filter(log -> entityType == null || entityType.isBlank() || log.getTargetEntityName().equalsIgnoreCase(entityType))
                .filter(log -> from == null || !log.getEventTimestamp().isBefore(from))
                .filter(log -> to == null || log.getEventTimestamp().isBefore(to))
                .map(AuditLogResponse::from)
                .toList();
    }
}
