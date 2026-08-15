package com.kcpc.mkt.masterdata.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import com.kcpc.mkt.masterdata.domain.Platform;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PlatformRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * BRS-REQ-060/061 / API-OP-036/066..070: Permission #17 Master-Catalogue Management for
 * Platforms, Company Channels, and Publication Targets. Deactivation is always soft
 * (`is_active = FALSE`); historical data referencing a catalogue object is preserved.
 */
@Service
public class MasterCatalogueService {

    private final PlatformRepository platformRepository;
    private final CompanyChannelRepository channelRepository;
    private final PublicationTargetRepository targetRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public MasterCatalogueService(PlatformRepository platformRepository, CompanyChannelRepository channelRepository,
                                   PublicationTargetRepository targetRepository,
                                   AuthorizationService authorizationService, AuditService auditService) {
        this.platformRepository = platformRepository;
        this.channelRepository = channelRepository;
        this.targetRepository = targetRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private Optional<PermissionGrant> requireCatalogueAuthority(User actor) {
        return authorizationService.requireAuthority(actor, OperationalPermission.PERM_17_PLATFORM_CATALOGUE_MANAGE,
                LifecycleStage.ADMINISTRATIVE, null);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A catalogue reason is mandatory (BRS-REQ-061)");
        }
    }

    @Transactional
    public Platform createPlatform(User actor, String platformName, String catalogueReason) {
        Optional<PermissionGrant> grant = requireCatalogueAuthority(actor);
        requireReason(catalogueReason);
        if (platformName == null || platformName.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Platform name is mandatory");
        }
        if (platformRepository.findByPlatformNameIgnoreCase(platformName).isPresent()) {
            throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION,
                    "A platform with this name already exists (ERD-CON-032)");
        }
        Platform platform = platformRepository.save(new Platform(platformName));
        auditService.record(actor, grant, "MASTER_CATALOGUE", "PLATFORM_CATALOGUE_CREATED", "platforms",
                platform.getId(), catalogueReason);
        return platform;
    }

    @Transactional
    public Platform updatePlatform(User actor, UUID platformId, String newName, Boolean isActive, String catalogueReason) {
        Optional<PermissionGrant> grant = requireCatalogueAuthority(actor);
        requireReason(catalogueReason);
        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> DomainException.notFound("Platform not found: " + platformId));
        if (newName != null && !newName.isBlank()) {
            platform.rename(newName);
        }
        if (isActive != null) {
            if (isActive) {
                platform.activate();
            } else {
                platform.deactivate();
            }
        }
        platformRepository.save(platform);
        auditService.record(actor, grant, "MASTER_CATALOGUE", "PLATFORM_CATALOGUE_UPDATED", "platforms",
                platform.getId(), catalogueReason);
        return platform;
    }

    @Transactional
    public CompanyChannel createChannel(User actor, String channelHandle, String catalogueReason) {
        Optional<PermissionGrant> grant = requireCatalogueAuthority(actor);
        requireReason(catalogueReason);
        if (channelHandle == null || channelHandle.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Channel handle is mandatory");
        }
        if (channelRepository.findByChannelHandleIgnoreCase(channelHandle).isPresent()) {
            throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION,
                    "A channel with this handle already exists (ERD-CON-032)");
        }
        CompanyChannel channel = channelRepository.save(new CompanyChannel(channelHandle));
        auditService.record(actor, grant, "MASTER_CATALOGUE", "CHANNEL_CATALOGUE_UPDATED", "company_channels",
                channel.getId(), catalogueReason);
        return channel;
    }

    @Transactional
    public CompanyChannel updateChannel(User actor, UUID channelId, String newHandle, Boolean isActive,
                                         String catalogueReason) {
        Optional<PermissionGrant> grant = requireCatalogueAuthority(actor);
        requireReason(catalogueReason);
        CompanyChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> DomainException.notFound("Channel not found: " + channelId));
        if (newHandle != null && !newHandle.isBlank()) {
            channel.rename(newHandle);
        }
        if (isActive != null) {
            if (isActive) {
                channel.activate();
            } else {
                channel.deactivate();
            }
        }
        channelRepository.save(channel);
        auditService.record(actor, grant, "MASTER_CATALOGUE", "CHANNEL_CATALOGUE_UPDATED", "company_channels",
                channel.getId(), catalogueReason);
        return channel;
    }

    @Transactional
    public PublicationTarget createTarget(User actor, UUID platformId, UUID channelId, String targetName,
                                           String catalogueReason) {
        Optional<PermissionGrant> grant = requireCatalogueAuthority(actor);
        requireReason(catalogueReason);
        if (targetName == null || targetName.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Target name is mandatory");
        }
        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> DomainException.notFound("Platform not found: " + platformId));
        CompanyChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> DomainException.notFound("Channel not found: " + channelId));
        if (targetRepository.findByPlatformAndChannelAndActiveTrue(platform, channel).isPresent()) {
            throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION,
                    "An active target already exists for this Platform x Channel pairing (ERD-CON-032)");
        }
        PublicationTarget target = targetRepository.save(new PublicationTarget(platform, channel, targetName));
        auditService.record(actor, grant, "MASTER_CATALOGUE", "TARGET_CATALOGUE_CREATED", "publication_targets",
                target.getId(), catalogueReason);
        return target;
    }

    @Transactional
    public PublicationTarget setTargetActive(User actor, UUID targetId, boolean isActive, String catalogueReason) {
        Optional<PermissionGrant> grant = requireCatalogueAuthority(actor);
        requireReason(catalogueReason);
        PublicationTarget target = targetRepository.findById(targetId)
                .orElseThrow(() -> DomainException.notFound("Publication Target not found: " + targetId));
        if (isActive) {
            target.activate();
        } else {
            target.deactivate();
        }
        targetRepository.save(target);
        auditService.record(actor, grant, "MASTER_CATALOGUE", "TARGET_CATALOGUE_UPDATED", "publication_targets",
                target.getId(), catalogueReason);
        return target;
    }
}
