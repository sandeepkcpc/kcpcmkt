package com.kcpc.mkt.identity.repository;

import com.kcpc.mkt.identity.domain.BusinessRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRoleRepository extends JpaRepository<BusinessRole, UUID> {
    List<BusinessRole> findByActiveTrue();

    /** CSV User import: exact-name resolution (case-insensitive only - never fuzzy/partial) so a
     * typo like "Vido Editor" is rejected rather than silently matching "Video Editor". */
    Optional<BusinessRole> findByRoleNameIgnoreCaseAndActiveTrue(String roleName);
}
