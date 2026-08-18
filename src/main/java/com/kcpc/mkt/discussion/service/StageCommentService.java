package com.kcpc.mkt.discussion.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.discussion.domain.StageComment;
import com.kcpc.mkt.discussion.repository.StageCommentRepository;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ENG-046: one Jira-style discussion thread per (Content Plan, stage) - Shoot/Edit/Publishing
 * threads never mix. Not in the frozen ERD/API spec.
 */
@Service
public class StageCommentService {

    private final StageCommentRepository stageCommentRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public StageCommentService(StageCommentRepository stageCommentRepository, ContentPlanRepository contentPlanRepository,
                                ShootingAssignmentRepository shootingAssignmentRepository,
                                EditingAssignmentRepository editingAssignmentRepository,
                                PublishingAssignmentRepository publishingAssignmentRepository,
                                AuthorizationService authorizationService, AuditService auditService) {
        this.stageCommentRepository = stageCommentRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private ContentPlan requirePlan(UUID contentPlanId) {
        return contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
    }

    /**
     * Comment authorship is native CEO/MM authority or whoever is currently an active assignee on
     * this exact stage - the same "management vs the assigned employee" split ENG-043/044
     * established for execution actions, applied here to who may speak on that stage's thread.
     * Reading a thread is not separately gated - it's visible to whoever can already load the
     * deliverable detail page, matching that page's existing (page-level, not per-section)
     * visibility model.
     */
    private void requireCommentAuthority(User actor, ContentPlan plan, LifecycleStage stage) {
        if (authorizationService.hasNativeAuthority(actor)) {
            return;
        }
        boolean isActiveAssignee = switch (stage) {
            case SHOOTING -> shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                    .anyMatch(a -> a.getCameraperson().getId().equals(actor.getId()));
            case EDITING -> editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                    .anyMatch(a -> a.getEditor().getId().equals(actor.getId()));
            case PUBLISHING -> publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                    .anyMatch(a -> a.getPublisher().getId().equals(actor.getId()));
            default -> false;
        };
        if (!isActiveAssignee) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED,
                    "Only CEO/MM or the currently assigned " + stage + " team can comment on this thread");
        }
    }

    @Transactional
    public StageComment addComment(User actor, UUID contentPlanId, LifecycleStage stage, String commentText) {
        if (commentText == null || commentText.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Comment text is required");
        }
        ContentPlan plan = requirePlan(contentPlanId);
        requireCommentAuthority(actor, plan, stage);
        StageComment comment = stageCommentRepository.save(new StageComment(plan, stage, actor, commentText));
        auditService.record(actor, Optional.empty(), stage.name(), "STAGE_COMMENT_ADDED", "stage_comments",
                comment.getId(), null);
        return comment;
    }

    public List<StageComment> listComments(UUID contentPlanId, LifecycleStage stage) {
        ContentPlan plan = requirePlan(contentPlanId);
        return stageCommentRepository.findByContentPlanAndStageOrderByCreatedAtAsc(plan, stage);
    }

    private StageComment requireOwnComment(User actor, UUID contentPlanId, UUID commentId) {
        StageComment comment = stageCommentRepository.findById(commentId)
                .orElseThrow(() -> DomainException.notFound("Comment not found: " + commentId));
        if (!comment.getContentPlan().getId().equals(contentPlanId)) {
            throw DomainException.notFound("Comment not found: " + commentId);
        }
        // ENG-050: deliberately NOT native-authority-bypassable - "sirf apne comment par" (only on
        // your own comment) is an explicit exception even to CEO/MM's usual native authority, unlike
        // every other authority check in this app.
        if (!comment.getCommenter().getId().equals(actor.getId())) {
            throw DomainException.forbidden(ErrorCode.PERM_ACCESS_CLASS_DENIED, "You can only edit or delete your own comment");
        }
        if (comment.isDeleted()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "This comment was already deleted");
        }
        return comment;
    }

    @Transactional
    public StageComment editComment(User actor, UUID contentPlanId, UUID commentId, String newText) {
        if (newText == null || newText.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Comment text is required");
        }
        StageComment comment = requireOwnComment(actor, contentPlanId, commentId);
        String previous = comment.getCommentText();
        comment.editText(newText);
        stageCommentRepository.save(comment);
        String auditReason = "Old: \"" + previous + "\" -> New: \"" + newText + "\"";
        auditService.record(actor, Optional.empty(), comment.getStage().name(), "STAGE_COMMENT_EDITED", "stage_comments",
                comment.getId(), auditReason);
        return comment;
    }

    @Transactional
    public void deleteComment(User actor, UUID contentPlanId, UUID commentId) {
        StageComment comment = requireOwnComment(actor, contentPlanId, commentId);
        comment.softDelete();
        stageCommentRepository.save(comment);
        auditService.record(actor, Optional.empty(), comment.getStage().name(), "STAGE_COMMENT_DELETED", "stage_comments",
                comment.getId(), "Deleted text: \"" + comment.getCommentText() + "\"");
    }
}
