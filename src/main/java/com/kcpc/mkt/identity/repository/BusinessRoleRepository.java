package com.kcpc.mkt.identity.repository;

import com.kcpc.mkt.identity.domain.BusinessRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessRoleRepository extends JpaRepository<BusinessRole, UUID> {
    List<BusinessRole> findByActiveTrue();
}
