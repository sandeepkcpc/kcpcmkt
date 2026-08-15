package com.kcpc.mkt.security;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.domain.UserSession;
import com.kcpc.mkt.identity.repository.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * ERD-TBL-002 user_sessions as the JWT token registry (SAD-ADR-001). Only SHA-256(jti) is ever
 * persisted - never the raw JWT or raw jti. Revocation is by flag, never by row deletion.
 */
@Service
public class TokenRegistryService {

    private final UserSessionRepository sessionRepository;
    private final JwtService jwtService;

    public TokenRegistryService(UserSessionRepository sessionRepository, JwtService jwtService) {
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
    }

    public static String sha256Hex(UUID jti) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jti.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Transactional
    public JwtService.IssuedJwt issueAndRegister(User user, String ipAddress, String userAgent) {
        JwtService.IssuedJwt issued = jwtService.issue(user.getId());
        UserSession session = new UserSession(user, sha256Hex(issued.jti()), ipAddress, userAgent, issued.expiresAt());
        sessionRepository.save(session);
        return issued;
    }

    /**
     * Full per-request revalidation path (KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md "Security rules"):
     * signature, expiry, registry entry exists, not revoked, account active.
     */
    @Transactional(readOnly = true)
    public User validateAndResolveUser(String rawJwt) {
        JwtService.ParsedJwt parsed = jwtService.parseAndVerifySignature(rawJwt);
        String hash = sha256Hex(parsed.jti());
        UserSession session = sessionRepository.findBySessionTokenHash(hash)
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED,
                        "No registry entry for this token"));
        if (session.isRevoked()) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_REVOKED, HttpStatus.UNAUTHORIZED, "Token has been revoked");
        }
        if (!Instant.now().isBefore(session.getExpiresAt())) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Token has expired");
        }
        User user = session.getUser();
        if (!user.isActive()) {
            throw new DomainException(ErrorCode.AUTH_ACCOUNT_INACTIVE, HttpStatus.UNAUTHORIZED, "Account is deactivated");
        }
        return user;
    }

    /** Logout: revoke, never delete. */
    @Transactional
    public void revoke(String rawJwt) {
        JwtService.ParsedJwt parsed = jwtService.parseAndVerifySignature(rawJwt);
        sessionRepository.findBySessionTokenHash(sha256Hex(parsed.jti()))
                .ifPresent(UserSession::revoke);
    }

    /** Account deactivation: revoke all of the user's currently-active tokens, effective immediately. */
    @Transactional
    public void revokeAllActiveSessionsForUser(User user) {
        List<UserSession> active = sessionRepository.findByUserAndRevokedFalse(user);
        active.forEach(UserSession::revoke);
    }
}
