package com.kcpc.mkt.security;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.PasswordResetToken;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.PasswordResetTokenRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Self-service "Forgot Password" flow: request a reset link, then confirm it with a new password.
 * Same shared-application-service architecture as {@link AuthenticationApplicationService} (both
 * MVC and any future REST caller would call this one service, never re-implement the logic).
 *
 * <p>Deliberately never reveals whether a given email has an account (BR: "do not reveal whether
 * email exists") - {@link #requestReset} takes exactly the same code path and returns exactly the
 * same way whether the email matches an active user, an inactive user, or no user at all; only the
 * inside of the {@code ifPresent} branch differs, and none of that difference is observable by the
 * caller (no distinguishing response, no distinguishing timing branch, no exception either way).
 *
 * <p>Only SHA-256(raw token) is ever persisted (mirrors {@link TokenRegistryService}'s own
 * SHA-256(jti) pattern for the JWT registry) - the raw token exists only transiently, to be
 * delivered to the user out-of-band.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRegistryService tokenRegistryService;
    private final AuditService auditService;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository resetTokenRepository,
                                 PasswordEncoder passwordEncoder, TokenRegistryService tokenRegistryService,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRegistryService = tokenRegistryService;
        this.auditService = auditService;
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Always succeeds (never throws for an unknown/inactive email) - the caller-visible behavior
     * (MVC controller) must always show the identical message regardless of the return value here,
     * so the caller can never distinguish "no such account" from "reset link sent" (BR: never
     * reveal whether an email exists).
     *
     * <p>Returns the raw token when a matching active account was found, empty otherwise - this
     * return value is a Java-level detail for the caller to act on (e.g. hand it to a real
     * transactional-email send once that integration exists; today's MVC controller deliberately
     * discards it, never surfaces it in any HTTP response/page), the same shape as
     * {@link AuthenticationApplicationService.LoginResult#jwt()} already returning a raw secret for
     * its caller to decide what to do with. Never returned/logged/stored anywhere the requester of
     * an unknown email could observe a difference.
     */
    @Transactional
    public Optional<String> requestReset(String email, String ipAddress) {
        Optional<User> match = userRepository.findByEmailIgnoreCase(email).filter(User::isActive);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        User user = match.get();
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(RESET_TOKEN_TTL);
        resetTokenRepository.save(new PasswordResetToken(user, sha256Hex(rawToken), expiresAt, ipAddress));
        auditService.record(user, Optional.empty(), "AUTH", "PASSWORD_RESET_REQUESTED", "users", user.getId(), null);
        // Email-delivery integration point: this codebase has no mail/SMTP dependency today (same
        // "disabled until real credentials/infra exist" situation as the Google Drive integration -
        // see DriveProvisioningService). Logging the link is a stand-in so the flow is at least
        // locally usable/testable end-to-end; wiring a real transactional-email provider here is
        // required before this is production-ready, and the raw token must never be sent through
        // any channel less secure than that provider's own delivery.
        log.info("Password reset requested for user {} - reset link: /reset-password?token={}",
                user.getId(), rawToken);
        return Optional.of(rawToken);
    }

    /** Validates the token (exists, unused, unexpired), updates the password, invalidates every
     * currently-active session for that user, and seals the token as used - all in one transaction
     * so a confirmed reset can never be left half-applied. */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_MISSING, HttpStatus.BAD_REQUEST,
                    "Reset token is required");
        }
        PasswordResetToken token = resetTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_TOKEN_INVALID, HttpStatus.BAD_REQUEST,
                        "This reset link is invalid"));
        if (token.getUsedAt() != null) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_REVOKED, HttpStatus.BAD_REQUEST,
                    "This reset link has already been used");
        }
        if (!Instant.now().isBefore(token.getExpiresAt())) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_EXPIRED, HttpStatus.BAD_REQUEST,
                    "This reset link has expired");
        }

        User user = token.getUser();
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed();
        // BR: "after successful reset invalidate active sessions" - reuses the exact same
        // revocation TokenRegistryService already performs on account deactivation, never a
        // second/parallel implementation of session invalidation.
        tokenRegistryService.revokeAllActiveSessionsForUser(user);
        auditService.record(user, Optional.empty(), "AUTH", "PASSWORD_RESET_COMPLETED", "users",
                user.getId(), null);
    }

    /**
     * Completes the "Force Change Password" screen after an Admin/CEO-issued temporary password:
     * sets the employee's own chosen password and clears {@link User#isPasswordChangeRequired()} so
     * normal navigation resumes. No re-entry of the current/temporary password is required (matches
     * the agreed flow's own simplicity - New Password + Confirm Password only) since reaching this
     * screen at all already required successfully logging in with it. The currently-active session
     * performing this change is deliberately left alone (only revoked at admin-reset time, when the
     * temporary password was actually issued) - forcing a fresh login here would add friction the
     * agreed flow never asked for.
     */
    @Transactional
    public void completeForcedPasswordChange(User user, String newPassword) {
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.clearPasswordChangeRequirement();
        userRepository.save(user);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
