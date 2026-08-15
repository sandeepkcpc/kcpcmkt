package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.reporting.dto.ContentPlanExport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * SAD-ADR-008: JSON full-graph export per Content ID. CSV/XLSX flat export is deferred - see
 * docs/IMPLEMENTATION_DECISIONS.md. Gated to native CEO/MM authority (export carries no
 * dedicated entry in the 17-permission catalogue).
 */
@Service
public class ExportService {

    private final ContentPlanRepository contentPlanRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final PersonalMarkAttributionRepository markAttributionRepository;
    private final ActualPublicationEventRepository eventRepository;
    private final AuthorizationService authorizationService;

    public ExportService(ContentPlanRepository contentPlanRepository, PlannedOutputRepository plannedOutputRepository,
                          PersonalMarkAttributionRepository markAttributionRepository,
                          ActualPublicationEventRepository eventRepository, AuthorizationService authorizationService) {
        this.contentPlanRepository = contentPlanRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.markAttributionRepository = markAttributionRepository;
        this.eventRepository = eventRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public ContentPlanExport exportContentPlan(User requester, UUID contentPlanId) {
        authorizationService.requireNativeAuthority(requester, "Export");
        ContentPlan plan = contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));

        var outputs = plannedOutputRepository.findByContentPlan(plan).stream()
                .map(o -> new ContentPlanExport.OutputExport(o.getId(), o.getOutputType().name(),
                        o.getReelType() != null ? o.getReelType().name() : null))
                .toList();
        var marks = markAttributionRepository.findByContentPlan(plan).stream()
                .map(ContentPlanExport.MarkExport::from).toList();
        var events = eventRepository.findByContentPlan(plan).stream()
                .map(ContentPlanExport.PublicationExport::from).toList();

        return new ContentPlanExport(plan.getId(), plan.getContentId(), plan.getWorkflowInstance().getCurrentStatusCode(),
                outputs, marks, events);
    }
}
