package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import com.kcpc.mkt.masterdata.domain.Platform;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.dto.CreateChannelRequest;
import com.kcpc.mkt.masterdata.dto.CreatePlatformRequest;
import com.kcpc.mkt.masterdata.dto.CreateTargetRequest;
import com.kcpc.mkt.masterdata.dto.SetTargetActiveRequest;
import com.kcpc.mkt.masterdata.dto.UpdateChannelRequest;
import com.kcpc.mkt.masterdata.dto.UpdatePlatformRequest;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PlatformRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.masterdata.service.MasterCatalogueService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API-DOM-006 (`SAD-COMP-006`) master-catalogue reads (`API-OP-034/035/037`) and Permission #17
 * writes (`API-OP-036/066..070`): Platforms, Company Channels, Publication Targets.
 */
@RestController
@RequestMapping("/api/v1/publishing")
public class MasterCatalogueRestController {

    private final PlatformRepository platformRepository;
    private final CompanyChannelRepository channelRepository;
    private final PublicationTargetRepository targetRepository;
    private final MasterCatalogueService masterCatalogueService;

    public MasterCatalogueRestController(PlatformRepository platformRepository, CompanyChannelRepository channelRepository,
                                          PublicationTargetRepository targetRepository,
                                          MasterCatalogueService masterCatalogueService) {
        this.platformRepository = platformRepository;
        this.channelRepository = channelRepository;
        this.targetRepository = targetRepository;
        this.masterCatalogueService = masterCatalogueService;
    }

    @GetMapping("/platforms")
    public List<Platform> listPlatforms() {
        return platformRepository.findByActiveTrue();
    }

    @GetMapping("/channels")
    public List<CompanyChannel> listChannels() {
        return channelRepository.findByActiveTrue();
    }

    @GetMapping("/targets")
    public List<PublicationTarget> listTargets() {
        return targetRepository.findByActiveTrue();
    }

    @PostMapping("/platforms")
    public ResponseEntity<Platform> createPlatform(@Valid @RequestBody CreatePlatformRequest request,
                                                     @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var platform = masterCatalogueService.createPlatform(principal.user(), request.platformName(), request.catalogueReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(platform);
    }

    @PatchMapping("/platforms/{platformId}")
    public ResponseEntity<Platform> updatePlatform(@PathVariable UUID platformId,
                                                     @Valid @RequestBody UpdatePlatformRequest request,
                                                     @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var platform = masterCatalogueService.updatePlatform(principal.user(), platformId, request.platformName(),
                request.isActive(), request.catalogueReason());
        return ResponseEntity.ok(platform);
    }

    @PostMapping("/channels")
    public ResponseEntity<CompanyChannel> createChannel(@Valid @RequestBody CreateChannelRequest request,
                                                          @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var channel = masterCatalogueService.createChannel(principal.user(), request.channelHandle(), request.catalogueReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(channel);
    }

    @PatchMapping("/channels/{channelId}")
    public ResponseEntity<CompanyChannel> updateChannel(@PathVariable UUID channelId,
                                                          @Valid @RequestBody UpdateChannelRequest request,
                                                          @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var channel = masterCatalogueService.updateChannel(principal.user(), channelId, request.channelHandle(),
                request.isActive(), request.catalogueReason());
        return ResponseEntity.ok(channel);
    }

    @PostMapping("/targets")
    public ResponseEntity<PublicationTarget> createTarget(@Valid @RequestBody CreateTargetRequest request,
                                                            @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var target = masterCatalogueService.createTarget(principal.user(), request.platformId(), request.channelId(),
                request.targetName(), request.catalogueReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(target);
    }

    @PatchMapping("/targets/{targetId}")
    public ResponseEntity<PublicationTarget> setTargetActive(@PathVariable UUID targetId,
                                                               @Valid @RequestBody SetTargetActiveRequest request,
                                                               @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var target = masterCatalogueService.setTargetActive(principal.user(), targetId, request.isActive(),
                request.catalogueReason());
        return ResponseEntity.ok(target);
    }
}
