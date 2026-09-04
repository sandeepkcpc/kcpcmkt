package com.kcpc.mkt.notification.service;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.notification.domain.Notification;
import com.kcpc.mkt.notification.domain.NotificationType;
import com.kcpc.mkt.notification.repository.NotificationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Role-based Notification System (MVP): meaningful, actionable workflow events only - callers
 * (IdeaService, ShootingService, EditingService, PublishingService, AdminActionService,
 * PerformanceService) call {@link #notify} at the exact point a real event already happens, never
 * for raw activity/UI logging (page views, tab switches, Drive link clicks - none of that reaches
 * this service anywhere in this codebase). Deliberately does not touch, wrap, or gate any of the
 * business logic at those call sites - every call here is a pure side effect after the real state
 * change already happened and would happen identically with this service entirely stubbed out.
 *
 * <p>Duplicate prevention: {@code eventReference} must be a stable identifier tied to the
 * underlying real record the notification is about (see each call site's own comment for its
 * exact scheme) - {@link #notify} is a no-op if a notification already exists for this exact
 * (recipient, eventReference) pair, backed by both an existence check here and the DB's own
 * {@code uq_notifications_dedup} constraint as a race-safety net.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(User recipient, NotificationType type, String title, String message,
                        ContentPlan contentPlan, String eventReference) {
        notify(recipient, type, title, message, contentPlan, eventReference, null);
    }

    /** Same as {@link #notify(User, NotificationType, String, String, ContentPlan, String)}, plus
     * an optional {@code targetTab} deep-link hint (see {@link Notification#getTargetTab()}) - used
     * by comment notifications to land click-through on the relevant stage's own discussion panel. */
    @Transactional
    public void notify(User recipient, NotificationType type, String title, String message,
                        ContentPlan contentPlan, String eventReference, String targetTab) {
        if (recipient == null || eventReference == null || eventReference.isBlank()) {
            return;
        }
        if (notificationRepository.findByRecipientAndEventReference(recipient, eventReference).isPresent()) {
            return;
        }
        notificationRepository.save(new Notification(recipient, type, title, message, contentPlan, eventReference, targetTab));
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByRecipientAndReadAtIsNull(user);
    }

    /** Header dropdown's own "latest N" list - never the full history (see {@link #listAll}). */
    @Transactional(readOnly = true)
    public List<Notification> listRecent(User user, int limit) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user, Limit.of(limit));
    }

    /** "View all notifications" page. */
    @Transactional(readOnly = true)
    public List<Notification> listAll(User user) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
    }

    /** A user may only read/mark their own notifications - never another employee's, regardless of
     * how the id was obtained (spec: no cross-user access via an API parameter). */
    @Transactional
    public void markRead(User user, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> DomainException.notFound("Notification not found: " + notificationId));
        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw DomainException.forbidden(ErrorCode.RESOURCE_ACCESS_DENIED,
                    "You may only access your own notifications");
        }
        notification.markRead();
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.markAllReadForRecipient(user, Instant.now());
    }
}
