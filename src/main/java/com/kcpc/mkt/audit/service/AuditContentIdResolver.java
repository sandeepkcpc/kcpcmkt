package com.kcpc.mkt.audit.service;

import com.kcpc.mkt.drive.repository.ContentDriveProvisioningRepository;
import com.kcpc.mkt.idea.repository.IdeaDescriptionCorrectionRepository;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.repository.PredefinedMarkCorrectionRepository;
import com.kcpc.mkt.performance.repository.CreativePerformanceScorecardRepository;
import com.kcpc.mkt.performance.repository.PerformanceMetricCorrectionRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.publishing.repository.PublicationEvidenceCorrectionRepository;
import com.kcpc.mkt.publishing.repository.PublicationTargetNaRecordRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Logs (Audit History) "Content ID" column: resolves a {@link com.kcpc.mkt.audit.domain.SystemAuditLog}'s
 * {@code targetEntityName}/{@code targetEntityId} to the owning {@link ContentPlan}'s business
 * Content ID (e.g. "C-0926-0001") wherever the app's existing entity relationships make that
 * possible - display-only, read-only, never a new business calculation and never a second
 * "outstanding"/audit-creation concept. Deliberately a SEPARATE class from
 * {@code AdminReportingService#resolveTarget} (which only needs to cover the 4 target types
 * Administrative Actions itself governs, and returns a richer display string, not a bare Content
 * ID) - the Logs page shows every audited event type system-wide, so this covers every target
 * type that genuinely traces back to a Content Plan. Every hop below was verified directly against
 * the actual entity getters before being added, not guessed. Target types with no content
 * relationship at all (users, business_roles, permission_grants, mark_catalogue_entries,
 * categories, platforms, company_channels, publication_targets, user_csv_import_batch) correctly
 * fall through to {@code null} - the JSP renders that as "-", never a fabricated Content ID.
 */
@Service
public class AuditContentIdResolver {

    private final ContentPlanRepository contentPlanRepository;
    private final IdeaRepository ideaRepository;
    private final WorkHoldRecordRepository workHoldRecordRepository;
    private final PlannedOutputRepository plannedOutputRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final ActualPublicationEventRepository actualPublicationEventRepository;
    private final ContentDriveProvisioningRepository contentDriveProvisioningRepository;
    private final PublicationTargetNaRecordRepository publicationTargetNaRecordRepository;
    private final PublicationEvidenceCorrectionRepository publicationEvidenceCorrectionRepository;
    private final PredefinedMarkCorrectionRepository predefinedMarkCorrectionRepository;
    private final IdeaDescriptionCorrectionRepository ideaDescriptionCorrectionRepository;
    private final CreativePerformanceScorecardRepository creativePerformanceScorecardRepository;
    private final PerformanceMetricCorrectionRepository performanceMetricCorrectionRepository;
    private final EntityManager entityManager;

    public AuditContentIdResolver(ContentPlanRepository contentPlanRepository, IdeaRepository ideaRepository,
                                   WorkHoldRecordRepository workHoldRecordRepository,
                                   PlannedOutputRepository plannedOutputRepository,
                                   ShootingAssignmentRepository shootingAssignmentRepository,
                                   EditingAssignmentRepository editingAssignmentRepository,
                                   PublishingAssignmentRepository publishingAssignmentRepository,
                                   ActualPublicationEventRepository actualPublicationEventRepository,
                                   ContentDriveProvisioningRepository contentDriveProvisioningRepository,
                                   PublicationTargetNaRecordRepository publicationTargetNaRecordRepository,
                                   PublicationEvidenceCorrectionRepository publicationEvidenceCorrectionRepository,
                                   PredefinedMarkCorrectionRepository predefinedMarkCorrectionRepository,
                                   IdeaDescriptionCorrectionRepository ideaDescriptionCorrectionRepository,
                                   CreativePerformanceScorecardRepository creativePerformanceScorecardRepository,
                                   PerformanceMetricCorrectionRepository performanceMetricCorrectionRepository,
                                   EntityManager entityManager) {
        this.contentPlanRepository = contentPlanRepository;
        this.ideaRepository = ideaRepository;
        this.workHoldRecordRepository = workHoldRecordRepository;
        this.plannedOutputRepository = plannedOutputRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.actualPublicationEventRepository = actualPublicationEventRepository;
        this.contentDriveProvisioningRepository = contentDriveProvisioningRepository;
        this.publicationTargetNaRecordRepository = publicationTargetNaRecordRepository;
        this.publicationEvidenceCorrectionRepository = publicationEvidenceCorrectionRepository;
        this.predefinedMarkCorrectionRepository = predefinedMarkCorrectionRepository;
        this.ideaDescriptionCorrectionRepository = ideaDescriptionCorrectionRepository;
        this.creativePerformanceScorecardRepository = creativePerformanceScorecardRepository;
        this.performanceMetricCorrectionRepository = performanceMetricCorrectionRepository;
        this.entityManager = entityManager;
    }

    /**
     * Resolved WITHIN one transaction so every lazy hop (e.g. {@code PlannedOutput.contentPlan})
     * is safely traversed before returning a plain String - the MVC layer runs with
     * open-in-view disabled, so a lazy association read there would otherwise throw. Never
     * throws: a resolution failure (e.g. a since-deleted row) returns {@code null} rather than
     * blocking the report, exactly like {@code AdminReportingService#resolveTarget}'s own
     * established convention.
     */
    @Transactional(readOnly = true)
    public String resolveContentId(String targetEntityName, UUID targetEntityId) {
        if (targetEntityName == null || targetEntityId == null) {
            return null;
        }
        try {
            return switch (targetEntityName) {
                case "content_plans" -> contentPlanRepository.findById(targetEntityId)
                        .map(ContentPlan::getContentId).orElse(null);
                case "ideas" -> ideaRepository.findById(targetEntityId)
                        .flatMap(contentPlanRepository::findByIdea).map(ContentPlan::getContentId).orElse(null);
                case "workflow_instances" -> contentPlanByWorkflowInstanceId(targetEntityId);
                case "work_hold_records" -> workHoldRecordRepository.findById(targetEntityId)
                        .map(h -> contentPlanByWorkflowInstanceId(h.getWorkflowInstance().getId())).orElse(null);
                case "planned_outputs" -> plannedOutputRepository.findById(targetEntityId)
                        .map(o -> o.getContentPlan().getContentId()).orElse(null);
                case "shooting_assignments" -> shootingAssignmentRepository.findById(targetEntityId)
                        .map(a -> a.getContentPlan().getContentId()).orElse(null);
                case "editing_assignments" -> editingAssignmentRepository.findById(targetEntityId)
                        .map(a -> a.getContentPlan().getContentId()).orElse(null);
                case "publishing_assignments" -> publishingAssignmentRepository.findById(targetEntityId)
                        .map(a -> a.getContentPlan().getContentId()).orElse(null);
                case "actual_publication_events" -> actualPublicationEventRepository.findById(targetEntityId)
                        .map(e -> e.getContentPlan().getContentId()).orElse(null);
                case "content_drive_provisioning" -> contentDriveProvisioningRepository.findById(targetEntityId)
                        .map(p -> p.getContentPlan().getContentId()).orElse(null);
                case "publication_target_na_records" -> publicationTargetNaRecordRepository.findById(targetEntityId)
                        .map(r -> r.getPlannedOutput().getContentPlan().getContentId()).orElse(null);
                case "publication_evidence_corrections" -> publicationEvidenceCorrectionRepository.findById(targetEntityId)
                        .map(c -> c.getEvent().getContentPlan().getContentId()).orElse(null);
                case "predefined_mark_corrections" -> predefinedMarkCorrectionRepository.findById(targetEntityId)
                        .map(c -> c.getPredefinedMark().getContentPlan().getContentId()).orElse(null);
                case "idea_description_corrections" -> ideaDescriptionCorrectionRepository.findById(targetEntityId)
                        .flatMap(c -> contentPlanRepository.findByIdea(c.getIdea()))
                        .map(ContentPlan::getContentId).orElse(null);
                case "creative_performance_scorecards" -> creativePerformanceScorecardRepository.findById(targetEntityId)
                        .map(s -> s.getObligation().getEvent().getContentPlan().getContentId()).orElse(null);
                case "performance_metric_corrections" -> performanceMetricCorrectionRepository.findById(targetEntityId)
                        .map(c -> c.getScorecard().getObligation().getEvent().getContentPlan().getContentId())
                        .orElse(null);
                // users, business_roles, permission_grants, mark_catalogue_entries, categories, platforms,
                // company_channels, publication_targets, user_csv_import_batch - genuinely never content-related.
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Same JPQL lookup {@code AdminReportingService#contentPlanByWorkflowInstance} already uses -
     * no {@code ContentPlanRepository} derived-query method exists for this join, since a
     * WorkflowInstance is shared by an Idea and (once approved) its resulting Content Plan. */
    private String contentPlanByWorkflowInstanceId(UUID workflowInstanceId) {
        List<ContentPlan> matches = entityManager
                .createQuery("select cp from ContentPlan cp where cp.workflowInstance.id = :wiId", ContentPlan.class)
                .setParameter("wiId", workflowInstanceId).setMaxResults(1).getResultList();
        return matches.isEmpty() ? null : matches.get(0).getContentId();
    }
}
