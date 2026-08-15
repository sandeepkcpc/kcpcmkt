package com.kcpc.mkt.masterdata.repository;

import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import com.kcpc.mkt.masterdata.domain.Platform;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationTargetRepository extends JpaRepository<PublicationTarget, UUID> {
    List<PublicationTarget> findByActiveTrue();

    Optional<PublicationTarget> findByPlatformAndChannelAndActiveTrue(Platform platform, CompanyChannel channel);
}
