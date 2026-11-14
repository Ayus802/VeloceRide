package com.rideshare.notification.repository;

import com.rideshare.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndRead(UUID recipientId, boolean read, Pageable pageable);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    /** Scoped by recipient so users can never touch another user's notification. */
    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipient.id = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") UUID recipientId);
}
