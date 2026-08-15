package com.kcpc.mkt.security;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared login/logout application service. Both the REST controller and the MVC controller call
 * this same service (architecture rule: MVC and REST share one application/service layer).
 */
@Service
public class AuthenticationApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRegistryService tokenRegistryService;

    public AuthenticationApplicationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                             TokenRegistryService tokenRegistryService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRegistryService = tokenRegistryService;
    }

    public record LoginResult(String jwt, java.time.Instant expiresAt, User user) {
    }

    @Transactional
    public LoginResult login(String email, String rawPassword, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException(ErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }
        if (!user.isActive()) {
            throw new DomainException(ErrorCode.AUTH_ACCOUNT_INACTIVE, HttpStatus.UNAUTHORIZED,
                    "Account is deactivated");
        }
        JwtService.IssuedJwt issued = tokenRegistryService.issueAndRegister(user, ipAddress, userAgent);
        return new LoginResult(issued.token(), issued.expiresAt(), user);
    }

    @Transactional
    public void logout(String rawJwt) {
        if (rawJwt != null && !rawJwt.isBlank()) {
            tokenRegistryService.revoke(rawJwt);
        }
    }
}
