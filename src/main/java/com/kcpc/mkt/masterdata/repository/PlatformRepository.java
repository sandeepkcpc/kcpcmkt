package com.kcpc.mkt.masterdata.repository;

import com.kcpc.mkt.masterdata.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformRepository extends JpaRepository<Platform, UUID> {
    List<Platform> findByActiveTrue();

    Optional<Platform> findByPlatformNameIgnoreCase(String platformName);
}
