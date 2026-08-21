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
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.domain.PublicationEvidenceCorrection;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.publishing.repository.PublicationEvidenceCorrectionRepository;
import com.kcpc.mkt.reporting.dto.PipelineChannelStatus;
import com.kcpc.mkt.reporting.dto.PipelineFilterCriteria;
import com.kcpc.mkt.reporting.dto.PipelinePlatformSummary;
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
import java.util.Set;
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
    private final PublicationEvidenceCorrectionRepository evidenceCorrectionRepository;
    private final ReviewCycleRepository reviewCycleRepository;

    public PipelineDashboardService(ShootingAssignmentRepository shootingAssignmentRepository,
                                     EditingAssignmentRepository editingAssignmentRepository,
                                     ContentPlanTalentEntryRepository talentEntryRepository,
                                     PlannedOutputRepository plannedOutputRepository,
                                     PlannedOutputPublicationTargetMappingRepository mappingRepository,
                                     ActualPublicationEventRepository actualPublicationEventRepository,
                                     PublicationEvidenceCorrectionRepository evidenceCorrectionRepository,
                                     ReviewCycleRepository reviewCycleRepository) {
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.mappingRepository = mappingRepository;
        this.actualPublicationEventRepository = actualPublicationEventRepository;
        this.evidenceCorrectionRepository = evidenceCorrectionRepository;
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

        List<ActualPublicationEvent> originalEvents = actualPublicationEventRepository.findByContentPlan_IdIn(planIds)
                .stream().filter(e -> e.getEventType() == PublicationEventType.ORIGINAL).toList();

        // Actual Live Date: earliest ORIGINAL-type publication event per plan ("when it first
        // went live") - the one defensible single value across a Content ID's potentially many
        // events/targets (see docs/changes/CEO_CONTENT_PIPELINE_18_COLUMN_CHANGE.md).
        Map<UUID, LocalDate> actualLiveDateByPlan = originalEvents.stream()
                .collect(Collectors.groupingBy(e -> e.getContentPlan().getId(),
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparing(ActualPublicationEvent::getActualPublicationTimestamp)),
                                earliest -> earliest.map(e -> LocalDate.ofInstant(e.getActualPublicationTimestamp(), BUSINESS_ZONE))
                                        .orElse(null))));

        // ENG-075: Platforms column icon+popover - per (plan, Publication Target), the single
        // representative ORIGINAL event (latest by timestamp, in the rare case the same channel
        // was mapped from more than one Planned Output and each got its own event) and its CURRENT
        // effective Evidence URL (the latest PublicationEvidenceCorrection if one exists, else the
        // event's own URL) - never a planned-only mapping treated as published.
        Map<UUID, Map<UUID, ActualPublicationEvent>> latestEventByPlanAndTarget = new HashMap<>();
        for (ActualPublicationEvent e : originalEvents) {
            latestEventByPlanAndTarget
                    .computeIfAbsent(e.getContentPlan().getId(), k -> new HashMap<>())
                    .merge(e.getPublicationTarget().getId(), e,
                            (a, b) -> a.getActualPublicationTimestamp().isAfter(b.getActualPublicationTimestamp()) ? a : b);
        }
        Set<UUID> representativeEventIds = latestEventByPlanAndTarget.values().stream()
                .flatMap(m -> m.values().stream()).map(ActualPublicationEvent::getId).collect(Collectors.toSet());
        Map<UUID, PublicationEvidenceCorrection> latestCorrectionByEventId = new HashMap<>();
        if (!representativeEventIds.isEmpty()) {
            for (PublicationEvidenceCorrection corr : evidenceCorrectionRepository.findByEvent_IdIn(representativeEventIds)) {
                latestCorrectionByEventId.merge(corr.getEvent().getId(), corr,
                        (a, b) -> a.getCorrectedAt().isAfter(b.getCorrectedAt()) ? a : b);
            }
        }

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
                    actualLiveDateByPlan.get(planId),
                    latestEventByPlanAndTarget.getOrDefault(planId, Map.of()),
                    latestCorrectionByEventId));
        }
        return rows;
    }

    private PipelineRow buildRow(ContentPlan plan, List<User> camerapersons, List<User> editors,
                                  List<String> talent, List<PlannedOutputPublicationTargetMapping> mappings,
                                  LocalDate actualShootDate, LocalDate actualEditDate, LocalDate actualLiveDate,
                                  Map<UUID, ActualPublicationEvent> latestEventByTarget,
                                  Map<UUID, PublicationEvidenceCorrection> latestCorrectionByEventId) {
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

        String priority = plan.getContentPriority() == null ? null : plan.getContentPriority().name();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Integer delayDays = delayDays(status, plan, today);

        List<PipelinePlatformSummary> platformSummaries = buildPlatformSummaries(mappings, latestEventByTarget,
                latestCorrectionByEventId);

        return new PipelineRow(plan.getId(), plan.getContentId(), sku, plan.getIdea().getTitle(), referenceLink,
                referenceLinkIsUrl, category, blankToDash(channels), actor, blankToDash(cameraPersonNames),
                blankToDash(modelNames), blankToDash(editorNames), plan.getFolderLink(), plan.getPlannedShootDate(),
                plan.getPlannedEditDate(), plan.getPlannedLiveDate(), dateToDash(actualShootDate),
                dateToDash(actualEditDate), dateToDash(actualLiveDate), blankToDash(platforms),
                performanceState, performanceLinkEligible, status.getStatusName(), priority,
                delayDays != null, delayDays, platformSummaries);
    }

    /**
     * ENG-075: groups this plan's distinct planned (Platform, Channel) pairs (deduplicated by
     * Publication Target id, since the same target can be mapped from more than one Planned
     * Output) by Platform, resolving each Channel's real publication status from the already
     * batch-loaded representative-event/correction maps. A channel is "published" only when a real
     * ORIGINAL event exists AND its effective Evidence URL is non-blank - a planned-only mapping
     * with no event is always "not published", never inferred otherwise.
     * ENG-082: {@code public static} (was {@code private}) so {@code DeliverableMvcController} can
     * reuse this exact same plan-scoped transform for the Content Detail Publishing tab's
     * Platform×Channel chips, instead of re-deriving the "which channel is published, using the
     * correction-resolved effective Evidence URL" rule a second time. Takes zero repository
     * dependencies and does zero DB access itself either way - callers do their own batch-loading
     * of {@code mappings}/{@code latestEventByTarget}/{@code latestCorrectionByEventId} first
     * (see {@link #buildRows} for the multi-plan batch form; a single-plan caller just scopes the
     * same three queries to one plan instead of many).
     */
    public static List<PipelinePlatformSummary> buildPlatformSummaries(List<PlannedOutputPublicationTargetMapping> mappings,
                                                                   Map<UUID, ActualPublicationEvent> latestEventByTarget,
                                                                   Map<UUID, PublicationEvidenceCorrection> latestCorrectionByEventId) {
        Map<String, Map<UUID, PublicationTarget>> targetsByPlatform = new java.util.LinkedHashMap<>();
        for (PlannedOutputPublicationTargetMapping m : mappings) {
            PublicationTarget target = m.getPublicationTarget();
            targetsByPlatform.computeIfAbsent(target.getPlatform().getPlatformName(), k -> new java.util.LinkedHashMap<>())
                    .putIfAbsent(target.getId(), target);
        }

        List<PipelinePlatformSummary> summaries = new ArrayList<>();
        for (var entry : targetsByPlatform.entrySet()) {
            List<PipelineChannelStatus> channelStatuses = new ArrayList<>();
            int publishedCount = 0;
            for (PublicationTarget target : entry.getValue().values()) {
                ActualPublicationEvent event = latestEventByTarget.get(target.getId());
                String effectiveUrl = null;
                if (event != null) {
                    PublicationEvidenceCorrection latestCorrection = latestCorrectionByEventId.get(event.getId());
                    effectiveUrl = latestCorrection != null ? latestCorrection.getCorrectedEvidenceUrl() : event.getEvidenceUrl();
                }
                boolean published = effectiveUrl != null && !effectiveUrl.isBlank();
                if (published) {
                    publishedCount++;
                }
                channelStatuses.add(new PipelineChannelStatus(target.getChannel().getChannelHandle(), published,
                        published ? effectiveUrl : null));
            }
            summaries.add(new PipelinePlatformSummary(entry.getKey(), channelStatuses.size(), publishedCount, channelStatuses));
        }
        return summaries;
    }

    /**
     * Pipeline dashboard "Attention / Delayed" indicator - display-only, never a persisted or
     * workflow status (BFD's only real status catalogue entry for "delayed" is the supplementary
     * DLY flag, never used here). Compares the plan's already-stored Planned date for whichever
     * stage it is CURRENTLY active in against today - the same "past planned date, not yet past
     * that stage" rule {@code LandingMvcController}'s My Work already applies per-employee-task,
     * just evaluated here at the whole-plan level for the management dashboard. Planning/terminal/
     * already-resolved statuses have no single applicable Planned date and are never flagged.
     */
    /**
     * ENG-087: {@code public static} (was {@code private}) - same reasoning as
     * {@link #buildPlatformSummaries}: a pure transform (no repository dependency, no DB access),
     * reused verbatim by the Team Workload dashboard's Assignee Load "Delayed Tasks" column rather
     * than re-deriving the "planned date for this stage vs today" rule a fourth time (Pipeline row
     * delay, My Work delay, Team Workload's old native-SQL delay CASE, and this).
     */
    public static Integer delayDays(WorkflowStatus status, ContentPlan plan, LocalDate today) {
        LocalDate relevantPlannedDate = switch (status) {
            case SA, SIP, SRV -> plan.getPlannedShootDate();
            case EA, ED, ERV -> plan.getPlannedEditDate();
            case RFP, PUBG -> plan.getPlannedLiveDate();
            default -> null;
        };
        if (relevantPlannedDate == null || !relevantPlannedDate.isBefore(today)) {
            return null;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(relevantPlannedDate, today);
    }

    private String blankToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private String dateToDash(LocalDate value) {
        return value == null ? "—" : value.toString();
    }

    /**
     * ENG-071: Content Pipeline per-column filter/sort - operates purely over the already-built
     * {@link PipelineRow} list from {@link #buildRows}, never touching that method or issuing any
     * further query. This is a display/reporting-layer filter over pre-joined display strings, not
     * a workflow rule - it changes what a Marketing Manager/CEO chooses to LOOK AT on the
     * dashboard, never what any Content Plan's actual status/assignment/permission is. Multi-valued
     * People/Publication columns (Cameraperson(s)/Model(s)/Video Editor(s)/Platform/Channel) match
     * as a case-insensitive substring against their comma-joined display string, since that's the
     * only form this data exists in on a {@link PipelineRow} - a row with 3 Camerapersons matches a
     * Cameraperson filter if ANY of the 3 names contains the filter text. Each Planned/Actual date
     * column has its OWN independent range (ENG-071 - one filter popup per column, replacing
     * ENG-070's single combined Planned/Actual range keyed off Live Date only).
     */
    public List<PipelineRow> filterAndSort(List<PipelineRow> rows, PipelineFilterCriteria criteria) {
        List<PipelineRow> result = rows.stream().filter(row -> matches(row, criteria)).collect(Collectors.toList());
        Comparator<PipelineRow> comparator = comparatorFor(criteria.sortBy());
        if (comparator != null) {
            if ("desc".equalsIgnoreCase(criteria.sortDir())) {
                comparator = comparator.reversed();
            }
            result.sort(comparator);
        }
        return result;
    }

    private boolean matches(PipelineRow row, PipelineFilterCriteria c) {
        if (notBlank(c.search())) {
            String q = c.search().trim().toLowerCase();
            String haystack = (nullToEmpty(row.getContentId()) + " " + nullToEmpty(row.getSku()) + " "
                    + nullToEmpty(row.getIdeaTitle()) + " " + nullToEmpty(row.getCategory())).toLowerCase();
            if (!haystack.contains(q)) {
                return false;
            }
        }
        if (notBlank(c.stage()) && !"all".equalsIgnoreCase(c.stage()) && !matchesStage(row, c.stage())) {
            return false;
        }
        if (notBlank(c.sku()) && !containsIgnoreCase(row.getSku(), c.sku())) {
            return false;
        }
        if (notBlank(c.idea()) && !containsIgnoreCase(row.getIdeaTitle(), c.idea())) {
            return false;
        }
        if (notBlank(c.priority()) && !c.priority().equalsIgnoreCase(row.getPriority())) {
            return false;
        }
        if (notBlank(c.cameraperson()) && !containsIgnoreCase(row.getCameraPersons(), c.cameraperson())) {
            return false;
        }
        if (notBlank(c.model()) && !containsIgnoreCase(row.getModels(), c.model())) {
            return false;
        }
        if (notBlank(c.videoEditor()) && !containsIgnoreCase(row.getVideoEditors(), c.videoEditor())) {
            return false;
        }
        if (notBlank(c.platform()) && !containsIgnoreCase(row.getPlatforms(), c.platform())) {
            return false;
        }
        if (notBlank(c.channel()) && !containsIgnoreCase(row.getChannels(), c.channel())) {
            return false;
        }
        if (notBlank(c.status()) && !c.status().equalsIgnoreCase(row.getStatus())) {
            return false;
        }
        if (notBlank(c.performanceState()) && !c.performanceState().equalsIgnoreCase(row.getPerformanceState())) {
            return false;
        }
        if (c.delayedOnly() && !row.isDelayed()) {
            return false;
        }
        if ((c.plannedShootFrom() != null || c.plannedShootTo() != null)
                && !inRange(row.getPlannedShootDate(), c.plannedShootFrom(), c.plannedShootTo())) {
            return false;
        }
        if ((c.plannedEditFrom() != null || c.plannedEditTo() != null)
                && !inRange(row.getPlannedEditDate(), c.plannedEditFrom(), c.plannedEditTo())) {
            return false;
        }
        if ((c.plannedLiveFrom() != null || c.plannedLiveTo() != null)
                && !inRange(row.getPlannedLiveDate(), c.plannedLiveFrom(), c.plannedLiveTo())) {
            return false;
        }
        if ((c.actualShootFrom() != null || c.actualShootTo() != null)
                && !inRange(parseDashedDate(row.getActualShootDate()), c.actualShootFrom(), c.actualShootTo())) {
            return false;
        }
        if ((c.actualEditFrom() != null || c.actualEditTo() != null)
                && !inRange(parseDashedDate(row.getActualEditDate()), c.actualEditFrom(), c.actualEditTo())) {
            return false;
        }
        if ((c.actualLiveFrom() != null || c.actualLiveTo() != null)
                && !inRange(parseDashedDate(row.getActualLiveDate()), c.actualLiveFrom(), c.actualLiveTo())) {
            return false;
        }
        return true;
    }

    private boolean inRange(LocalDate value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        return to == null || !value.isAfter(to);
    }

    private LocalDate parseDashedDate(String value) {
        if (value == null || "—".equals(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    // ENG-073: Stage filter tabs - coarse groupings of the row's own friendly status text
    // (WorkflowStatus.getStatusName(), already on every PipelineRow), never a new backend status.
    // "attention" reuses the existing delay computation (ENG-069) rather than any status text.
    private static final java.util.Set<String> STAGE_PLANNING = java.util.Set.of("Planning", "Planning Review", "Planning Approved");
    private static final java.util.Set<String> STAGE_SHOOT = java.util.Set.of("Shoot Assigned", "Shoot In Progress", "Shoot Review", "Shoot Approved");
    private static final java.util.Set<String> STAGE_EDIT = java.util.Set.of("Edit Assigned", "Editing", "Edit Review", "Edit Approved");
    private static final java.util.Set<String> STAGE_PUBLISHING = java.util.Set.of("Ready for Publishing", "Publishing");
    private static final java.util.Set<String> STAGE_PERFORMANCE = java.util.Set.of("Performance Pending", "Performance Update");

    private boolean matchesStage(PipelineRow row, String stage) {
        return switch (stage.toLowerCase()) {
            case "attention" -> row.isDelayed();
            case "planning" -> STAGE_PLANNING.contains(row.getStatus());
            case "shoot" -> STAGE_SHOOT.contains(row.getStatus());
            case "edit" -> STAGE_EDIT.contains(row.getStatus());
            case "publishing" -> STAGE_PUBLISHING.contains(row.getStatus());
            case "performance" -> STAGE_PERFORMANCE.contains(row.getStatus());
            case "completed" -> "Completed".equals(row.getStatus());
            default -> true;
        };
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.trim().toLowerCase());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final java.util.Map<String, Integer> PRIORITY_RANK = java.util.Map.of("HIGH", 0, "MEDIUM", 1, "LOW", 2);

    private Comparator<PipelineRow> comparatorFor(String sortBy) {
        if (sortBy == null) {
            return null;
        }
        return switch (sortBy) {
            case "contentId" -> Comparator.comparing(PipelineRow::getContentId, Comparator.nullsLast(Comparator.naturalOrder()));
            case "sku" -> Comparator.comparing(PipelineRow::getSku, Comparator.nullsLast(Comparator.naturalOrder()));
            case "idea" -> Comparator.comparing(PipelineRow::getIdeaTitle, Comparator.nullsLast(Comparator.naturalOrder()));
            case "priority" -> Comparator.comparing(
                    row -> PRIORITY_RANK.getOrDefault(row.getPriority(), Integer.MAX_VALUE),
                    Comparator.naturalOrder());
            case "plannedShootDate" -> Comparator.comparing(PipelineRow::getPlannedShootDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "plannedEditDate" -> Comparator.comparing(PipelineRow::getPlannedEditDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "plannedLiveDate" -> Comparator.comparing(PipelineRow::getPlannedLiveDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "actualShootDate" -> Comparator.comparing(PipelineRow::getActualShootDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "actualEditDate" -> Comparator.comparing(PipelineRow::getActualEditDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "actualLiveDate" -> Comparator.comparing(PipelineRow::getActualLiveDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(PipelineRow::getStatus, Comparator.nullsLast(Comparator.naturalOrder()));
            case "referenceLink" -> Comparator.comparing(PipelineRow::getReferenceLink, Comparator.nullsLast(Comparator.naturalOrder()));
            case "category" -> Comparator.comparing(PipelineRow::getCategory, Comparator.nullsLast(Comparator.naturalOrder()));
            case "channels" -> Comparator.comparing(PipelineRow::getChannels, Comparator.nullsLast(Comparator.naturalOrder()));
            case "actor" -> Comparator.comparing(PipelineRow::getActor, Comparator.nullsLast(Comparator.naturalOrder()));
            case "cameraPersons" -> Comparator.comparing(PipelineRow::getCameraPersons, Comparator.nullsLast(Comparator.naturalOrder()));
            case "models" -> Comparator.comparing(PipelineRow::getModels, Comparator.nullsLast(Comparator.naturalOrder()));
            case "videoEditors" -> Comparator.comparing(PipelineRow::getVideoEditors, Comparator.nullsLast(Comparator.naturalOrder()));
            case "platforms" -> Comparator.comparing(PipelineRow::getPlatforms, Comparator.nullsLast(Comparator.naturalOrder()));
            case "performanceState" -> Comparator.comparing(PipelineRow::getPerformanceState, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
    }
}
