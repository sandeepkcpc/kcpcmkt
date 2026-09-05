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
import com.kcpc.mkt.idea.dto.PlanningStage;
import com.kcpc.mkt.idea.repository.IdeaDescriptionCorrectionRepository;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.domain.PersonalMarkAttribution;
import com.kcpc.mkt.marks.domain.PredefinedMarkCorrection;
import com.kcpc.mkt.marks.domain.PredefinedRoleMarks;
import com.kcpc.mkt.marks.domain.RoleType;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.marks.repository.PredefinedMarkCorrectionRepository;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.marks.service.MarkCatalogueService;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.notification.domain.NotificationType;
import com.kcpc.mkt.notification.service.NotificationService;
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
import com.kcpc.mkt.production.domain.EditingAssignment;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.domain.PublishingAssignment;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
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
    // ENG-096: predefined_role_marks has one NOT NULL column per role (Cameraperson/Editor/Model) -
    // a role whose stage was skipped never had a reviewer-supplied mark to store, but the row still
    // needs a value there to satisfy that schema. This is an inert placeholder, never validated
    // against the Mark Catalogue and never read back for that skipped role (see approve() below).
    private static final BigDecimal NO_MARK = new BigDecimal("0.0");

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
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final UserRepository userRepository;
    private final OperationalEligibilityService operationalEligibilityService;
    private final MarkCatalogueService markCatalogueService;
    private final com.kcpc.mkt.masterdata.service.CategoryService categoryService;
    private final NotificationService notificationService;

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
                        EditingAssignmentRepository editingAssignmentRepository,
                        PublishingAssignmentRepository publishingAssignmentRepository,
                        UserRepository userRepository, OperationalEligibilityService operationalEligibilityService,
                        MarkCatalogueService markCatalogueService,
                        com.kcpc.mkt.masterdata.service.CategoryService categoryService,
                        NotificationService notificationService) {
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
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.userRepository = userRepository;
        this.operationalEligibilityService = operationalEligibilityService;
        this.markCatalogueService = markCatalogueService;
        this.categoryService = categoryService;
        this.notificationService = notificationService;
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
     * Content Priority, Planned Live/Shoot/Edit Dates, Drive Link, at least one Cameraperson when
     * Shoot starts the pipeline) - Category, SKU, Outputs, Publication Scope and Models remain
     * optional, exactly as they always were. On success, creates the Content Plan already fully
     * populated and transitions straight to Shoot Assigned/Edit Assigned/Ready for Publishing -
     * never PL/PLRV/PLAP.
     *
     * <p>ENG-091 (Stages): {@code planning.stages()} picks where the pipeline starts - Standard
     * ({@code SHOOT,EDIT,PUBLISHING}, unchanged from before ENG-091: Cameraperson(s)/Shoot Lead
     * required, target SA), Direct Edit ({@code EDIT,PUBLISHING}: Editor(s) required, no Lead,
     * target EA), or Direct Publishing ({@code PUBLISHING} only: target RFP). Exactly one of these
     * 3 combinations is valid; nothing else is. Publisher(s) (ENG-099) is required for all three -
     * not just Direct Publishing. Whichever stage(s) are
     * excluded gets a plain skip-reason note on the Content Plan (see
     * {@link ContentPlan#getShootStageSkipReason()}/{@link ContentPlan#getEditStageSkipReason()})
     * - never a fake {@code WorkflowTransitionHistory} row, since that stage's status was never
     * really entered.
     */
    private void approve(User reviewer, Idea idea, WorkflowInstance workflowInstance, ReviewCycle cycle,
                          Optional<PermissionGrant> actingGrant, BigDecimal cameramanMark, BigDecimal editorMark,
                          BigDecimal modelMark, PlanningApprovalRequest planning) {
        if (planning == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Planning details are mandatory to approve an idea");
        }
        // Content Priority is no longer mandatory here - a null value defaults to LOW (see below,
        // where the Content Plan is built) rather than being rejected.
        if (planning.plannedLiveDate() == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Planned Live Date is mandatory");
        }
        if (planning.plannedLiveDate().isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Planned Live Date must not be in the past");
        }
        // ENG-093: Stages must be parsed BEFORE date requiredness is decided - which of Shoot
        // Date/Edit Date are even asked for depends on which stages are actually part of the
        // pipeline, in both Standard and Urgent Planning Mode alike. Absent (not merely empty)
        // stages means the caller predates ENG-091 entirely (every existing API/test caller never
        // sends this field) - defaults to Standard/Standard-stage-set, the exact behavior this app
        // already had before Stages existed. An explicitly empty/invalid list IS a real validation
        // error, never silently defaulted.
        List<PlanningStage> stages = planning.stages() == null
                ? List.of(PlanningStage.SHOOT, PlanningStage.EDIT, PlanningStage.PUBLISHING)
                : planning.stages();
        if (stages.isEmpty() || !stages.contains(PlanningStage.PUBLISHING)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid stage selection");
        }
        boolean shootStarts = stages.contains(PlanningStage.SHOOT);
        boolean editStarts = !shootStarts && stages.contains(PlanningStage.EDIT);
        boolean publishingStarts = !shootStarts && !editStarts;
        // Only the 3 starting-point combinations are valid - {SHOOT,EDIT,PUBLISHING},
        // {EDIT,PUBLISHING}, {PUBLISHING} - never an arbitrary subset (e.g. SHOOT without EDIT).
        int stageCount = stages.size();
        boolean validCombo = (shootStarts && stageCount == 3 && stages.contains(PlanningStage.EDIT))
                || (editStarts && stageCount == 2)
                || (publishingStarts && stageCount == 1);
        if (!validCombo) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid stage selection");
        }
        // Edit is part of the pipeline whenever it isn't skipped entirely by starting directly at
        // Publishing - true for both the Standard case (Shoot starts) and Direct Edit (Edit starts).
        boolean editIncluded = !publishingStarts;

        PlanningMode mode = planning.planningMode() == null ? PlanningMode.STANDARD : planning.planningMode();
        LocalDate shootDate = null;
        LocalDate editDate = null;
        if (mode == PlanningMode.URGENT) {
            if (planning.urgencyReason() == null || planning.urgencyReason().isBlank()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Urgent Planning Mode requires an Urgency Reason");
            }
            // ENG-093: Planning Mode only controls the date OFFSET/deadline discipline (explicit
            // dates required in Urgent, defaulted in Standard) - it never forces a date for a
            // stage that Stages itself already excluded from the pipeline.
            if (shootStarts) {
                if (planning.shootDate() == null) {
                    throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "Urgent Planning Mode requires Shoot Date");
                }
                shootDate = planning.shootDate();
            }
            if (editIncluded) {
                if (planning.editDate() == null) {
                    throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "Urgent Planning Mode requires Edit Date");
                }
                editDate = planning.editDate();
            }
        } else {
            // BRS-REQ-027/086/093: STANDARD defaults shoot=live-5d/edit=live-2d (both overridable);
            // this floor exists solely to keep the derived Shoot/Edit Date from landing before
            // today, so how many days it requires depends on which of those dates the current
            // Stages combination actually has: 5 when Shoot starts the pipeline (protects the
            // Live-5d Shoot Date, the stricter of the two), 2 when only Edit does (Direct Edit -
            // no Shoot Date exists to protect, only the Live-2d Edit Date). Publishing-only
            // planning has neither derived date at all, so this check never applies there - the
            // Planned Live Date's own "must not be in the past" requirement (already enforced
            // above, unconditionally) is the only date rule a Publishing-only plan is subject to.
            if (!publishingStarts) {
                int minDaysOut = shootStarts ? 5 : 2;
                if (planning.plannedLiveDate().isBefore(LocalDate.now(BUSINESS_ZONE).plusDays(minDaysOut))) {
                    throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "A Planned Live Date fewer than " + minDaysOut
                                    + " days away requires Urgent Planning Mode (BRS-REQ-093)");
                }
            }
            if (shootStarts) {
                shootDate = planning.shootDate() != null ? planning.shootDate() : planning.plannedLiveDate().minusDays(5);
            }
            if (editIncluded) {
                editDate = planning.editDate() != null ? planning.editDate() : planning.plannedLiveDate().minusDays(2);
            }
            // Standard mode's own past-date guard: the BRS-REQ-093 check above only constrains the
            // Planned Live Date itself (>= 5 days out), but the resulting Shoot/Edit Date - whether
            // the Live-5d/Live-2d default above or a manually-overridden value in these same
            // Standard-mode fields - could still land before today (e.g. a Live Date only just past
            // the 5-day floor, or an explicit override). Never silently accept or shift a Standard
            // execution date that's already in the past; Urgent Planning Mode remains the one place
            // an explicit past-adjacent date is the user's own deliberate call, so this guard is
            // deliberately scoped to the Standard branch only.
            if (shootDate != null && shootDate.isBefore(LocalDate.now(BUSINESS_ZONE))) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Shoot Date cannot be before today. Please select a later Planned Live Date.");
            }
            if (editDate != null && editDate.isBefore(LocalDate.now(BUSINESS_ZONE))) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Edit Date cannot be before today. Please select a later Planned Live Date.");
            }
        }
        // A manually-entered Drive Folder Link is only mandatory when automatic Drive provisioning
        // is disabled - when enabled, initiateProvisioning below fills it in moments after this
        // transaction commits (it cannot have run yet, since approval and "ready" are now the same
        // instant), so requiring it here too would just be busywork for no safety benefit.
        boolean driveWillAutoProvision = driveProvisioningService.isDriveIntegrationEnabled();
        if (!driveWillAutoProvision && (planning.folderLink() == null || planning.folderLink().isBlank())) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Drive Folder Link is mandatory");
        }

        List<User> camerapersons = new ArrayList<>();
        if (shootStarts) {
            if (planning.camerapersonUserIds() == null || planning.camerapersonUserIds().isEmpty()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "At least one Cameraperson must be assigned before approval");
            }
            for (UUID camerapersonId : planning.camerapersonUserIds()) {
                User cameraperson = userRepository.findById(camerapersonId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + camerapersonId));
                // Assignee-side eligibility: evaluated against the SHOOTING stage being executed,
                // not the Idea Review screen this assignment now happens from (PERM_18 never needs
                // to also cover IDEA_MANAGEMENT) - the picker's own candidate filtering is not
                // authorization, so this is re-validated here regardless of what it offered.
                operationalEligibilityService.requireShootExecutionEligible(cameraperson, workflowInstance);
                camerapersons.add(cameraperson);
            }
        }
        List<User> editors = new ArrayList<>();
        if (editStarts) {
            if (planning.editorUserIds() == null || planning.editorUserIds().isEmpty()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "At least one Editor must be assigned before approval");
            }
            // ENG-095: Direct Edit has no earlier Shoot Review Approve to fold Editor Lead into -
            // this is the only checkpoint for this Content Plan's Edit team, so Editor Lead is
            // mandatory here too, exactly like ShootingService#decideShootReview's own rule.
            if (planning.leadEditorUserId() == null) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Editor Lead is mandatory");
            }
            if (!planning.editorUserIds().contains(planning.leadEditorUserId())) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Editor Lead must be one of the selected Editor(s)");
            }
            for (UUID editorId : planning.editorUserIds()) {
                User editor = userRepository.findById(editorId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + editorId));
                operationalEligibilityService.requireEditExecutionEligible(editor, workflowInstance);
                editors.add(editor);
            }
        }
        // Publisher(s) at Planning time (ENG-099): now MANDATORY for every stage combination, not
        // just Direct Publishing - every valid combination (Shoot+Edit+Publishing, Edit+Publishing,
        // Publishing-only) always includes Publishing eventually, so Publisher(s) must be decided
        // at Planning regardless of which of Shoot/Edit also starts the pipeline. Publisher(s) can
        // still ALSO be reassigned later at Shoot/Edit Review Approve (or Edit Stage Skip) exactly
        // as before - this requirement only means it can never be left unset here. Assigning here
        // never activates Publishing (PublishingAssignment carries no status of its own - see
        // PublishingAssignment/PublishingService) and never duplicates a later assignment:
        // PublishingService#assignPublisher (reused unchanged by every later fold-in path) is
        // already idempotent, returning the existing active row instead of inserting a second one
        // for the same (ContentPlan, Publisher) pair.
        List<User> publishers = new ArrayList<>();
        if (planning.publisherUserIds() == null || planning.publisherUserIds().isEmpty()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "At least one Publisher must be assigned before approval");
        }
        if (planning.publisherUserIds() != null) {
            for (UUID publisherId : planning.publisherUserIds()) {
                User publisher = userRepository.findById(publisherId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + publisherId));
                operationalEligibilityService.requirePublishingExecutionEligible(publisher, workflowInstance);
                publishers.add(publisher);
            }
        }
        WorkflowStatus targetStatus = shootStarts ? WorkflowStatus.SA : editStarts ? WorkflowStatus.EA : WorkflowStatus.RFP;
        List<PlanningOutputRequest> outputRequests = planning.outputs() == null ? List.of() : planning.outputs();

        // AC-016.3/016.6: both marks mandatory from the controlled list; validated by PredefinedRoleMarks ctor.
        cycle.decide(reviewer, "APPROVED", null, actingGrant.orElse(null));
        reviewCycleRepository.save(cycle);

        String contentId = contentIdAllocationService.allocateContentId();
        // ENG-094: blank/null stays allowed unconditionally (Category has always been optional;
        // every pre-existing API/test caller that never sends this field keeps working exactly as
        // before) - only a non-blank value must match a currently-active Category Catalogue entry.
        categoryService.requireActiveNameOrBlank(planning.categoryText());
        ContentPlan contentPlan = new ContentPlan(idea, workflowInstance, contentId);
        contentPlan.setCategoryText(planning.categoryText());
        // Content Priority defaults to LOW when not explicitly provided - never left null. The
        // Planning form itself now pre-selects LOW (still changeable), so this is a safety-net for
        // any caller that omits the field entirely, not something a real UI submission relies on.
        // Approval CREATES the Content Plan, so a retired priority (see ContentPriority) is never
        // legitimate here - there is no pre-existing value to preserve. Refused for API callers
        // too, not just hidden from the Planning Basics dropdown.
        if (planning.contentPriority() != null && !planning.contentPriority().isSelectable()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Priority " + planning.contentPriority().name() + " is retired and can no longer be "
                            + "assigned. Existing " + planning.contentPriority().name() + " plans are unaffected.");
        }
        contentPlan.setContentPriority(planning.contentPriority() != null
                ? planning.contentPriority() : com.kcpc.mkt.planning.domain.ContentPriority.LOW);
        contentPlan.setSku(planning.skuReference(), planning.skuNotApplicable());
        if (mode == PlanningMode.URGENT) {
            contentPlan.setPlanningScheduleUrgent(planning.plannedLiveDate(), shootDate, editDate, planning.urgencyReason());
        } else {
            contentPlan.setPlanningScheduleStandard(planning.plannedLiveDate(), shootDate, editDate);
        }
        contentPlan.setFolderLink(planning.folderLink());
        contentPlan.setPreparedBy(reviewer);
        if (!shootStarts) {
            contentPlan.setShootStageSkipReason("Stage not selected during planning");
        }
        if (publishingStarts) {
            contentPlan.setEditStageSkipReason("Stage not selected during planning");
        }
        contentPlanRepository.save(contentPlan);
        planningPreparerRepository.save(new PlanningPreparer(contentPlan, reviewer));

        // ENG-096: Cameraperson/Model Marks only apply when Shoot is actually part of the pipeline
        // (no cameraperson/model participation exists otherwise); Editor Mark only when Edit is
        // included. A skipped role's mark is neither required from nor validated against the
        // reviewer's submission - it's stored as NO_MARK, which is never read back for that role
        // (Cameraperson/Editor Mark attribution only ever happens at that role's own Shoot/Edit
        // Review approval - ShootingService/EditingService - which cannot occur for a skipped stage).
        BigDecimal effectiveCameramanMark = NO_MARK;
        BigDecimal effectiveEditorMark = NO_MARK;
        BigDecimal effectiveModelMark = NO_MARK;
        if (shootStarts) {
            markCatalogueService.requireActiveValue(RoleType.CAMERAPERSON, cameramanMark);
            markCatalogueService.requireActiveValue(RoleType.MODEL, modelMark);
            effectiveCameramanMark = cameramanMark;
            effectiveModelMark = modelMark;
        }
        if (editIncluded) {
            markCatalogueService.requireActiveValue(RoleType.EDITOR, editorMark);
            effectiveEditorMark = editorMark;
        }
        PredefinedRoleMarks marks = new PredefinedRoleMarks(
                contentPlan, effectiveCameramanMark, effectiveEditorMark, effectiveModelMark, reviewer);
        marksRepository.save(marks);

        // Models have no future "Model Review" gate the way Shoot/Edit Review exist, and are
        // already fully resolved right here - unlike Cameraperson/Editor marks (attributed later,
        // at their own review decision), the Model Mark is attributed immediately to every
        // selected Model/Talent, using this same IDEA_REVIEW cycle. ENG-096: Model participation
        // only exists when Shoot is actually part of the pipeline - the Models/Talent picker is
        // already hidden whenever Shoot is skipped (reviews-ideas.jspf/idea-detail.jsp), but this
        // is the backend's own non-bypassable enforcement of that same rule (a hidden field is
        // never itself the safety net): talentUserIds submitted for a Shoot-skipped plan - e.g. a
        // stale selection left over from switching Stages after checking some - must never create
        // a ContentPlanTalentEntry or a PersonalMarkAttribution.
        if (shootStarts && planning.talentUserIds() != null) {
            for (UUID talentUserId : planning.talentUserIds()) {
                User talentUser = userRepository.findById(talentUserId)
                        .orElseThrow(() -> DomainException.notFound("User not found: " + talentUserId));
                ContentPlanTalentEntry talentEntry = talentEntryRepository.save(
                        new ContentPlanTalentEntry(contentPlan, talentUser.getFullName(), talentUser));
                attributionRepository.save(new PersonalMarkAttribution(talentUser, RoleType.MODEL, contentPlan,
                        cycle, marks, marks.getPredefinedModelMark()));
                notificationService.notify(talentUser, NotificationType.TASK_ASSIGNED, "New Task Assigned",
                        "You have been assigned " + contentPlan.getContentId(), contentPlan,
                        "TASK_ASSIGNED:ContentPlanTalentEntry:" + talentEntry.getId());
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
            notificationService.notify(cameraperson, NotificationType.TASK_ASSIGNED, "New Task Assigned",
                    "You have been assigned " + contentPlan.getContentId(), contentPlan,
                    "TASK_ASSIGNED:ShootingAssignment:" + assignment.getId());
        }
        // Direct Edit only (ENG-091/ENG-095): Editor Lead IS required here too, validated above -
        // this is the only checkpoint for the Edit team when Shoot itself was never selected, no
        // later Shoot Review Approve exists to fold it into. Direct repository write mirroring the
        // ShootingAssignment loop above, not through EditingService.
        for (User editor : editors) {
            EditingAssignment assignment = editingAssignmentRepository.save(
                    new EditingAssignment(contentPlan, editor, reviewer));
            if (planning.leadEditorUserId() != null && planning.leadEditorUserId().equals(editor.getId())) {
                assignment.setLead(true);
                editingAssignmentRepository.save(assignment);
            }
            notificationService.notify(editor, NotificationType.TASK_ASSIGNED, "New Task Assigned",
                    "You have been assigned " + contentPlan.getContentId(), contentPlan,
                    "TASK_ASSIGNED:EditingAssignment:" + assignment.getId());
        }
        // Direct Publishing only (ENG-091): no earlier Edit Review Approve exists to fold this
        // into - Publisher(s) never have a Lead concept anyway (ENG-036/ENG-044).
        for (User publisher : publishers) {
            PublishingAssignment assignment = publishingAssignmentRepository.save(
                    new PublishingAssignment(contentPlan, publisher, reviewer));
            notificationService.notify(publisher, NotificationType.TASK_ASSIGNED, "New Task Assigned",
                    "You have been assigned " + contentPlan.getContentId(), contentPlan,
                    "TASK_ASSIGNED:PublishingAssignment:" + assignment.getId());
        }

        // Cheap tracking-row insert only (no network call) - the real Google Drive folder
        // creation happens after this transaction commits (DriveProvisioningService), so a Drive
        // failure can never roll back or duplicate Content ID creation.
        driveProvisioningService.initiateProvisioning(contentPlan);

        workflowService.transition(workflowInstance, targetStatus, reviewer, actingGrant, "APPROVE_IDEA", null);
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
        // A retired Output Type (see OutputType) is closed to new Planned Outputs - refused here
        // as well as being absent from the Planned Outputs grid, so an API caller posting one
        // directly to Idea Review approval is rejected rather than quietly creating it.
        if (!outputRequest.outputType().isSelectable()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Output Type " + outputRequest.outputType().name() + " is retired and can no longer be "
                            + "used for new Planned Outputs. Existing " + outputRequest.outputType().name()
                            + " outputs are unaffected.");
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

        markCatalogueService.requireActiveValue(RoleType.CAMERAPERSON, newCamerapersonMark);
        markCatalogueService.requireActiveValue(RoleType.EDITOR, newEditorMark);
        markCatalogueService.requireActiveValue(RoleType.MODEL, newModelMark);
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

    /**
     * CEO/Marketing Manager only (native authority - same gate as {@link #updateDescription}).
     * Same Idea record, same Idea ID - never a new Idea/version, and no other field (Title,
     * Description, Additional Note, Priority, Stages, assignments, workflow status) is touched.
     * The new value must be a non-blank, valid http(s) URL (reuses {@link #isValidUrl}, the exact
     * same check {@link #submit} already applies to the original submission). Recorded via the
     * existing {@code SystemAuditLog} mechanism only (no dedicated correction-ledger table, unlike
     * Description - this feature was specced without a "reason"/prior-value-history requirement),
     * which the Idea Review screens read back to bump "Last Updated" (see
     * IdeaMvcController#detail / ReviewsMvcController#buildIdeasTab).
     */
    @Transactional
    public Idea updateReferenceLink(User actor, UUID ideaId, String newReferenceLink) {
        authorizationService.requireNativeAuthority(actor, "Editing the Idea Reference Link");
        if (newReferenceLink == null || newReferenceLink.isBlank() || !isValidUrl(newReferenceLink)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Reference Link must be a valid URL");
        }
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> DomainException.notFound("Idea not found: " + ideaId));
        idea.updateReferenceLink(newReferenceLink.trim());
        ideaRepository.save(idea);
        auditService.record(actor, Optional.empty(), "IDEA", "IDEA_REFERENCE_LINK_UPDATED", "ideas", idea.getId(), null);
        return idea;
    }

    private int nextCycleNumber(WorkflowInstance workflowInstance, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType)
                .stream().findFirst().map(c -> c.getCycleNumber() + 1).orElse(1);
    }
}
