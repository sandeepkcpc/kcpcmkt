package com.kcpc.mkt.identity.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.BusinessRole;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.BusinessRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** BRS-REQ-085 / ERD-CON-063: expandable Business Role catalogue administration, CEO-exclusive. */
@Service
public class BusinessRoleAdminService {

    private final BusinessRoleRepository businessRoleRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public BusinessRoleAdminService(BusinessRoleRepository businessRoleRepository,
                                     AuthorizationService authorizationService, AuditService auditService) {
        this.businessRoleRepository = businessRoleRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private void requireCeo(User actor) {
        authorizationService.requireAccessClass(actor, AccessClass.CEO_OWNER, "Business Role administration");
    }

    public List<BusinessRole> listActive() {
        return businessRoleRepository.findByActiveTrue();
    }

    /** New ordinary Business Roles default to EMPLOYEE unless explicitly designated otherwise by the CEO. */
    @Transactional
    public BusinessRole create(User ceo, String roleName, AccessClass accessClass) {
        requireCeo(ceo);
        if (roleName == null || roleName.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Business Role name is mandatory");
        }
        BusinessRole role = businessRoleRepository.save(new BusinessRole(roleName, accessClass));
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "BUSINESS_ROLE_CREATED", "business_roles",
                role.getId(), null);
        return role;
    }

    /** Historically-used Business Roles are deactivated, never destructively deleted. */
    @Transactional
    public BusinessRole deactivate(User ceo, UUID businessRoleId) {
        requireCeo(ceo);
        BusinessRole role = businessRoleRepository.findById(businessRoleId)
                .orElseThrow(() -> DomainException.notFound("Business Role not found: " + businessRoleId));
        role.deactivate();
        businessRoleRepository.save(role);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "BUSINESS_ROLE_DEACTIVATED", "business_roles",
                role.getId(), null);
        return role;
    }

    @Transactional
    public BusinessRole activate(User ceo, UUID businessRoleId) {
        requireCeo(ceo);
        BusinessRole role = businessRoleRepository.findById(businessRoleId)
                .orElseThrow(() -> DomainException.notFound("Business Role not found: " + businessRoleId));
        role.activate();
        businessRoleRepository.save(role);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "BUSINESS_ROLE_ACTIVATED", "business_roles",
                role.getId(), null);
        return role;
    }
}
