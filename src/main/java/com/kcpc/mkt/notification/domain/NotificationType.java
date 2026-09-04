package com.kcpc.mkt.notification.domain;

/**
 * MVP notification categories - meaningful, actionable workflow events only, never raw
 * activity/UI logging (see NotificationService's own class javadoc). "Stage Ready"/"Publishing
 * Ready" are deliberately not separate types: they fire at the exact same fold-in event
 * TASK_ASSIGNED already covers (a fresh Editor/Publisher assignment), so a second notification for
 * the same event would just be noise. Deadline Approaching/Task Delayed (no scheduler
 * infrastructure exists in this app) remain out of MVP scope.
 *
 * <p>{@link #COMMENT_ADDED} deliberately has no @mention concept - see
 * {@code StageCommentService#addComment}'s own comment for the exact recipient rule (an Employee's
 * comment notifies MM/CEO; an MM/CEO comment notifies that stage's current assignees).
 */
public enum NotificationType {
    TASK_ASSIGNED,
    TASK_REASSIGNED,
    TASK_REASSIGNED_AWAY,
    REVIEW_REQUIRED,
    REVIEW_APPROVED,
    CHANGES_REQUIRED,
    TASK_COMPLETED,
    TASK_RESCHEDULED,
    TASK_CANCELLED,
    COMMENT_ADDED
}
