package com.kcpc.mkt.identity.repository;

import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PermissionGrantRepository extends JpaRepository<PermissionGrant, UUID> {
    List<PermissionGrant> findByGranteeAndPermissionAndActiveTrue(User grantee, OperationalPermission permission);

    List<PermissionGrant> findByGrantee(User grantee);

    List<PermissionGrant> findByActiveTrueOrderByEffectiveFromDesc();

    /** Bulk lookup for candidate-picker resolution (avoids one query per user - see AuthorizationService). */
    List<PermissionGrant> findByPermissionAndActiveTrue(OperationalPermission permission);
}
