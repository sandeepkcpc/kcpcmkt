package com.kcpc.mkt.planning.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * ERD-TBL-010 content_plans: created atomically at Idea Approval together with the Content ID
 * (API-OP-013). Planning-stage parameter fields are nullable here and validated required only
 * before Planning Review submission is allowed (ERD-CON-026, enforced in the Planning service
 * added in Phase 4).
 */
@Entity
@Table(name = "content_plans")
@AttributeOverride(name = "id", column = @Column(name = "content_plan_id"))
public class ContentPlan extends BaseEntity {

    // EAGER: read directly in MVC/JSP views outside any open transaction (open-in-view is
    // disabled) - see docs/IMPLEMENTATION_DECISIONS.md ENG-005 for the same pattern.
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "idea_id", nullable = false, unique = true)
    private Idea idea;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false, unique = true)
    private WorkflowInstance workflowInstance;

    /** ERD-CON-036: immutable once allocated - no setter. */
    @Column(name = "content_id", nullable = false, unique = true, length = 20)
    private String contentId;

    @Column(name = "category_text", columnDefinition = "text")
    private String categoryText;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_priority", length = 10)
    private ContentPriority contentPriority;

    @Column(name = "sku_reference", length = 100)
    private String skuReference;

    @Column(name = "sku_not_applicable", nullable = false)
    private boolean skuNotApplicable = false;

    @Column(name = "planned_live_date")
    private LocalDate plannedLiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "planning_mode", nullable = false, length = 10)
    private PlanningMode planningMode = PlanningMode.STANDARD;

    @Column(name = "urgency_reason", columnDefinition = "text")
    private String urgencyReason;

    @Column(name = "planned_shoot_date")
    private LocalDate plannedShootDate;

    @Column(name = "planned_edit_date")
    private LocalDate plannedEditDate;

    @Column(name = "folder_link", columnDefinition = "text")
    private String folderLink;

    /**
     * ENG-046: one common Description per stage per Content Plan (not per individual assignee) -
     * shoot/edit/publishing instructions for whoever is assigned to that stage. Plain nullable
     * columns here rather than a separate table: each stage maps 1:1 to this ContentPlan, the
     * value is a single mutable field (not a growing list, unlike the comments below), and
     * "retained through stage transitions" falls out for free since a column is never deleted.
     */
    @Column(name = "shoot_description", columnDefinition = "text")
    private String shootDescription;

    @Column(name = "edit_description", columnDefinition = "text")
    private String editDescription;

    @Column(name = "publishing_description", columnDefinition = "text")
    private String publishingDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prepared_by_user_id")
    private User preparedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContentPlan() {
    }

    public ContentPlan(Idea idea, WorkflowInstance workflowInstance, String contentId) {
        this.idea = idea;
        this.workflowInstance = workflowInstance;
        this.contentId = contentId;
    }

    /** BRS-REQ-021: optional free-text, single field, one/many values, or blank - no reference-table validation. */
    public void setCategoryText(String categoryText) {
        this.categoryText = categoryText;
    }

    public void setContentPriority(ContentPriority contentPriority) {
        this.contentPriority = contentPriority;
    }

    /** ERD-CON-009: SKU N/A mutual exclusion. */
    public void setSku(String skuReference, boolean skuNotApplicable) {
        if (skuNotApplicable && skuReference != null && !skuReference.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "sku_reference must be null when sku_not_applicable is true (ERD-CON-009)");
        }
        this.skuReference = skuNotApplicable ? null : skuReference;
        this.skuNotApplicable = skuNotApplicable;
    }

    /**
     * BRS-REQ-027 / BRS-REQ-086: STANDARD defaults shoot=live-5d, edit=live-2d (overridable);
     * URGENT requires manual dates + mandatory urgency reason. ERD-CON-065/066 enforced here
     * before the DB constraints re-verify.
     */
    public void setPlanningScheduleStandard(LocalDate plannedLiveDate, LocalDate shootDate, LocalDate editDate) {
        this.planningMode = PlanningMode.STANDARD;
        this.urgencyReason = null;
        this.plannedLiveDate = plannedLiveDate;
        this.plannedShootDate = shootDate;
        this.plannedEditDate = editDate;
        validateChronology();
    }

    public void setPlanningScheduleUrgent(LocalDate plannedLiveDate, LocalDate shootDate, LocalDate editDate,
                                           String urgencyReason) {
        if (urgencyReason == null || urgencyReason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "urgency_reason is mandatory when planning_mode = URGENT (ERD-CON-065)");
        }
        this.planningMode = PlanningMode.URGENT;
        this.urgencyReason = urgencyReason;
        this.plannedLiveDate = plannedLiveDate;
        this.plannedShootDate = shootDate;
        this.plannedEditDate = editDate;
        validateChronology();
    }

    private void validateChronology() {
        // ERD-CON-066: shoot <= edit <= live when all present; equal dates permitted under URGENT.
        if (plannedShootDate != null && plannedEditDate != null && plannedShootDate.isAfter(plannedEditDate)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "planned_shoot_date must be on or before planned_edit_date (ERD-CON-066)");
        }
        if (plannedEditDate != null && plannedLiveDate != null && plannedEditDate.isAfter(plannedLiveDate)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "planned_edit_date must be on or before planned_live_date (ERD-CON-066)");
        }
    }

    /** Permission #10 Reschedule: dates only, planning_mode/urgency_reason untouched. Performance Due Date is separate and never affected. */
    public void applyReschedule(LocalDate newShootDate, LocalDate newEditDate, LocalDate newLiveDate) {
        this.plannedShootDate = newShootDate;
        this.plannedEditDate = newEditDate;
        this.plannedLiveDate = newLiveDate;
        validateChronology();
    }

    public void setFolderLink(String folderLink) {
        this.folderLink = folderLink;
    }

    public void setShootDescription(String shootDescription) {
        this.shootDescription = shootDescription;
    }

    public void setEditDescription(String editDescription) {
        this.editDescription = editDescription;
    }

    public void setPublishingDescription(String publishingDescription) {
        this.publishingDescription = publishingDescription;
    }

    public void setPreparedBy(User preparedBy) {
        this.preparedBy = preparedBy;
    }

    /** Always true - IdeaService#approve enforces every one of these fields before a Content Plan
     * can even be created. Kept as an informational API field on ContentPlanResponse. */
    public boolean isFullyPlanned() {
        return contentPriority != null && plannedLiveDate != null && plannedShootDate != null
                && plannedEditDate != null && folderLink != null && !folderLink.isBlank();
    }

    public Idea getIdea() {
        return idea;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public String getContentId() {
        return contentId;
    }

    public String getCategoryText() {
        return categoryText;
    }

    public ContentPriority getContentPriority() {
        return contentPriority;
    }

    public String getSkuReference() {
        return skuReference;
    }

    public boolean isSkuNotApplicable() {
        return skuNotApplicable;
    }

    public LocalDate getPlannedLiveDate() {
        return plannedLiveDate;
    }

    public PlanningMode getPlanningMode() {
        return planningMode;
    }

    public String getUrgencyReason() {
        return urgencyReason;
    }

    public LocalDate getPlannedShootDate() {
        return plannedShootDate;
    }

    public LocalDate getPlannedEditDate() {
        return plannedEditDate;
    }

    public String getFolderLink() {
        return folderLink;
    }

    public String getShootDescription() {
        return shootDescription;
    }

    public String getEditDescription() {
        return editDescription;
    }

    public String getPublishingDescription() {
        return publishingDescription;
    }

    public User getPreparedBy() {
        return preparedBy;
    }
}
