-- Comment notifications (COMMENT_ADDED) + an optional deep-link target tab (e.g. 'shoot'/'edit'/
-- 'publishing') so a comment notification's click-through can land directly on the stage's own
-- discussion panel on Content Detail instead of always the generic Overview tab - reuses the SAME
-- ?tab= query param DeliverableMvcController#view already accepts (CONTENT_DETAIL_TABS), nullable
-- and unused by every pre-existing notification type (their click-through is unchanged).
ALTER TABLE notifications ADD COLUMN target_tab VARCHAR(20);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (notification_type IN (
    'TASK_ASSIGNED', 'TASK_REASSIGNED', 'TASK_REASSIGNED_AWAY', 'REVIEW_REQUIRED',
    'REVIEW_APPROVED', 'CHANGES_REQUIRED', 'TASK_COMPLETED', 'TASK_RESCHEDULED', 'TASK_CANCELLED',
    'COMMENT_ADDED'));
