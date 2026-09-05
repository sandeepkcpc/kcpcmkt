package com.kcpc.mkt.planning.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.common.util.UuidV7;
import com.kcpc.mkt.drive.repository.ContentDriveProvisioningRepository;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.service.OperationalEligibilityService;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.planning.domain.ContentPriority;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping;
import com.kcpc.mkt.planning.domain.PlanningMode;
import com.kcpc.mkt.planning.domain.PlanningPreparer;
import com.kcpc.mkt.planning.domain.ReelType;
import com.kcpc.mkt.planning.dto.TalentSelection;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.planning.repository.PlanningPreparerRepository;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import com.kcpc.mkt.workflow.service.WorkflowTransitionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BRS-REQ-020..030, BRS-REQ-086: Planning stage (Stage 3) parameters, Planned Outputs,
 * publication scope, initial shooting assignment, and the Planning Review gate.
 */
@Service
public class PlanningService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ContentPlanRepository contentPlanRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final PublicationTargetRepository publicationTargetRepository;
    private final PlanningPreparerRepository planningPreparerRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionService workflowService;
    private final AuthorizationService authorizationService;
    private final OperationalEligibilityService operationalEligibilityService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ActualPublicationEventRepository actualPublicationEventRepository;
    private final ContentDriveProvisioningRepository driveProvisioningRepository;
    private final com.kcpc.mkt.masterdata.service.CategoryService categoryService;

    public PlanningService(ContentPlanRepository contentPlanRepository,
                            ContentPlanTalentEntryRepository talentEntryRepository,
                            PlannedOutputRepository plannedOutputRepository,
                            PlannedOutputPublicationTargetMappingRepository mappingRepository,
                            PublicationTargetRepository publicationTargetRepository,
                            PlanningPreparerRepository planningPreparerRepository,
                            ShootingAssignmentRepository shootingAssignmentRepository,
                            ReviewCycleRepository reviewCycleRepository,
                            WorkflowTransitionService workflowService, AuthorizationService authorizationService,
                            OperationalEligibilityService operationalEligibilityService,
                            AuditService auditService, UserRepository userRepository,
                            ActualPublicationEventRepository actualPublicationEventRepository,
                            ContentDriveProvisioningRepository driveProvisioningRepository,
                            com.kcpc.mkt.masterdata.service.CategoryService categoryService) {
        this.contentPlanRepository = contentPlanRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.mappingRepository = mappingRepository;
        this.publicationTargetRepository = publicationTargetRepository;
        this.planningPreparerRepository = planningPreparerRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
        this.operationalEligibilityService = operationalEligibilityService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.actualPublicationEventRepository = actualPublicationEventRepository;
        this.driveProvisioningRepository = driveProvisioningRepository;
        this.categoryService = categoryService;
    }

    private ContentPlan requireContentPlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    private Optional<PermissionGrant> requirePlanningExecutionAuthority(User user, WorkflowInstance workflowInstance) {
        return authorizationService.requireAuthority(user, OperationalPermission.PERM_02_PLANNING_EXECUTION,
                LifecycleStage.PLANNING, workflowInstance);
    }

    private void recordPreparer(ContentPlan plan, User user) {
        boolean alreadyRecorded = planningPreparerRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getPreparer().getId().equals(user.getId()));
        if (!alreadyRecorded) {
            planningPreparerRepository.save(new PlanningPreparer(plan, user));
        }
    }

    /** BRS-REQ-021: Category (free-text), Priority, SKU, Models/Talent, Drive Link. */
    @Transactional
    public ContentPlan updateParameters(User user, UUID contentPlanId, String categoryText, ContentPriority priority,
                                         String skuReference, boolean skuNotApplicable,
                                         List<TalentSelection> talentSelections, String folderLink) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        requirePlanningExecutionAuthority(user, plan.getWorkflowInstance());
        recordPreparer(plan, user);

        // ENG-094: same rule as IdeaService#approve - blank/null stays allowed, a non-blank value
        // must match a currently-active Category Catalogue entry.
        categoryService.requireActiveNameOrBlank(categoryText);
        plan.setCategoryText(categoryText);
        // Content Priority defaults to LOW when not explicitly provided - never left/cleared to null.
        // A retired priority (see ContentPriority) is closed to new use, but re-submitting the
        // value this plan ALREADY carries stays allowed: an existing MEDIUM plan must remain
        // editable (category, SKU, talent, dates) without that edit silently re-grading it, and
        // without the edit being refused outright. Only newly introducing a retired value fails.
        if (priority != null && !priority.isSelectable() && priority != plan.getContentPriority()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Priority " + priority.name() + " is retired and can no longer be assigned. Existing "
                            + priority.name() + " plans are unaffected.");
        }
        plan.setContentPriority(priority != null ? priority : ContentPriority.LOW);
        plan.setSku(skuReference, skuNotApplicable);
        // Once structured Drive provisioning has a known root folder, folder_link becomes a
        // derived/compatibility mirror of that root (DriveProvisioningService keeps it synced) -
        // an ordinary Planning edit must never silently diverge it from the canonical structured
        // record. Only the PERM_13 admin relink action may change the root folder mapping (it
        // updates the structured record first, then resyncs this field). Legacy content with no
        // structured provisioning record at all is completely unaffected - folder_link there stays
        // exactly as free-text-editable as it always was.
        boolean structuredRootKnown = driveProvisioningRepository.findByContentPlan(plan)
                .map(p -> p.getRootFolderId() != null).orElse(false);
        if (!structuredRootKnown) {
            plan.setFolderLink(folderLink);
        }
        plan.setPreparedBy(user);

        talentEntryRepository.deleteByContentPlan(plan);
        if (talentSelections != null) {
            for (TalentSelection selection : talentSelections) {
                if (selection.talentName() != null && !selection.talentName().isBlank()) {
                    // ENG-067: talentUserId is null on the frozen REST plain-name-string path;
                    // populated when the MVC Model(s) picker (real Model-role users only) supplied it.
                    User talentUser = selection.talentUserId() == null ? null
                            : userRepository.findById(selection.talentUserId()).orElse(null);
                    talentEntryRepository.save(new ContentPlanTalentEntry(plan, selection.talentName(), talentUser));
                }
            }
        }
        contentPlanRepository.save(plan);
        auditService.record(user, Optional.empty(), "PLANNING", "PLANNING_PARAMETERS_UPDATED", "content_plans",
                plan.getId(), null);
        return plan;
    }

    /**
     * Single-form Planning submission: Parameters and Schedule (Standard or Urgent) saved as one
     * user action instead of two separate form posts. Delegates to {@link #updateParameters} and
     * {@link #setStandardSchedule}/{@link #setUrgentSchedule} unchanged - same authority checks,
     * same validation, same audit trail (one entry per underlying save), just invoked together.
     */
    @Transactional
    public ContentPlan savePlan(User user, UUID contentPlanId, String categoryText, ContentPriority priority,
                                 String skuReference, boolean skuNotApplicable, List<TalentSelection> talentSelections,
                                 String folderLink, PlanningMode planningMode, LocalDate plannedLiveDate,
                                 LocalDate shootDate, LocalDate editDate, String urgencyReason) {
        updateParameters(user, contentPlanId, categoryText, priority, skuReference, skuNotApplicable, talentSelections,
                folderLink);
        if (planningMode == PlanningMode.URGENT) {
            if (shootDate == null || editDate == null || urgencyReason == null || urgencyReason.isBlank()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Urgent Planning Mode requires Shoot Date, Edit Date and Urgency Reason");
            }
            return setUrgentSchedule(user, contentPlanId, plannedLiveDate, shootDate, editDate, urgencyReason);
        }
        return setStandardSchedule(user, contentPlanId, plannedLiveDate, shootDate, editDate);
    }

    /**
     * BRS-REQ-027/086: STANDARD defaults shoot=live-5d/edit=live-2d, both overridable. A target
     * live date fewer than 5 days from the current IST business date requires URGENT.
     */
    @Transactional
    public ContentPlan setStandardSchedule(User user, UUID contentPlanId, LocalDate plannedLiveDate,
                                            LocalDate shootDateOverride, LocalDate editDateOverride) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        requirePlanningExecutionAuthority(user, plan.getWorkflowInstance());
        requireFutureLiveDate(plannedLiveDate);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (plannedLiveDate.isBefore(today.plusDays(5))) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A Planned Live Date fewer than 5 days away requires Urgent Planning Mode (BRS-REQ-093)");
        }
        LocalDate shootDate = shootDateOverride != null ? shootDateOverride : plannedLiveDate.minusDays(5);
        LocalDate editDate = editDateOverride != null ? editDateOverride : plannedLiveDate.minusDays(2);
        plan.setPlanningScheduleStandard(plannedLiveDate, shootDate, editDate);
        contentPlanRepository.save(plan);
        auditService.record(user, Optional.empty(), "PLANNING", "SCHEDULE_SET_STANDARD", "content_plans",
                plan.getId(), null);
        return plan;
    }

    @Transactional
    public ContentPlan setUrgentSchedule(User user, UUID contentPlanId, LocalDate plannedLiveDate,
                                          LocalDate shootDate, LocalDate editDate, String urgencyReason) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        requirePlanningExecutionAuthority(user, plan.getWorkflowInstance());
        requireFutureLiveDate(plannedLiveDate);
        plan.setPlanningScheduleUrgent(plannedLiveDate, shootDate, editDate, urgencyReason);
        contentPlanRepository.save(plan);
        auditService.record(user, Optional.empty(), "PLANNING", "SCHEDULE_SET_URGENT", "content_plans",
                plan.getId(), urgencyReason);
        return plan;
    }

    private void requireFutureLiveDate(LocalDate plannedLiveDate) {
        if (plannedLiveDate == null || plannedLiveDate.isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Planned Live Date must not be in the past");
        }
    }

    /**
     * Rejects an Output Type that has been retired from new usage (see {@link OutputType} - a
     * retired constant stays fully valid for the historical rows that already carry it, and is
     * closed only to NEW ones). Enforced here, at the service, rather than only by omitting the
     * option from the dropdowns: a direct API call must be refused too, not silently accepted.
     */
    private static void requireSelectableOutputType(OutputType outputType) {
        if (outputType != null && !outputType.isSelectable()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Output Type " + outputType.name() + " is retired and can no longer be used for new "
                            + "Planned Outputs. Existing " + outputType.name() + " outputs are unaffected.");
        }
    }

    @Transactional
    public PlannedOutput addPlannedOutput(User user, UUID contentPlanId, OutputType outputType, ReelType reelType,
                                           String titleDescription) {
        requireSelectableOutputType(outputType);
        ContentPlan plan = requireContentPlan(contentPlanId);
        requirePlanningExecutionAuthority(user, plan.getWorkflowInstance());
        PlannedOutput output = plannedOutputRepository.save(new PlannedOutput(plan, outputType, reelType, titleDescription));
        auditService.record(user, Optional.empty(), "PLANNING", "PLANNED_OUTPUT_ADDED", "planned_outputs",
                output.getId(), null);
        return output;
    }

    /**
     * Planning Workspace "+ Add Output" UI: a single PlannedOutput can only carry one Reel Type
     * (ERD-CON-008), so selecting multiple Reel Types (e.g. SHORT and LONG) for a REEL output
     * creates one separate PlannedOutput per selected type - not one output with multiple types -
     * so each can later be completed, targeted and tracked independently. All outputs created by
     * one such submission share a single reelGroupId so they render as one grouped row and are
     * made to share one common Publication Target set (see {@link #mapPublicationScope}). Reel
     * Type is optional: a REEL output submitted with none selected creates a single PlannedOutput
     * with a NULL Reel Type, same as any other Output Type.
     */
    @Transactional
    public List<PlannedOutput> addPlannedOutputs(User user, UUID contentPlanId, OutputType outputType,
                                                  List<ReelType> reelTypes, String titleDescription) {
        requireSelectableOutputType(outputType);
        ContentPlan plan = requireContentPlan(contentPlanId);
        requirePlanningExecutionAuthority(user, plan.getWorkflowInstance());
        List<ReelType> typesToCreate = outputType == OutputType.REEL && reelTypes != null && !reelTypes.isEmpty()
                ? reelTypes.stream().distinct().toList()
                : java.util.Collections.singletonList(null);
        UUID sharedGroupId = UuidV7.generate();
        List<PlannedOutput> created = new java.util.ArrayList<>();
        for (ReelType reelType : typesToCreate) {
            PlannedOutput output = new PlannedOutput(plan, outputType, reelType, titleDescription);
            output.setReelGroupId(sharedGroupId);
            plannedOutputRepository.save(output);
            created.add(output);
            auditService.record(user, Optional.empty(), "PLANNING", "PLANNED_OUTPUT_ADDED", "planned_outputs",
                    output.getId(), null);
        }
        return created;
    }

    /**
     * Applies additively to every Planned Output sharing the given output's reelGroupId - a REEL
     * group (e.g. SHORT + LONG created together) always has exactly one common Publication Target
     * set, never a per-Reel-Type override, so a mapping added via any group member is propagated
     * to all of them. Non-grouped outputs are a "group of one" and this is a no-op difference.
     */
    @Transactional
    public void mapPublicationScope(User user, UUID plannedOutputId, List<UUID> publicationTargetIds) {
        PlannedOutput output = plannedOutputRepository.findById(plannedOutputId)
                .orElseThrow(() -> DomainException.notFound("Planned Output not found: " + plannedOutputId));
        requirePlanningExecutionAuthority(user, output.getContentPlan().getWorkflowInstance());
        List<PlannedOutput> groupMembers = plannedOutputRepository.findByReelGroupId(output.getReelGroupId());
        for (UUID targetId : publicationTargetIds) {
            PublicationTarget target = publicationTargetRepository.findById(targetId)
                    .orElseThrow(() -> DomainException.notFound("Publication Target not found: " + targetId));
            for (PlannedOutput member : groupMembers) {
                boolean exists = mappingRepository.findByPlannedOutput(member).stream()
                        .anyMatch(m -> m.getPublicationTarget().getId().equals(targetId));
                if (!exists) {
                    mappingRepository.save(new PlannedOutputPublicationTargetMapping(member, target));
                }
            }
        }
        auditService.record(user, Optional.empty(), "PLANNING", "PUBLICATION_SCOPE_MAPPED", "planned_outputs",
                output.getId(), null);
    }

    /**
     * Planning Workspace grouped-row "Edit": reconciles a REEL group's Reel Type membership to the
     * submitted set (creating new PlannedOutput rows and deleting dropped ones as needed) and
     * applies the Output Type/Description to every member, all inside one transaction so a partial
     * group update can never be observed. A newly added Reel Type immediately inherits the group's
     * existing shared Publication Target mappings, so "one shared target set per group" never has a
     * gap. Non-REEL edits are simply a group of one being synced to itself.
     */
    @Transactional
    public List<PlannedOutput> syncReelGroup(User user, UUID reelGroupId, OutputType outputType,
                                              List<ReelType> reelTypes, String titleDescription) {
        List<PlannedOutput> currentMembers = plannedOutputRepository.findByReelGroupId(reelGroupId);
        if (currentMembers.isEmpty()) {
            throw DomainException.notFound("Planned Output group not found: " + reelGroupId);
        }
        PlannedOutput anyMember = currentMembers.get(0);
        requirePlanningExecutionAuthority(user, anyMember.getContentPlan().getWorkflowInstance());
        // Editing a group that is ALREADY a retired type (a historical STORY group whose title or
        // targets are being corrected) must keep working and must not silently convert the row to
        // some other type - so a retired type is refused only when it would be newly INTRODUCED
        // here, never when the group already carries it.
        if (outputType != anyMember.getOutputType()) {
            requireSelectableOutputType(outputType);
        }

        List<ReelType> desiredTypes = outputType == OutputType.REEL && reelTypes != null && !reelTypes.isEmpty()
                ? reelTypes.stream().distinct().toList()
                : java.util.Collections.singletonList(null);

        List<PublicationTarget> sharedTargets = mappingRepository.findByPlannedOutput(anyMember).stream()
                .map(PlannedOutputPublicationTargetMapping::getPublicationTarget).toList();

        java.util.Map<ReelType, PlannedOutput> byReelType = new java.util.HashMap<>();
        for (PlannedOutput member : currentMembers) {
            byReelType.put(member.getReelType(), member);
        }

        List<PlannedOutput> result = new java.util.ArrayList<>();
        for (ReelType type : desiredTypes) {
            PlannedOutput existing = byReelType.remove(type);
            if (existing != null) {
                existing.setTypeAndReelType(outputType, type);
                existing.setTitleDescription(titleDescription);
                plannedOutputRepository.save(existing);
                result.add(existing);
            } else {
                PlannedOutput fresh = new PlannedOutput(anyMember.getContentPlan(), outputType, type, titleDescription);
                fresh.setReelGroupId(reelGroupId);
                plannedOutputRepository.save(fresh);
                for (PublicationTarget target : sharedTargets) {
                    mappingRepository.save(new PlannedOutputPublicationTargetMapping(fresh, target));
                }
                result.add(fresh);
                auditService.record(user, Optional.empty(), "PLANNING", "PLANNED_OUTPUT_ADDED", "planned_outputs",
                        fresh.getId(), null);
            }
        }
        for (PlannedOutput removed : byReelType.values()) {
            for (PlannedOutputPublicationTargetMapping mapping : mappingRepository.findByPlannedOutput(removed)) {
                mappingRepository.delete(mapping);
            }
            plannedOutputRepository.delete(removed);
            auditService.record(user, Optional.empty(), "PLANNING", "PLANNED_OUTPUT_REMOVED", "planned_outputs",
                    removed.getId(), null);
        }
        auditService.record(user, Optional.empty(), "PLANNING", "PLANNED_OUTPUT_UPDATED", "planned_outputs",
                reelGroupId, null);
        return result;
    }

    /** Removes every Planned Output sharing reelGroupId (a whole grouped row) plus their target mappings. */
    @Transactional
    public void removeReelGroup(User user, UUID reelGroupId) {
        List<PlannedOutput> members = plannedOutputRepository.findByReelGroupId(reelGroupId);
        if (members.isEmpty()) {
            throw DomainException.notFound("Planned Output group not found: " + reelGroupId);
        }
        requirePlanningExecutionAuthority(user, members.get(0).getContentPlan().getWorkflowInstance());
        for (PlannedOutput member : members) {
            for (PlannedOutputPublicationTargetMapping mapping : mappingRepository.findByPlannedOutput(member)) {
                mappingRepository.delete(mapping);
            }
            plannedOutputRepository.delete(member);
        }
        auditService.record(user, Optional.empty(), "PLANNING", "PLANNED_OUTPUT_REMOVED", "planned_outputs",
                reelGroupId, null);
    }

    /**
     * Applies to every Planned Output sharing the given output's reelGroupId, mirroring
     * {@link #mapPublicationScope}'s group-wide propagation.
     */
    @Transactional
    public void unmapPublicationTarget(User user, UUID plannedOutputId, UUID publicationTargetId) {
        PlannedOutput output = plannedOutputRepository.findById(plannedOutputId)
                .orElseThrow(() -> DomainException.notFound("Planned Output not found: " + plannedOutputId));
        requirePlanningExecutionAuthority(user, output.getContentPlan().getWorkflowInstance());
        List<PlannedOutput> groupMembers = plannedOutputRepository.findByReelGroupId(output.getReelGroupId());
        // A target that already has a real ActualPublicationEvent (ORIGINAL) must never be silently
        // unmapped - the planning scope is no longer just an intention at that point, it's what was
        // actually published, and removing the mapping would sever that traceability. Checked across
        // every group member (a REEL group shares one target set) before deleting anything.
        for (PlannedOutput member : groupMembers) {
            if (actualPublicationEventRepository.existsByPlannedOutputAndPublicationTarget_IdAndEventType(
                    member, publicationTargetId, PublicationEventType.ORIGINAL)) {
                throw DomainException.conflict(ErrorCode.PUBLICATION_TARGET_ALREADY_PUBLISHED,
                        "This Publication Target has already been published and cannot be removed.");
            }
        }
        for (PlannedOutput member : groupMembers) {
            mappingRepository.findByPlannedOutput(member).stream()
                    .filter(m -> m.getPublicationTarget().getId().equals(publicationTargetId))
                    .findFirst()
                    .ifPresent(mappingRepository::delete);
        }
        auditService.record(user, Optional.empty(), "PLANNING", "PUBLICATION_SCOPE_TARGET_REMOVED", "planned_outputs",
                plannedOutputId, null);
    }

    /**
     * BRS-REQ-022: additional shooting assignment(s) alongside the initial Shoot Team already set
     * at Idea Review approval time (workflow redesign - see IdeaService#approve), Permission #4,
     * one or more Camerapersons. Idempotent: re-assigning a Cameraperson who already holds an
     * active assignment on this plan returns the existing row rather than inserting a duplicate
     * (the chip-picker UI can safely re-POST a still-checked box). Window is now Shoot Assigned
     * (SA) - the direct equivalent of the old Planning-only window, re-pointed to the status a
     * fresh plan now actually rests at before Shoot execution begins.
     */
    @Transactional
    public ShootingAssignment assignCameraperson(User assigner, UUID contentPlanId, User cameraperson) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        if (plan.getWorkflowInstance().getCurrentStatusCode() != WorkflowStatus.SA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Shooting assignment can only be changed before Shoot execution begins (Shoot Assigned)");
        }
        authorizationService.requireAuthority(assigner, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT,
                LifecycleStage.PLANNING, plan.getWorkflowInstance());
        // Assignee-side eligibility: evaluated against the SHOOTING stage being executed, not the
        // PLANNING screen this assignment happens from (PERM_18 never needs to also cover
        // PLANNING) - frontend candidate filtering is not authorization, so this is re-validated
        // here regardless of what the picker offered.
        operationalEligibilityService.requireShootExecutionEligible(cameraperson, plan.getWorkflowInstance());
        Optional<ShootingAssignment> existing =
                shootingAssignmentRepository.findByContentPlanAndCamerapersonAndActiveTrue(plan, cameraperson);
        if (existing.isPresent()) {
            return existing.get();
        }
        ShootingAssignment assignment = shootingAssignmentRepository.save(
                new ShootingAssignment(plan, cameraperson, assigner));
        auditService.record(assigner, Optional.empty(), "PLANNING", "CAMERAPERSON_ASSIGNED", "shooting_assignments",
                assignment.getId(), null);
        return assignment;
    }

    /** Removes an active shooting assignment, mirroring the same Shoot-Assigned/Permission #4 window as assign. */
    @Transactional
    public void removeCameraperson(User actor, UUID contentPlanId, UUID camerapersonUserId) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        if (plan.getWorkflowInstance().getCurrentStatusCode() != WorkflowStatus.SA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Shooting assignment can only be removed before Shoot execution begins (Shoot Assigned)");
        }
        authorizationService.requireAuthority(actor, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT,
                LifecycleStage.PLANNING, plan.getWorkflowInstance());
        ShootingAssignment assignment = shootingAssignmentRepository.findByContentPlan(plan).stream()
                .filter(ShootingAssignment::isActive)
                .filter(a -> a.getCameraperson().getId().equals(camerapersonUserId))
                .findFirst()
                .orElseThrow(() -> DomainException.notFound("No active shooting assignment for this Cameraperson"));
        assignment.end();
        shootingAssignmentRepository.save(assignment);
        auditService.record(actor, Optional.empty(), "PLANNING", "CAMERAPERSON_UNASSIGNED", "shooting_assignments",
                assignment.getId(), null);
    }

    /**
     * Shoot Lead (not in the frozen ERD - see ENG-036): {@code camerapersonUserId == null} clears
     * the Lead. Otherwise it must be one of the plan's currently active Camerapersons - the Lead
     * dropdown is a subset of Assignee(s), never an independent selection.
     */
    @Transactional
    public void setShootLead(User actor, UUID contentPlanId, UUID camerapersonUserId) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        if (plan.getWorkflowInstance().getCurrentStatusCode() != WorkflowStatus.SA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Shoot Lead can only be set before Shoot execution begins (Shoot Assigned)");
        }
        authorizationService.requireAuthority(actor, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT,
                LifecycleStage.PLANNING, plan.getWorkflowInstance());
        List<ShootingAssignment> active = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        // The partial unique index (V16) checks per-statement, not deferred to commit - the old
        // Lead's clear must actually flush to the DB before the new Lead's set is issued, or
        // Postgres would momentarily see two active Leads and reject it.
        active.forEach(a -> a.setLead(false));
        shootingAssignmentRepository.saveAll(active);
        shootingAssignmentRepository.flush();
        if (camerapersonUserId != null) {
            ShootingAssignment target = active.stream()
                    .filter(a -> a.getCameraperson().getId().equals(camerapersonUserId))
                    .findFirst()
                    .orElseThrow(() -> DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "Shoot Lead must be one of the currently assigned Camerapersons"));
            target.setLead(true);
            shootingAssignmentRepository.save(target);
        }
        auditService.record(actor, Optional.empty(), "PLANNING",
                camerapersonUserId != null ? "SHOOT_LEAD_SET" : "SHOOT_LEAD_CLEARED", "shooting_assignments",
                plan.getId(), null);
    }

    /**
     * Single-button "Assign Cameraperson(s)" (ENG-041): assigns every newly-staged Cameraperson and
     * sets the Shoot Lead in one request. Composed by calling {@link #assignCameraperson} and
     * {@link #setShootLead} directly (self-invocation on the same bean bypasses their own
     * {@code @Transactional} proxies) so both steps commit or roll back together as one transaction,
     * exactly as the user asked - not two sequential AJAX calls under one button.
     */
    @Transactional
    public void assignShootTeam(User actor, UUID contentPlanId, List<User> camerapersons, UUID leadUserId) {
        if (camerapersons != null) {
            for (User cameraperson : camerapersons) {
                assignCameraperson(actor, contentPlanId, cameraperson);
            }
        }
        setShootLead(actor, contentPlanId, leadUserId);
    }

    /**
     * ENG-046: one Shoot Description shared by the whole Cameraperson team on this plan (not per
     * individual assignee), editable any time by whoever holds PERM_04_SHOOT_ASSIGNMENT (the same
     * authority that governs Shoot Assignment itself) - not restricted to a particular workflow
     * status, since CEO/MM should be able to update instructions for an already-assigned team too.
     */
    @Transactional
    public ContentPlan updateShootDescription(User actor, UUID contentPlanId, String description) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        requireShootDescriptionAuthority(actor, plan.getWorkflowInstance());
        String previous = plan.getShootDescription();
        plan.setShootDescription(description);
        contentPlanRepository.save(plan);
        // ENG-048: old + new value, not just a bare "updated" marker - so a Planning Approver's
        // edit during Planning Review is traceable later, never a silent overwrite.
        String auditReason = "Old: \"" + (previous == null ? "" : previous) + "\" -> New: \""
                + (description == null ? "" : description) + "\"";
        auditService.record(actor, Optional.empty(), "SHOOTING", "SHOOT_DESCRIPTION_UPDATED", "content_plans",
                plan.getId(), auditReason);
        return plan;
    }

    /**
     * ENG-048: Shoot Instructions is editable by whoever holds Shoot Assignment authority
     * (PERM_04, unchanged - see {@code updateShootDescription}'s original javadoc), or native
     * CEO/MM authority. The former PERM_03 (Planning Review) fallback was removed alongside
     * Planning Review itself (workflow redesign) - the reviewer who now sets up the Shoot team at
     * Idea Review time already holds PERM_01, and correcting instructions afterward is squarely a
     * PERM_04 concern like every other Shoot-assignment-adjacent action.
     */
    private void requireShootDescriptionAuthority(User actor, WorkflowInstance workflowInstance) {
        if (authorizationService.hasNativeAuthority(actor)) {
            return;
        }
        authorizationService.requireAuthority(actor, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT,
                LifecycleStage.PLANNING, workflowInstance);
    }

    // NOTE (workflow redesign): Planning Review has been eliminated as a separate active-workflow
    // gate, and PL/PLRV/PLAP are gone entirely from WorkflowStatus/workflow_concepts (fresh
    // deployment, no legacy data to preserve). A Content Plan is now created already fully planned
    // (Category/Priority/Schedule/Folder Link/Outputs/Publication Scope/Models/initial Shoot Team)
    // and transitions straight PA -> SA, all inside IdeaService#approve (the single combined
    // "Idea Review + Planning Details" action, governed by PERM_01_IDEA_REVIEW alone).
    // submitPlanningReview/savePlanAssignAndSubmit/decidePlanningReview (formerly here) are removed
    // entirely, not merely disabled.
}
