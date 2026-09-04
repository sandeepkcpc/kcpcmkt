package com.kcpc.mkt.discussion.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.discussion.domain.StageComment;
import com.kcpc.mkt.discussion.repository.StageCommentRepository;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.notification.domain.NotificationType;
import com.kcpc.mkt.notification.service.NotificationService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
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
    private final ContentPlanTalentEntryRepository talentEntryRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public StageCommentService(StageCommentRepository stageCommentRepository, ContentPlanRepository contentPlanRepository,
                                ShootingAssignmentRepository shootingAssignmentRepository,
                                EditingAssignmentRepository editingAssignmentRepository,
                                PublishingAssignmentRepository publishingAssignmentRepository,
                                ContentPlanTalentEntryRepository talentEntryRepository,
                                AuthorizationService authorizationService, AuditService auditService,
                                NotificationService notificationService) {
        this.stageCommentRepository = stageCommentRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.talentEntryRepository = talentEntryRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.notificationService = notificationService;
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
        notifyCommentRecipients(actor, plan, stage, comment);
        return comment;
    }

    /**
     * Notification recipients are the mirror image of who is currently "on" this thread
     * ({@link #requireCommentAuthority}'s own native-authority-or-active-stage-assignee split),
     * never a blanket broadcast: an Employee's comment notifies every active native-authority user
     * (MM/CEO - {@link AuthorizationService#findActiveNativeAuthorityUsers()}); an MM/CEO's own
     * comment instead notifies every currently active assignee of THAT SAME stage
     * ({@link #currentStageAssignees}) - never another stage's assignees, and never a second
     * blanket MM/CEO broadcast on top (matches the existing REVIEW_REQUIRED precedent of never
     * notifying native authority for every event). The commenter is always excluded, and every
     * recipient list here is already deduplicated by user id at its source, so a person reachable
     * two ways (e.g. Model + Cameraperson) is still notified exactly once.
     */
    private void notifyCommentRecipients(User actor, ContentPlan plan, LifecycleStage stage, StageComment comment) {
        List<User> recipients = authorizationService.hasNativeAuthority(actor)
                ? currentStageAssignees(plan, stage)
                : authorizationService.findActiveNativeAuthorityUsers();
        // Entity-id-based, same TYPE:EntityName:id convention every other notification type
        // uses - a re-processing of this exact comment (same id) is a no-op in NotificationService.
        String eventReference = "COMMENT_ADDED:StageComment:" + comment.getId();
        String targetTab = targetTabFor(stage);
        String message = actor.getFullName() + " commented on " + plan.getContentId()
                + ": \"" + truncatePreview(comment.getCommentText()) + "\"";
        for (User recipient : recipients) {
            if (recipient.getId().equals(actor.getId())) {
                continue;
            }
            notificationService.notify(recipient, NotificationType.COMMENT_ADDED, "New Comment", message,
                    plan, eventReference, targetTab);
        }
    }

    /** Every currently active assignee of exactly this stage - Shoot additionally includes
     * Model/Talent ("where applicable"), Edit/Publishing do not. Deduplicated by user id, same
     * LinkedHashMap-keyed-by-id pattern {@code AdminActionService#currentlyAffectedUsers} already
     * uses for the same reason (one real person can hold more than one of these at once). */
    private List<User> currentStageAssignees(ContentPlan plan, LifecycleStage stage) {
        LinkedHashMap<UUID, User> byId = new LinkedHashMap<>();
        switch (stage) {
            case SHOOTING -> {
                shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                        .forEach(a -> byId.put(a.getCameraperson().getId(), a.getCameraperson()));
                talentEntryRepository.findByContentPlan(plan).stream()
                        .filter(t -> t.getTalentUser() != null)
                        .forEach(t -> byId.put(t.getTalentUser().getId(), t.getTalentUser()));
            }
            case EDITING -> editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                    .forEach(a -> byId.put(a.getEditor().getId(), a.getEditor()));
            case PUBLISHING -> publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)
                    .forEach(a -> byId.put(a.getPublisher().getId(), a.getPublisher()));
            default -> {
            }
        }
        return List.copyOf(byId.values());
    }

    /** Reuses the SAME ?tab= value DeliverableMvcController#view already accepts (CONTENT_DETAIL_TABS). */
    private static String targetTabFor(LifecycleStage stage) {
        return switch (stage) {
            case SHOOTING -> "shoot";
            case EDITING -> "edit";
            case PUBLISHING -> "publishing";
            default -> null;
        };
    }

    /** Long comments are truncated in the notification preview; the full text remains visible in
     * the thread itself after click-through (StageComment.commentText is never altered here). */
    private static String truncatePreview(String commentText) {
        String trimmed = commentText.strip();
        int maxLen = 100;
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen).stripTrailing() + "…";
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
