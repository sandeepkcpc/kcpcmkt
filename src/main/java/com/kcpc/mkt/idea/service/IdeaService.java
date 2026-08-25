package com.kcpc.mkt.idea.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.drive.service.DriveProvisioningService;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.domain.IdeaReviewDecision;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.domain.PredefinedMarkCorrection;
import com.kcpc.mkt.marks.domain.PredefinedRoleMarks;
import com.kcpc.mkt.marks.repository.PredefinedMarkCorrectionRepository;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.service.ContentIdAllocationService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * BRS-REQ-014..019: Idea Submission and Idea Review, including the atomic Idea-Approval
 * compound command (Idea approval -&gt; Content ID -&gt; Content Plan -&gt; predefined Marks -&gt;
 * transition to Planning) required by build-prompt section 14/37.
 */
@Service
public class IdeaService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final IdeaRepository ideaRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final PredefinedRoleMarksRepository marksRepository;
    private final PredefinedMarkCorrectionRepository markCorrectionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final WorkflowTransitionService workflowService;
    private final ContentIdAllocationService contentIdAllocationService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final DriveProvisioningService driveProvisioningService;

    public IdeaService(IdeaRepository ideaRepository, ContentPlanRepository contentPlanRepository,
                        PredefinedRoleMarksRepository marksRepository,
                        PredefinedMarkCorrectionRepository markCorrectionRepository,
                        ReviewCycleRepository reviewCycleRepository,
                        WorkflowTransitionService workflowService, ContentIdAllocationService contentIdAllocationService,
                        AuthorizationService authorizationService, AuditService auditService,
                        DriveProvisioningService driveProvisioningService) {
        this.ideaRepository = ideaRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.marksRepository = marksRepository;
        this.markCorrectionRepository = markCorrectionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.workflowService = workflowService;
        this.driveProvisioningService = driveProvisioningService;
        this.contentIdAllocationService = contentIdAllocationService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
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

    @Transactional
    public Idea decide(User reviewer, UUID ideaId, IdeaReviewDecision decision, String reason,
                        BigDecimal cameramanMark, BigDecimal editorMark) {
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
            case APPROVE -> approve(reviewer, idea, workflowInstance, cycle, actingGrant, cameramanMark, editorMark);
            case REJECT -> reject(reviewer, workflowInstance, cycle, actingGrant, reason);
            case RETAIN -> retain(reviewer, workflowInstance, cycle, actingGrant, reason);
        }
        return idea;
    }

    private void approve(User reviewer, Idea idea, WorkflowInstance workflowInstance, ReviewCycle cycle,
                          Optional<PermissionGrant> actingGrant, BigDecimal cameramanMark, BigDecimal editorMark) {
        // AC-016.3/016.6: both marks mandatory from the controlled list; validated by PredefinedRoleMarks ctor.
        cycle.decide(reviewer, "APPROVED", null, actingGrant.orElse(null));
        reviewCycleRepository.save(cycle);

        String contentId = contentIdAllocationService.allocateContentId();
        ContentPlan contentPlan = new ContentPlan(idea, workflowInstance, contentId);
        contentPlanRepository.save(contentPlan);
        PredefinedRoleMarks marks = new PredefinedRoleMarks(contentPlan, cameramanMark, editorMark, reviewer);
        marksRepository.save(marks);
        // Cheap tracking-row insert only (no network call) - the real Google Drive folder
        // creation happens after this transaction commits (DriveProvisioningService), so a Drive
        // failure can never roll back or duplicate Content ID creation.
        driveProvisioningService.initiateProvisioning(contentPlan);

        workflowService.transition(workflowInstance, WorkflowStatus.PL, reviewer, actingGrant, "APPROVE_IDEA", null);
        auditService.record(reviewer, actingGrant, "IDEA", "IDEA_APPROVED", "ideas", idea.getId(),
                "Content ID allocated: " + contentId);
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
                                                             BigDecimal newEditorMark, String correctionReason) {
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
        PredefinedMarkCorrection latest = markCorrectionRepository.findByPredefinedMarkOrderByCorrectedAtDesc(marks)
                .stream().findFirst().orElse(null);

        marks.applyCorrection(newCamerapersonMark, newEditorMark);
        marksRepository.save(marks);

        PredefinedMarkCorrection correction = markCorrectionRepository.save(new PredefinedMarkCorrection(marks, latest,
                priorCamerapersonMark, priorEditorMark, newCamerapersonMark, newEditorMark, correctionReason, actor,
                actingGrant.orElse(null)));
        auditService.record(actor, actingGrant, "MARKS", "PREDEFINED_MARKS_CORRECTED", "predefined_mark_corrections",
                correction.getId(), correctionReason);
        return correction;
    }

    private int nextCycleNumber(WorkflowInstance workflowInstance, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType)
                .stream().findFirst().map(c -> c.getCycleNumber() + 1).orElse(1);
    }
}
