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
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.reporting.dto.PipelineRow;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final ActualPublicationEventRepository actualPublicationEventRepository;
    private final ReviewCycleRepository reviewCycleRepository;

    public PipelineDashboardService(ShootingAssignmentRepository shootingAssignmentRepository,
                                     EditingAssignmentRepository editingAssignmentRepository,
                                     ContentPlanTalentEntryRepository talentEntryRepository,
                                     PlannedOutputRepository plannedOutputRepository,
                                     PlannedOutputPublicationTargetMappingRepository mappingRepository,
                                     ActualPublicationEventRepository actualPublicationEventRepository,
                                     ReviewCycleRepository reviewCycleRepository) {
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.mappingRepository = mappingRepository;
        this.actualPublicationEventRepository = actualPublicationEventRepository;
        this.reviewCycleRepository = reviewCycleRepository;
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

        // Actual Live Date: earliest ORIGINAL-type publication event per plan ("when it first
        // went live") - the one defensible single value across a Content ID's potentially many
        // events/targets (see docs/changes/CEO_CONTENT_PIPELINE_18_COLUMN_CHANGE.md).
        Map<UUID, LocalDate> actualLiveDateByPlan = actualPublicationEventRepository
                .findByContentPlan_IdIn(planIds).stream()
                .filter(e -> e.getEventType() == PublicationEventType.ORIGINAL)
                .collect(Collectors.groupingBy(e -> e.getContentPlan().getId(),
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparing(ActualPublicationEvent::getActualPublicationTimestamp)),
                                earliest -> earliest.map(e -> LocalDate.ofInstant(e.getActualPublicationTimestamp(), BUSINESS_ZONE))
                                        .orElse(null))));

        // Actual Shoot/Edit Date: the date the Shoot/Edit Review gate was APPROVED - "when the
        // stage was actually completed and signed off", batch-loaded across every plan's workflow
        // instance in one query.
        List<UUID> workflowInstanceIds = plans.stream().map(p -> p.getWorkflowInstance().getId()).toList();
        Map<UUID, LocalDate> actualShootDateByWorkflowInstance = new HashMap<>();
        Map<UUID, LocalDate> actualEditDateByWorkflowInstance = new HashMap<>();
        for (ReviewCycle cycle : reviewCycleRepository.findByWorkflowInstance_IdInAndGateTypeInAndDecision(
                workflowInstanceIds, List.of(GateType.SHOOT_REVIEW, GateType.EDIT_REVIEW), "APPROVED")) {
            LocalDate decidedDate = LocalDate.ofInstant(cycle.getDecidedAt(), BUSINESS_ZONE);
            UUID workflowInstanceId = cycle.getWorkflowInstance().getId();
            Map<UUID, LocalDate> target = cycle.getGateType() == GateType.SHOOT_REVIEW
                    ? actualShootDateByWorkflowInstance : actualEditDateByWorkflowInstance;
            // Defensive only: a gate is approved at most once per plan in practice (ERD-CON-039
            // decisions are immutable) - latest wins if that invariant is ever violated.
            target.merge(workflowInstanceId, decidedDate, (a, b) -> a.isAfter(b) ? a : b);
        }

        List<PipelineRow> rows = new ArrayList<>(plans.size());
        for (ContentPlan plan : plans) {
            UUID planId = plan.getId();
            UUID workflowInstanceId = plan.getWorkflowInstance().getId();
            rows.add(buildRow(plan,
                    camerapersonsByPlan.getOrDefault(planId, List.of()),
                    editorsByPlan.getOrDefault(planId, List.of()),
                    talentByPlan.getOrDefault(planId, List.of()),
                    mappingsByPlan.getOrDefault(planId, List.of()),
                    actualShootDateByWorkflowInstance.get(workflowInstanceId),
                    actualEditDateByWorkflowInstance.get(workflowInstanceId),
                    actualLiveDateByPlan.get(planId)));
        }
        return rows;
    }

    private PipelineRow buildRow(ContentPlan plan, List<User> camerapersons, List<User> editors,
                                  List<String> talent, List<PlannedOutputPublicationTargetMapping> mappings,
                                  LocalDate actualShootDate, LocalDate actualEditDate, LocalDate actualLiveDate) {
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

        return new PipelineRow(plan.getId(), plan.getContentId(), sku, plan.getIdea().getTitle(), referenceLink,
                referenceLinkIsUrl, category, blankToDash(channels), actor, blankToDash(cameraPersonNames),
                blankToDash(modelNames), blankToDash(editorNames), plan.getFolderLink(), plan.getPlannedShootDate(),
                plan.getPlannedEditDate(), plan.getPlannedLiveDate(), dateToDash(actualShootDate),
                dateToDash(actualEditDate), dateToDash(actualLiveDate), blankToDash(platforms),
                performanceState, performanceLinkEligible, status.getStatusName());
    }

    private String blankToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private String dateToDash(LocalDate value) {
        return value == null ? "—" : value.toString();
    }
}
