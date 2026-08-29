package com.kcpc.mkt.idea.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.drive.service.DriveProvisioningService;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.service.OperationalEligibilityService;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.domain.IdeaDescriptionCorrection;
import com.kcpc.mkt.idea.domain.IdeaReviewDecision;
import com.kcpc.mkt.idea.dto.PlanningApprovalRequest;
import com.kcpc.mkt.idea.dto.PlanningOutputRequest;
import com.kcpc.mkt.idea.repository.IdeaDescriptionCorrectionRepository;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.domain.PersonalMarkAttribution;
import com.kcpc.mkt.marks.domain.PredefinedMarkCorrection;
import com.kcpc.mkt.marks.domain.PredefinedRoleMarks;
import com.kcpc.mkt.marks.domain.RoleType;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.marks.repository.PredefinedMarkCorrectionRepository;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping;
import com.kcpc.mkt.planning.domain.PlanningMode;
import com.kcpc.mkt.planning.domain.PlanningPreparer;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.planning.repository.PlanningPreparerRepository;
import com.kcpc.mkt.planning.service.ContentIdAllocationService;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.workflow.domain.GateType;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.ReviewCycleRepository;
import com.kcpc.mkt.workflow.service.WorkflowTransitionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BRS-REQ-014..019: Idea Submission and Idea Review.
 *
 * <p><strong>Workflow redesign:</strong> Planning is no longer a separate workflow stage. The
 * atomic Idea-Approval compound command now runs Idea approval -&gt; Content ID -&gt; Content Plan
 * -&gt; every Planning field (Category/Priority/Schedule/Drive Link/Outputs/Publication Scope/
 * Models/initial Shoot Team) -&gt; predefined Marks -&gt; transition directly to Shoot Assigned
 * (SA), all under {@code PERM_01_IDEA_REVIEW} alone. PL/PLRV/PLAP no longer exist as
 * {@link WorkflowStatus} values at all (fresh deployment, no legacy data to preserve).
 */
@Service
public class IdeaService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final IdeaRepository ideaRepository;
    private final IdeaDescriptionCorrectionRepository descriptionCorrectionRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final PredefinedRoleMarksRepository marksRepository;
    private final PredefinedMarkCorrectionRepository markCorrectionRepository;
    private final PersonalMarkAttributionRepository attributionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionService workflowService;
    private final ContentIdAllocationService contentIdAllocationService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final DriveProvisioningService driveProvisioningService;
    private final PlanningPreparerRepository planningPreparerRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final PublicationTargetRepository publicationTargetRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final UserRepository userRepository;
    private final OperationalEligibilityService operationalEligibilityService;

    public IdeaService(IdeaRepository ideaRepository, IdeaDescriptionCorrectionRepository descriptionCorrectionRepository,
                        ContentPlanRepository contentPlanRepository,
                        PredefinedRoleMarksRepository marksRepository,
                        PredefinedMarkCorrectionRepository markCorrectionRepository,
                        PersonalMarkAttributionRepository attributionRepository,
                        ReviewCycleRepository reviewCycleRepository,
                        WorkflowTransitionService workflowService, ContentIdAllocationService contentIdAllocationService,
                        AuthorizationService authorizationService, AuditService auditService,
                        DriveProvisioningService driveProvisioningService,
                        PlanningPreparerRepository planningPreparerRepository,
                        ContentPlanTalentEntryRepository talentEntryRepository,
                        PlannedOutputRepository plannedOutputRepository,
                        PlannedOutputPublicationTargetMappingRepository mappingRepository,
                        PublicationTargetRepository publicationTargetRepository,
                        ShootingAssignmentRepository shootingAssignmentRepository,
                        UserRepository userRepository, OperationalEligibilityService operationalEligibilityService) {
        this.ideaRepository = ideaRepository;
        this.descriptionCorrectionRepository = descriptionCorrectionRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.marksRepository = marksRepository;
        this.markCorrectionRepository = markCorrectionRepository;
        this.attributionRepository = attributionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workflowService = workflowService;
        this.driveProvisioningService = driveProvisioningService;
        this.contentIdAllocationService = contentIdAllocationService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.planningPreparerRepository = planningPreparerRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.mappingRepository = mappingRepository;
        this.publicationTargetRepository = publicationTargetRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.userRepository = userRepository;
        this.operationalEligibilityService = operationalEligibilityService;
    }

    /** BRS-REQ-014: any of the 3 access classes may submit; no permission gate on submission itself. */
    @Transactional
    public Idea submit(User submitter, String title, String referenceLink, String notesRemarks,
                        String additionalNote) {
        if (title == null || title.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Idea Title is mandatory");
        }
        if (referenceLink != null && !referenceLink.isBlank() && !isValidUrl(referenceLink)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Reference Link must be a valid URL");
        }
        WorkflowInstance workflowInstance = workflowService.createInstance(WorkflowStatus.IS);
        String businessIdeaCode = generateIdeaCode();
        Idea idea = ideaRepository.save(new Idea(workflowInstance, businessIdeaCode, title, referenceLink,
                notesRemarks, additionalNote, submitter));
        // AC-014.2: system-derived status Idea Submitted -> Pending Approval, recorded as a transition.
        workflowService.transition(workflowInstance, WorkflowStatus.PA, submitter, Optional.empty(),
                "SUBMIT_IDEA", null);
        auditService.record(submitter, Optional.empty(), "IDEA", "IDEA_SUBMITTED", "ideas", idea.getId(), null);
        return idea;
    }

    private static boolean isValidUrl(String value) {
        try {
            java.net.URI uri = new java.net.URI(value.trim());
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    private String generateIdeaCode() {
        String datePart = LocalDate.now(BUSINESS_ZONE).format(YYYYMMDD);
        String prefix = "IDEA-" + datePart + "-";
        long countToday = ideaRepository.countByBusinessIdeaCodeStartingWith(prefix);
        return prefix + "%04d".formatted(countToday + 1);
    }

    /** {@code planning} is mandatory when {@code decision == APPROVE} (see {@link #approve}) and
     * ignored for REJECT/RETAIN. */
    @Transactional
    public Idea decide(User reviewer, UUID ideaId, IdeaReviewDecision decision, String reason,
                        BigDecimal cameramanMark, BigDecimal editorMark, BigDecimal modelMark,
                        PlanningApprovalRequest planning) {
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> DomainException.notFound("Idea not found: " + ideaId));
        WorkflowInstance workflowInstance = idea.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.PA) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Idea Review decisions are only valid while the idea is Pending Approval");
        }

        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(reviewer,
                OperationalPermission.PERM_01_IDEA_REVIEW, LifecycleStage.IDEA_MANAGEMENT, workflowInstance);
        authorizationService.requireNoSelfReviewConflict(actingGrant, reviewer, idea.getSubmittedBy().getId());

        int cycleNumber = nextCycleNumber(workflowInstance, GateType.IDEA_REVIEW);
        ReviewCycle cycle = new ReviewCycle(workflowInstance, GateType.IDEA_REVIEW, cycleNumber, idea.getSubmittedBy());
        reviewCycleRepository.save(cycle);

        switch (decision) {
            case APPROVE -> approve(reviewer, idea, workflowInstance, cycle, actingGrant, cameramanMark, editorMark,
                    modelMark, planning);
            case REJECT -> reject(reviewer, workflowInstance, cycle, actingGrant, reason);
            case RETAIN -> retain(reviewer, workflowInstance, cycle, actingGrant, reason);
        }
        return idea;
    }

    /**
     * The combined "Idea Review + Planning Details" approval command. Validates every Planning
     * field this app has ever required before a plan could proceed to execution (the exact same
     * required set {@code ContentPlan#isFullyPlanned}/the old Planning Review gate enforced:
     * Content Priority, Planned Live/Shoot/Edit Dates, Drive Link, at least one Cameraperson) -
     * Category, SKU, Outputs, Publication Scope and Models remain optional, exactly as they always
     * were. On success, creates the Content Plan already fully populated and transitions straight
     * to Shoot Assigned (SA) - never PL/PLRV/PLAP.
     */
    private void approve(User reviewer, Idea idea, WorkflowInstance workflowInstance, ReviewCycle cycle,
                          Optional<PermissionGrant> actingGrant, BigDecimal cameramanMark, BigDecimal editorMark,
                          BigDecimal modelMark, PlanningApprovalRequest planning) {
        if (planning == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Planning details are mandatory to approve an idea");
        }
        if (planning.contentPriority() == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Content Priority is mandatory");
        }
        if (planning.plannedLiveDate() == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Planned Live Date is mandatory");
        }
        if (planning.plannedLiveDate().isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Planned Live Date must not be in the past");
        }
        PlanningMode mode = planning.planningMode() == null ? PlanningMode.STANDARD : planning.planningMode();
        LocalDate shootDate;
        LocalDate editDate;
        if (mode == PlanningMode.URGENT) {
            if (planning.shootDate() == null || planning.editDate() == null
                    || planning.urgencyReason() == null || planning.urgencyReason().isBlank()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Urgent Planning Mode requires Shoot Date, Edit Date and Urgency Reason");
            }
            shootDate = planning.shootDate();
            editDate = planning.editDate();
        } else {
            // BRS-REQ-027/086/093: STANDARD defaults shoot=live-5d/edit=live-2d (both overridable);
            // a live date fewer than 5 days away requires URGENT instead.
            if (planning.plannedLiveDate().isBefore(LocalDate.now(BUSINESS_ZONE).plusDays(5))) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "A Planned Live Date fewer than 5 days away requires Urgent Planning Mode (BRS-REQ-093)");
            }
            shootDate = planning.shootDate() != null ? planning.shootDate() : planning.plannedLiveDate().minusDays(5);
            editDate = planning.editDate() != null ? planning.editDate() : planning.plannedLiveDate().minusDays(2);
        }
        // A manually-entered Drive Folder Link is only mandatory when automatic Drive provisioning
        // is disabled - when enabled, initiateProvisioning below fills it in moments after this
        // transaction commits (it cannot have run yet, since approval and "ready" are now the same
        // instant), so requiring it here too would just be busywork for no safety benefit.
        boolean driveWillAutoProvision = driveProvisioningService.isDriveIntegrationEnabled();
        if (!driveWillAutoProvision && (planning.folderLink() == null || planning.folderLink().isBlank())) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Drive Folder Link is mandatory");
        }
        if (planning.camerapersonUserIds() == null || planning.camerapersonUserIds().isEmpty()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "At least one Cameraperson must be assigned before approval");
        }
        List<User> camerapersons = new ArrayList<>();
        for (UUID camerapersonId : planning.camerapersonUserIds()) {
            User cameraperson = userRepository.findById(camerapersonId)
                    .orElseThrow(() -> DomainException.notFound("User not found: " + camerapersonId));
            // Assignee-side eligibility: evaluated against the SHOOTING stage being executed, not
            // the Idea Review screen this assignment now happens from (PERM_18 never needs to also
            // cover IDEA_MANAGEMENT) - the picker's own candidate filtering is not authorization,
            // so this is re-validated here regardless of what it offered.
            operationalEligibilityService.requireShootExecutionEligible(cameraperson, workflowInstance);
            camerapersons.add(cameraperson);
        }
        List<PlanningOutputRequest> outputRequests = planning.outputs() == null ? List.of() : planning.outputs();

        // AC-016.3/016.6: both marks mandatory from the controlled list; validated by PredefinedRoleMarks ctor.
        cycle.decide(reviewer, "APPROVED", null, actingGrant.orElse(null));
        reviewCycleRepository.save(cycle);

        String contentId = contentIdAllocationService.allocateContentId();
        ContentPlan contentPlan = new ContentPlan(idea, workflowInstance, contentId);
        contentPlan.setCategoryText(planning.categoryText());
        contentPlan.setContentPriority(planning.contentPriority());
        contentPlan.setSku(planning.skuReference(), planning.skuNotApplicable());
        if (mode == PlanningMode.URGENT) {
            contentPlan.setPlanningScheduleUrgent(planning.plannedLiveDate(), shootDate, editDate, planning.urgencyReason());
        } else {
            contentPlan.setPlanningScheduleStandard(planning.plannedLiveDate(), shootDate, editDate);
        }
        contentPlan.setFolderLink(planning.folderLink());
        contentPlan.setPreparedBy(reviewer);
        contentPlanRepository.save(contentPlan);
        planningPreparerRepository.save(new PlanningPreparer(contentPlan, reviewer));

        PredefinedRoleMarks marks = new PredefinedRoleMarks(contentPlan, cameramanMark, editorMark, modelMark, reviewer);
        marksRepository.save(marks);

        // Models have no future "Model Review" gate the way Shoot/Edit Review exist, and are
        // already fully resolved right here - unlike Cameraperson/Editor marks (attributed later,
        // at their own review decision), the Model Mark is attributed immediately to every
        // selected Model/Talent, using this same IDEA_REVIEW cycle.
        if (planning.talentUserIds() != null) {
            for (UUID talentUserId : planning.talentUserIds()) {
                User talentUser = userRepository.findById(talentUserId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + talentUserId));
                talentEntryRepository.save(new ContentPlanTalentEntry(contentPlan, talentUser.getFullName(), talentUser));
                attributionRepository.save(new PersonalMarkAttribution(talentUser, RoleType.MODEL, contentPlan,
                        cycle, marks, marks.getPredefinedModelMark()));
            }
        }

        for (PlanningOutputRequest outputRequest : outputRequests) {
            createPlannedOutputGroup(contentPlan, outputRequest);
        }

        for (User cameraperson : camerapersons) {
            ShootingAssignment assignment = shootingAssignmentRepository.save(
                    new ShootingAssignment(contentPlan, cameraperson, reviewer));
            if (planning.leadCamerapersonUserId() != null
                    && planning.leadCamerapersonUserId().equals(cameraperson.getId())) {
                assignment.setLead(true);
                shootingAssignmentRepository.save(assignment);
            }
        }

        // Cheap tracking-row insert only (no network call) - the real Google Drive folder
        // creation happens after this transaction commits (DriveProvisioningService), so a Drive
        // failure can never roll back or duplicate Content ID creation.
        driveProvisioningService.initiateProvisioning(contentPlan);

        workflowService.transition(workflowInstance, WorkflowStatus.SA, reviewer, actingGrant, "APPROVE_IDEA", null);
        auditService.record(reviewer, actingGrant, "IDEA", "IDEA_APPROVED", "ideas", idea.getId(),
                "Content ID allocated: " + contentId);
    }

    /** One Output Type/Publication-Scope group per checked row in the Planned Outputs grid
     * (Story/Post/Reel/Long Video - see reviews-ideas.jspf/idea-detail.jsp). The grid has no Reel
     * Type sub-selector any more (V31 redesign), so unlike the Planning tab's own "+ Add Output"
     * (PlanningService#addPlannedOutputs, which still expands one REEL group into several
     * PlannedOutput rows, one per selected Reel Type), this always creates exactly one
     * PlannedOutput per group, mapped to every selected Publication Target. */
    private void createPlannedOutputGroup(ContentPlan contentPlan, PlanningOutputRequest outputRequest) {
        if (outputRequest.outputType() == null) {
            return;
        }
        List<PublicationTarget> publicationTargets = new ArrayList<>();
        if (outputRequest.publicationTargetIds() != null) {
            for (UUID targetId : outputRequest.publicationTargetIds()) {
                publicationTargets.add(publicationTargetRepository.findById(targetId)
                        .orElseThrow(() -> DomainException.notFound("Publication Target not found: " + targetId)));
            }
        }
        PlannedOutput output = new PlannedOutput(contentPlan, outputRequest.outputType(), null,
                outputRequest.outputTitleDescription());
        plannedOutputRepository.save(output);
        for (PublicationTarget target : publicationTargets) {
            mappingRepository.save(new PlannedOutputPublicationTargetMapping(output, target));
        }
    }

    private void reject(User reviewer, WorkflowInstance workflowInstance, ReviewCycle cycle,
                         Optional<PermissionGrant> actingGrant, String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A rejection reason is mandatory (AC-017.1)");
        }
        cycle.decide(reviewer, "REJECTED", reason, actingGrant.orElse(null));
        reviewCycleRepository.save(cycle);
        workflowService.transition(workflowInstance, WorkflowStatus.RJ, reviewer, actingGrant, "REJECT_IDEA", reason);
        auditService.record(reviewer, actingGrant, "IDEA", "IDEA_REJECTED", "workflow_instances",
                workflowInstance.getId(), reason);
    }

    private void retain(User reviewer, WorkflowInstance workflowInstance, ReviewCycle cycle,
                         Optional<PermissionGrant> actingGrant, String reason) {
        // BRS-REQ-018: Retain does not require a mandatory reason (optional comment permitted).
        cycle.decide(reviewer, "RETAINED", reason, actingGrant.orElse(null));
        reviewCycleRepository.save(cycle);
        workflowService.transition(workflowInstance, WorkflowStatus.RET, reviewer, actingGrant, "RETAIN_IDEA", reason);
        auditService.record(reviewer, actingGrant, "IDEA", "IDEA_RETAINED", "workflow_instances",
                workflowInstance.getId(), reason);
    }

    /** BRS-REQ-019: administrative Reopen of a dormant Retained idea, back to Pending Approval, under Permission #1. */
    @Transactional
    public Idea reopen(User actor, UUID ideaId) {
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> DomainException.notFound("Idea not found: " + ideaId));
        WorkflowInstance workflowInstance = idea.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.RET) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Reopen is only valid for a Retained idea");
        }
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_01_IDEA_REVIEW, LifecycleStage.IDEA_MANAGEMENT, workflowInstance);
        workflowService.transition(workflowInstance, WorkflowStatus.PA, actor, actingGrant, "REOPEN_IDEA", null);
        auditService.record(actor, actingGrant, "IDEA", "IDEA_REOPENED", "workflow_instances",
                workflowInstance.getId(), null);
        return idea;
    }

    /**
     * API-OP-033 / SRS-REQ-090: corrects the predefined Cameraperson/Editor marks under Permission
     * #1 authority, appending an immutable linked row to predefined_mark_corrections (ERD-TBL-026)
     * and updating the active values on predefined_role_marks (ERD-TBL-012) in the same transaction.
     */
    @Transactional
    public PredefinedMarkCorrection correctPredefinedMarks(User actor, UUID ideaId, BigDecimal newCamerapersonMark,
                                                             BigDecimal newEditorMark, BigDecimal newModelMark,
                                                             String correctionReason) {
        if (correctionReason == null || correctionReason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A correction reason is mandatory");
        }
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> DomainException.notFound("Idea not found: " + ideaId));
        ContentPlan contentPlan = contentPlanRepository.findByIdea(idea)
                .orElseThrow(() -> DomainException.notFound("No Content Plan exists yet for idea: " + ideaId));
        PredefinedRoleMarks marks = marksRepository.findByContentPlan(contentPlan)
                .orElseThrow(() -> DomainException.notFound("No predefined marks exist yet for idea: " + ideaId));

        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(actor,
                OperationalPermission.PERM_01_IDEA_REVIEW, LifecycleStage.IDEA_MANAGEMENT, idea.getWorkflowInstance());

        BigDecimal priorCamerapersonMark = marks.getPredefinedCameramanMark();
        BigDecimal priorEditorMark = marks.getPredefinedEditorMark();
        BigDecimal priorModelMark = marks.getPredefinedModelMark();
        PredefinedMarkCorrection latest = markCorrectionRepository.findByPredefinedMarkOrderByCorrectedAtDesc(marks)
                .stream().findFirst().orElse(null);

        marks.applyCorrection(newCamerapersonMark, newEditorMark, newModelMark);
        marksRepository.save(marks);

        PredefinedMarkCorrection correction = markCorrectionRepository.save(new PredefinedMarkCorrection(marks, latest,
                priorCamerapersonMark, priorEditorMark, priorModelMark, newCamerapersonMark, newEditorMark,
                newModelMark, correctionReason, actor, actingGrant.orElse(null)));
        auditService.record(actor, actingGrant, "MARKS", "PREDEFINED_MARKS_CORRECTED", "predefined_mark_corrections",
                correction.getId(), correctionReason);
        return correction;
    }

    /**
     * CEO/Marketing Manager only (native authority - never a delegated grant, matching the
     * explicit "CEO/MM edit, all other employees read-only" rule this feature was specced with,
     * unlike {@link #correctPredefinedMarks} which also allows a grant-holding Employee). The
     * originally submitted Description/Details is never silently overwritten: the prior/new pair
     * is preserved in an append-only {@link IdeaDescriptionCorrection} row (same ledger shape as
     * predefined_mark_corrections) alongside a mandatory reason, before the Idea's own field is
     * updated to the new value in the same transaction.
     */
    @Transactional
    public IdeaDescriptionCorrection updateDescription(User actor, UUID ideaId, String newDescription,
                                                         String correctionReason) {
        authorizationService.requireNativeAuthority(actor, "Editing the Idea Description");
        if (correctionReason == null || correctionReason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A reason is mandatory to update the Idea Description");
        }
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> DomainException.notFound("Idea not found: " + ideaId));

        String priorDescription = idea.getNotesRemarks();
        IdeaDescriptionCorrection latest = descriptionCorrectionRepository.findByIdeaOrderByCorrectedAtDesc(idea)
                .stream().findFirst().orElse(null);

        idea.updateNotesRemarks(newDescription);
        ideaRepository.save(idea);

        IdeaDescriptionCorrection correction = descriptionCorrectionRepository.save(new IdeaDescriptionCorrection(
                idea, latest, priorDescription, newDescription, correctionReason, actor, null));
        auditService.record(actor, Optional.empty(), "IDEA", "IDEA_DESCRIPTION_UPDATED", "idea_description_corrections",
                correction.getId(), correctionReason);
        return correction;
    }

    private int nextCycleNumber(WorkflowInstance workflowInstance, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType)
                .stream().findFirst().map(c -> c.getCycleNumber() + 1).orElse(1);
    }
}
