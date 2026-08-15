package com.kcpc.mkt.masterdata.repository;

import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyChannelRepository extends JpaRepository<CompanyChannel, UUID> {
    List<CompanyChannel> findByActiveTrue();

    Optional<CompanyChannel> findByChannelHandleIgnoreCase(String channelHandle);
}
