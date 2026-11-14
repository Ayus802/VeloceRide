package com.rideshare.notification.service;

import com.rideshare.common.enums.NotificationType;
import com.rideshare.common.response.PageResponse;
import com.rideshare.notification.dto.NotificationResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    /** Persists an in-app notification and hands it to every delivery channel after commit. */
    NotificationResponse notify(UUID recipientId, NotificationType type, String title, String message, UUID referenceId);

    PageResponse<NotificationResponse> list(UUID userId, Boolean read, Pageable pageable);

    long unreadCount(UUID userId);

    NotificationResponse markRead(UUID userId, UUID notificationId);

    int markAllRead(UUID userId);

    void delete(UUID userId, UUID notificationId);
}
