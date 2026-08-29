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

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

/**
 * BRS-REQ-003..005: exclusive CEO user account and Business Role assignment administration.
 * Not an Operational Permission - never delegable (SRS-REQ-003/004/092).
 */
@Service
public class UserAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    /**
     * Admin/CEO Password Reset (KCPC CPL's primary reset path - see PasswordResetService's own
     * javadoc for why: no email/SMS infra, small fixed employee base, CEO already administers every
     * account). Generates a fresh temporary password, forces the employee to change it on next
     * login ({@link User#requirePasswordChangeOnNextLogin()}), and immediately revokes every
     * currently-active session for that user - the same "reset invalidates active sessions"
     * guarantee the self-service email-link reset already provides
     * ({@code PasswordResetService#confirmReset}), never a second/weaker implementation of it.
     * Returns the raw temporary password so the caller (AdminMvcController) can display it to the
     * CEO exactly once, to copy and share out-of-band - it is never logged, and only its BCrypt
     * hash is ever persisted.
     */
    @Transactional
    public String resetPasswordByAdmin(User ceo, UUID userId, String reason) {
        requireCeo(ceo);
        requireReason(reason);
        User user = requireUser(userId);
        String temporaryPassword = generateTemporaryPassword();
        user.changePasswordHash(passwordEncoder.encode(temporaryPassword));
        user.requirePasswordChangeOnNextLogin();
        userRepository.save(user);
        tokenRegistryService.revokeAllActiveSessionsForUser(user);
        auditService.record(ceo, Optional.empty(), "USER_ADMIN", "PASSWORD_RESET_BY_ADMIN", "users", user.getId(), reason);
        return temporaryPassword;
    }

    /** {@code TEMP-######-KCPC}: readable/dictatable over phone or chat for the CEO's "generate,
     * then copy & share" workflow, with a 6-digit (not 4-digit) random segment - a meaningfully
     * larger, still practically shareable, search space (1,000,000 combinations), since this app
     * has no login rate-limiting yet and a guessable temporary credential deserves real entropy,
     * not just the smallest number that "looks right" in a mockup. */
    private static String generateTemporaryPassword() {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("TEMP-%06d-KCPC", number);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> DomainException.notFound("User not found: " + userId));
    }
}
