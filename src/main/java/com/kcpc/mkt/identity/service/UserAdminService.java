package com.kcpc.mkt.identity.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.BusinessRole;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.BusinessRoleRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.security.TokenRegistryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * BRS-REQ-003..005: exclusive CEO user account and Business Role assignment administration.
 * Not an Operational Permission - never delegable (SRS-REQ-003/004/092).
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final BusinessRoleRepository businessRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRegistryService tokenRegistryService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public UserAdminService(UserRepository userRepository, BusinessRoleRepository businessRoleRepository,
                             PasswordEncoder passwordEncoder, TokenRegistryService tokenRegistryService,
                             AuthorizationService authorizationService, AuditService auditService) {
        this.userRepository = userRepository;
        this.businessRoleRepository = businessRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRegistryService = tokenRegistryService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private void requireCeo(User actor) {
        authorizationService.requireAccessClass(actor, AccessClass.CEO_OWNER, "User administration");
    }

    /** AC-005.1: a mandatory reason is required to save any account administration action. */
    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A reason is mandatory for this action");
        }
    }

    @Transactional
    public User createUser(User ceo, String fullName, String email, String rawPassword, UUID businessRoleId,
                            String creationReason) {
        requireCeo(ceo);
        requireReason(creationReason);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION, "A user with this email already exists");
        }
        BusinessRole role = businessRoleRepository.findById(businessRoleId)
                .orElseThrow(() -> DomainException.notFound("Business Role not found: " + businessRoleId));
        User user = userRepository.save(new User(fullName, email, passwordEncoder.encode(rawPassword), role));
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "USER_CREATED", "users", user.getId(), creationReason);
        return user;
    }

    @Transactional
    public User deactivate(User ceo, UUID userId, String reason) {
        requireCeo(ceo);
        requireReason(reason);
        User user = requireUser(userId);
        user.deactivate();
        userRepository.save(user);
        // Deactivation revokes active tokens immediately (KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md "Security rules").
        tokenRegistryService.revokeAllActiveSessionsForUser(user);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "USER_DEACTIVATED", "users", user.getId(), reason);
        return user;
    }

    @Transactional
    public User activate(User ceo, UUID userId, String reason) {
        requireCeo(ceo);
        requireReason(reason);
        User user = requireUser(userId);
        user.activate();
        userRepository.save(user);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "USER_ACTIVATED", "users", user.getId(), reason);
        return user;
    }

    @Transactional
    public User changeBusinessRole(User ceo, UUID userId, UUID newBusinessRoleId, String reason) {
        requireCeo(ceo);
        requireReason(reason);
        User user = requireUser(userId);
        BusinessRole newRole = businessRoleRepository.findById(newBusinessRoleId)
                .orElseThrow(() -> DomainException.notFound("Business Role not found: " + newBusinessRoleId));
        user.reassignBusinessRole(newRole);
        userRepository.save(user);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "BUSINESS_ROLE_CHANGED", "users", user.getId(), reason);
        return user;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> DomainException.notFound("User not found: " + userId));
    }
}
