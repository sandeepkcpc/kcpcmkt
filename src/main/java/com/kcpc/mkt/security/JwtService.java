package com.kcpc.mkt.security;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and verifies the R3.5 stateful JWT (KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md "Security rules").
 * The JWT alone is never authoritative - {@link TokenRegistryService} additionally checks the
 * server-side registry on every request (revocation, expiry, account state).
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedJwt(String token, UUID jti, Instant issuedAt, Instant expiresAt) {
    }

    public IssuedJwt issue(UUID userId) {
        UUID jti = UUID.randomUUID();
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60);
        String token = Jwts.builder()
                .subject(userId.toString())
                .id(jti.toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
        return new IssuedJwt(token, jti, now, exp);
    }

    public record ParsedJwt(UUID userId, UUID jti, Instant expiresAt) {
    }

    /** Verifies signature and expiry only; registry/account-state checks live in TokenRegistryService. */
    public ParsedJwt parseAndVerifySignature(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedJwt(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getId()),
                    claims.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "JWT has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "JWT is invalid: " + e.getMessage());
        }
    }
}
