package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.reporting.dto.PipelineRow;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * docs/changes/CEO_CONTENT_PIPELINE_18_COLUMN_CHANGE.md - builds one {@link PipelineRow} per
 * Content ID for the CEO/Marketing-Manager Content Pipeline dashboard, batch-loading every
 * multi-valued child relation (Camerapersons, Editors, Models, Channels, Platforms, publication
 * events) across ALL plans in a handful of queries rather than per-row, to avoid N+1.
 */
@Service
public class PipelineDashboardService {

    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final ActualPublicationEventRepository actualPublicationEventRepository;

    public PipelineDashboardService(ShootingAssignmentRepository shootingAssignmentRepository,
                                     EditingAssignmentRepository editingAssignmentRepository,
                                     ContentPlanTalentEntryRepository talentEntryRepository,
                                     PlannedOutputRepository plannedOutputRepository,
                                     PlannedOutputPublicationTargetMappingRepository mappingRepository,
                                     ActualPublicationEventRepository actualPublicationEventRepository) {
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.mappingRepository = mappingRepository;
        this.actualPublicationEventRepository = actualPublicationEventRepository;
    }

    @Transactional(readOnly = true)
    public List<PipelineRow> buildRows(List<ContentPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<UUID> planIds = plans.stream().map(ContentPlan::getId).toList();

        Map<UUID, List<User>> camerapersonsByPlan = shootingAssignmentRepository
                .findByContentPlan_IdInAndActiveTrue(planIds).stream()
                .collect(Collectors.groupingBy(a -> a.getContentPlan().getId(),
                        Collectors.mapping(ShootingAssignment::getCameraperson, Collectors.toList())));

        Map<UUID, List<User>> editorsByPlan = editingAssignmentRepository
                .findByContentPlan_IdInAndActiveTrue(planIds).stream()
                .collect(Collectors.groupingBy(a -> a.getContentPlan().getId(),
                        Collectors.mapping(EditingAssignment::getEditor, Collectors.toList())));

        Map<UUID, List<String>> talentByPlan = talentEntryRepository.findByContentPlan_IdIn(planIds).stream()
                .collect(Collectors.groupingBy(t -> t.getContentPlan().getId(),
                        Collectors.mapping(ContentPlanTalentEntry::getTalentName, Collectors.toList())));

        List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan_IdIn(planIds);
        Map<UUID, UUID> planIdByOutputId = outputs.stream()
                .collect(Collectors.toMap(PlannedOutput::getId, o -> o.getContentPlan().getId()));
        List<UUID> outputIds = outputs.stream().map(PlannedOutput::getId).toList();

        Map<UUID, List<PlannedOutputPublicationTargetMapping>> mappingsByPlan = new HashMap<>();
        if (!outputIds.isEmpty()) {
            for (PlannedOutputPublicationTargetMapping mapping : mappingRepository.findByPlannedOutput_IdIn(outputIds)) {
                UUID planId = planIdByOutputId.get(mapping.getPlannedOutput().getId());
                mappingsByPlan.computeIfAbsent(planId, k -> new ArrayList<>()).add(mapping);
            }
        }

        Map<UUID, Long> publicationEventCountByPlan = actualPublicationEventRepository
                .findByContentPlan_IdIn(planIds).stream()
                .collect(Collectors.groupingBy(e -> e.getContentPlan().getId(), Collectors.counting()));

        List<PipelineRow> rows = new ArrayList<>(plans.size());
        for (ContentPlan plan : plans) {
            UUID planId = plan.getId();
            rows.add(buildRow(plan,
                    camerapersonsByPlan.getOrDefault(planId, List.of()),
                    editorsByPlan.getOrDefault(planId, List.of()),
                    talentByPlan.getOrDefault(planId, List.of()),
                    mappingsByPlan.getOrDefault(planId, List.of()),
                    publicationEventCountByPlan.getOrDefault(planId, 0L)));
        }
        return rows;
    }

    private PipelineRow buildRow(ContentPlan plan, List<User> camerapersons, List<User> editors,
                                  List<String> talent, List<PlannedOutputPublicationTargetMapping> mappings,
                                  long publicationEventCount) {
        String sku = plan.isSkuNotApplicable() ? "N/A" : blankToDash(plan.getSkuReference());
        String category = blankToDash(plan.getCategoryText());
        String referenceLink = plan.getIdea().getReferenceLink();
        boolean referenceLinkIsUrl = referenceLink != null
                && (referenceLink.startsWith("http://") || referenceLink.startsWith("https://"));

        String channels = mappings.stream()
                .map(m -> m.getPublicationTarget().getChannel().getChannelHandle())
                .distinct().sorted().collect(Collectors.joining(", "));
        String platforms = mappings.stream()
                .map(m -> m.getPublicationTarget().getPlatform().getPlatformName())
                .distinct().sorted().collect(Collectors.joining(", "));
        String cameraPersonNames = camerapersons.stream().map(User::getFullName).distinct()
                .collect(Collectors.joining(", "));
        String editorNames = editors.stream().map(User::getFullName).distinct().collect(Collectors.joining(", "));
        String modelNames = String.join(", ", talent);
        String actor = plan.getPreparedBy() != null ? plan.getPreparedBy().getFullName() : "—";

        WorkflowStatus status = plan.getWorkflowInstance().getCurrentStatusCode();
        String performanceState = switch (status) {
            case PP -> "Pending";
            case PFUP -> "Updated";
            case COMP -> "Completed";
            default -> "Not Yet Applicable";
        };
        boolean performanceLinkEligible = status == WorkflowStatus.PP || status == WorkflowStatus.PFUP
                || status == WorkflowStatus.COMP;

        // No canonical Content-ID-level Actual Live Date exists yet (see change record) - a
        // neutral non-date indicator is shown rather than arbitrarily picking earliest/latest.
        String liveDate = publicationEventCount == 0 ? "—" : "Published";

        return new PipelineRow(plan.getId(), plan.getContentId(), sku, plan.getIdea().getTitle(), referenceLink,
                referenceLinkIsUrl, category, blankToDash(channels), actor, blankToDash(cameraPersonNames),
                blankToDash(modelNames), blankToDash(editorNames), plan.getFolderLink(), plan.getPlannedLiveDate(),
                plan.getPlannedShootDate(), plan.getPlannedEditDate(), liveDate, blankToDash(platforms),
                performanceState, performanceLinkEligible, status.getStatusName());
    }

    private String blankToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
