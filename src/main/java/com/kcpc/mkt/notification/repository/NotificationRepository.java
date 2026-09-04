package com.kcpc.mkt.notification.repository;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.notification.domain.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByRecipientAndEventReference(User recipient, String eventReference);

    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Limit limit);

    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient);

    long countByRecipientAndReadAtIsNull(User recipient);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient = :recipient and n.readAt is null")
    int markAllReadForRecipient(@Param("recipient") User recipient, @Param("now") Instant now);
}
