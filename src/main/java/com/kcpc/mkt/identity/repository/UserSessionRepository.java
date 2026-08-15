package com.kcpc.mkt.identity.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findBySessionTokenHash(String sessionTokenHash);

    List<UserSession> findByUserAndRevokedFalse(User user);
}
