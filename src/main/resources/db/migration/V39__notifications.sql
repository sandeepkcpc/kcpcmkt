-- Role-based Notification System (MVP): one row per (recipient, logical event). Duplicate
-- prevention is enforced at the DB level via UNIQUE(recipient_user_id, event_reference) - callers
-- tie event_reference to the ID of the underlying real record the notification is about (an
-- assignment row, a reassignment-assignee row, a review cycle, a reschedule/cancellation record),
-- so an idempotent re-save of that same record (e.g. PublishingService#assignPublisher returning
-- an already-existing active row) naturally produces the same reference and is skipped, while a
-- genuinely new record (a real reassignment, a new review cycle) always gets a fresh reference.
CREATE TABLE notifications (
    notification_id      UUID PRIMARY KEY,
    recipient_user_id       UUID NOT NULL REFERENCES users (user_id),
    notification_type          VARCHAR(30) NOT NULL,
    title                          VARCHAR(120) NOT NULL,
    message                           TEXT NOT NULL,
    content_plan_id                     UUID REFERENCES content_plans (content_plan_id),
    event_reference                        VARCHAR(200) NOT NULL,
    read_at                                   TIMESTAMPTZ,
    created_at                                   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notifications_type CHECK (notification_type IN (
        'TASK_ASSIGNED', 'TASK_REASSIGNED', 'TASK_REASSIGNED_AWAY', 'REVIEW_REQUIRED',
        'REVIEW_APPROVED', 'CHANGES_REQUIRED', 'TASK_COMPLETED', 'TASK_RESCHEDULED', 'TASK_CANCELLED')),
    CONSTRAINT uq_notifications_dedup UNIQUE (recipient_user_id, event_reference)
);

-- "latest N for this user" / "view all" ordering.
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_user_id, created_at DESC);
-- Unread-count lookup (header badge) - partial index, only unread rows are ever scanned by it.
CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient_user_id) WHERE read_at IS NULL;
