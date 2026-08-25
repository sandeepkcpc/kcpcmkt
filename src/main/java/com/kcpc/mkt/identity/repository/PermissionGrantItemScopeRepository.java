package com.kcpc.mkt.identity.repository;

import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.PermissionGrantItemScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PermissionGrantItemScopeRepository extends JpaRepository<PermissionGrantItemScope, UUID> {
    List<PermissionGrantItemScope> findByGrant(PermissionGrant grant);

    /** Bulk variant for candidate-picker resolution (avoids one query per grant). */
    List<PermissionGrantItemScope> findByGrant_IdIn(Collection<UUID> grantIds);
}
