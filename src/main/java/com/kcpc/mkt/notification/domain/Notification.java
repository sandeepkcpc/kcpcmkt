package com.kcpc.mkt.notification.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One row per (recipient, logical workflow event) - see V39__notifications.sql's own comment for
 * the event_reference/duplicate-prevention contract. */
@Entity
@Table(name = "notifications")
@AttributeOverride(name = "id", column = @Column(name = "notification_id"))
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    // Nullable: not every future notification type needs to be Content-Plan-specific, even
    // though every MVP category currently is. EAGER: read directly by the header dropdown/View
    // all JSPs outside any open transaction (open-in-view: false - see ShootingAssignment's own
    // ENG-005 precedent for this exact same reason), not through a service-layer DTO mapping.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "content_plan_id")
    private ContentPlan contentPlan;

    @Column(name = "event_reference", nullable = false, length = 200)
    private String eventReference;

    // Nullable: an optional deep-link hint (e.g. "shoot"/"edit"/"publishing") reusing the SAME
    // ?tab= query param DeliverableMvcController#view already accepts - lets a notification's
    // click-through land directly on the relevant section (e.g. a comment's own stage panel)
    // instead of always the generic Overview tab. Every pre-existing notification type leaves this
    // null (their click-through is unchanged, plain "/app/deliverables/{id}").
    @Column(name = "target_tab", length = 20)
    private String targetTab;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(User recipient, NotificationType type, String title, String message,
                         ContentPlan contentPlan, String eventReference) {
        this(recipient, type, title, message, contentPlan, eventReference, null);
    }

    public Notification(User recipient, NotificationType type, String title, String message,
                         ContentPlan contentPlan, String eventReference, String targetTab) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.message = message;
        this.contentPlan = contentPlan;
        this.eventReference = eventReference;
        this.targetTab = targetTab;
    }

    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public String getEventReference() {
        return eventReference;
    }

    public String getTargetTab() {
        return targetTab;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
