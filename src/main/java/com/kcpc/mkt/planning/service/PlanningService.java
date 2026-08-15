package com.kcpc.mkt.planning.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
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
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.planning.repository.PlanningPreparerRepository;
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
    private final AuditService auditService;

    public PlanningService(ContentPlanRepository contentPlanRepository,
                            ContentPlanTalentEntryRepository talentEntryRepository,
                            PlannedOutputRepository plannedOutputRepository,
                            PlannedOutputPublicationTargetMappingRepository mappingRepository,
                            PublicationTargetRepository publicationTargetRepository,
                            PlanningPreparerRepository planningPreparerRepository,
                            ShootingAssignmentRepository shootingAssignmentRepository,
                            ReviewCycleRepository reviewCycleRepository,
                            WorkflowTransitionService workflowService, AuthorizationService authorizationService,
                            AuditService auditService) {
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
        this.auditService = auditService;
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

    /** BRS-REQ-021: Category (free-text), Priority, SKU, Models/Talent, Folder Link. */
    @Transactional
    public ContentPlan updateParameters(User user, UUID contentPlanId, String categoryText, ContentPriority priority,
                                         String skuReference, boolean skuNotApplicable, List<String> talentNames,
                                         String folderLink) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        requirePlanningExecutionAuthority(user, plan.getWorkflowInstance());
        recordPreparer(plan, user);

        plan.setCategoryText(categoryText);
        plan.setContentPriority(priority);
        plan.setSku(skuReference, skuNotApplicable);
        plan.setFolderLink(folderLink);
        plan.setPreparedBy(user);

        talentEntryRepository.deleteByContentPlan(plan);
        if (talentNames != null) {
            for (String name : talentNames) {
                if (name != null && !name.isBlank()) {
                    talentEntryRepository.save(new ContentPlanTalentEntry(plan, name));
                }
            }
        }
        contentPlanRepository.save(plan);
        auditService.record(user, Optional.empty(), "PLANNING", "PLANNING_PARAMETERS_UPDATED", "content_plans",
                plan.getId(), null);
        return plan;
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

    @Transactional
    public PlannedOutput addPlannedOutput(User user, UUID contentPlanId, OutputType outputType, ReelType reelType,
                                           String titleDescription) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        requirePlanningExecutionAuthority(user, plan.getWorkflowInstance());
        PlannedOutput output = plannedOutputRepository.save(new PlannedOutput(plan, outputType, reelType, titleDescription));
        auditService.record(user, Optional.empty(), "PLANNING", "PLANNED_OUTPUT_ADDED", "planned_outputs",
                output.getId(), null);
        return output;
    }

    @Transactional
    public void mapPublicationScope(User user, UUID plannedOutputId, List<UUID> publicationTargetIds) {
        PlannedOutput output = plannedOutputRepository.findById(plannedOutputId)
                .orElseThrow(() -> DomainException.notFound("Planned Output not found: " + plannedOutputId));
        requirePlanningExecutionAuthority(user, output.getContentPlan().getWorkflowInstance());
        for (UUID targetId : publicationTargetIds) {
            PublicationTarget target = publicationTargetRepository.findById(targetId)
                    .orElseThrow(() -> DomainException.notFound("Publication Target not found: " + targetId));
            boolean exists = mappingRepository.findByPlannedOutput(output).stream()
                    .anyMatch(m -> m.getPublicationTarget().getId().equals(targetId));
            if (!exists) {
                mappingRepository.save(new PlannedOutputPublicationTargetMapping(output, target));
            }
        }
        auditService.record(user, Optional.empty(), "PLANNING", "PUBLICATION_SCOPE_MAPPED", "planned_outputs",
                output.getId(), null);
    }

    /** BRS-REQ-022: initial shooting assignment during Planning, Permission #4, one or more Camerapersons. */
    @Transactional
    public ShootingAssignment assignCameraperson(User assigner, UUID contentPlanId, User cameraperson) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        if (plan.getWorkflowInstance().getCurrentStatusCode() != WorkflowStatus.PL) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Initial shooting assignment is only valid during Planning (Stage 3)");
        }
        authorizationService.requireAuthority(assigner, OperationalPermission.PERM_04_SHOOT_ASSIGNMENT,
                LifecycleStage.PLANNING, plan.getWorkflowInstance());
        ShootingAssignment assignment = shootingAssignmentRepository.save(
                new ShootingAssignment(plan, cameraperson, assigner));
        auditService.record(assigner, Optional.empty(), "PLANNING", "CAMERAPERSON_ASSIGNED", "shooting_assignments",
                assignment.getId(), null);
        return assignment;
    }

    /** ERD-CON-026: Stage-3 parameters must be complete before Planning Review submission. */
    @Transactional
    public ReviewCycle submitPlanningReview(User submitter, UUID contentPlanId) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.PL) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Planning Review can only be submitted while the deliverable is in Planning");
        }
        requirePlanningExecutionAuthority(submitter, workflowInstance);
        if (!plan.isReadyForPlanningReview()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Planning parameters are incomplete: Content Priority, Planned Live/Shoot/Edit Dates and "
                            + "Folder Link are all mandatory before Planning Review submission (ERD-CON-026)");
        }
        int cycleNumber = nextCycleNumber(workflowInstance, GateType.PLANNING_REVIEW);
        ReviewCycle cycle = reviewCycleRepository.save(
                new ReviewCycle(workflowInstance, GateType.PLANNING_REVIEW, cycleNumber, submitter));
        workflowService.transition(workflowInstance, WorkflowStatus.PLRV, submitter, Optional.empty(),
                "SUBMIT_PLANNING_REVIEW", null);
        auditService.record(submitter, Optional.empty(), "PLANNING", "PLANNING_REVIEW_SUBMITTED", "content_plans",
                plan.getId(), null);
        return cycle;
    }

    /**
     * Permission #3. Self-review barrier governs the review DECISION, not submission - a
     * preparer may still submit their own plan (see class docs / SAD §"Round-3.2 traceability").
     */
    @Transactional
    public ContentPlan decidePlanningReview(User reviewer, UUID contentPlanId, boolean approve, String reason) {
        ContentPlan plan = requireContentPlan(contentPlanId);
        WorkflowInstance workflowInstance = plan.getWorkflowInstance();
        if (workflowInstance.getCurrentStatusCode() != WorkflowStatus.PLRV) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "Planning Review decisions are only valid while under review");
        }
        Optional<PermissionGrant> actingGrant = authorizationService.requireAuthority(reviewer,
                OperationalPermission.PERM_03_PLANNING_REVIEW, LifecycleStage.PLANNING, workflowInstance);
        boolean isPreparer = planningPreparerRepository.findByContentPlan(plan).stream()
                .anyMatch(p -> p.getPreparer().getId().equals(reviewer.getId()));
        if (actingGrant.isPresent() && isPreparer) {
            throw DomainException.forbidden(ErrorCode.PERM_SELF_APPROVAL_PROHIBITED,
                    "Cannot make a review decision on a plan you prepared");
        }

        List<ReviewCycle> cycles = reviewCycleRepository
                .findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, GateType.PLANNING_REVIEW);
        ReviewCycle cycle = cycles.stream().filter(c -> c.getDecidedAt() == null).findFirst()
                .orElseThrow(() -> DomainException.notFound("No pending Planning Review submission found"));

        if (approve) {
            List<ShootingAssignment> activeAssignments = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
            if (activeAssignments.isEmpty()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "At least one Cameraperson must be assigned before Planning Review can be approved");
            }
            cycle.decide(reviewer, "APPROVED", null, actingGrant.orElse(null));
            reviewCycleRepository.save(cycle);
            workflowService.transition(workflowInstance, WorkflowStatus.PLAP, reviewer, actingGrant,
                    "APPROVE_PLANNING", null);
            workflowService.transition(workflowInstance, WorkflowStatus.SA, reviewer, actingGrant,
                    "ACTIVATE_SHOOTING", null);
            auditService.record(reviewer, actingGrant, "PLANNING", "PLANNING_APPROVED", "content_plans",
                    plan.getId(), null);
        } else {
            if (reason == null || reason.isBlank()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "A rework reason is mandatory (ERD-CON-059)");
            }
            cycle.decide(reviewer, "REQUEST_REWORK", reason, actingGrant.orElse(null));
            reviewCycleRepository.save(cycle);
            workflowService.transition(workflowInstance, WorkflowStatus.PL, reviewer, actingGrant,
                    "REQUEST_REWORK_PLANNING", reason);
            auditService.record(reviewer, actingGrant, "PLANNING", "PLANNING_REWORK_REQUESTED", "content_plans",
                    plan.getId(), reason);
        }
        return plan;
    }

    private int nextCycleNumber(WorkflowInstance workflowInstance, GateType gateType) {
        return reviewCycleRepository.findByWorkflowInstanceAndGateTypeOrderByCycleNumberDesc(workflowInstance, gateType)
                .stream().findFirst().map(c -> c.getCycleNumber() + 1).orElse(1);
    }
}
