package com.kcpc.mkt.identity.repository;

import com.kcpc.mkt.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByActiveTrueOrderByFullNameAsc();

    List<User> findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc(String roleName);

    /** Permission-driven candidate-picker resolution (see AuthorizationService#findActiveGranteesWithExplicitGrant). */
    List<User> findByIdInAndActiveTrueOrderByFullNameAsc(Collection<UUID> ids);
}
