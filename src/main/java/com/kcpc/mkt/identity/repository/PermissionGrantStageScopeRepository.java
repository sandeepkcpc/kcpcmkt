package com.kcpc.mkt.identity.repository;

import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.PermissionGrantStageScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PermissionGrantStageScopeRepository extends JpaRepository<PermissionGrantStageScope, UUID> {
    List<PermissionGrantStageScope> findByGrant(PermissionGrant grant);
}
