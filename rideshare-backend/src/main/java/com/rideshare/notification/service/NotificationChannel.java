package com.rideshare.notification.service;

import com.rideshare.notification.dto.NotificationResponse;

import java.util.UUID;

/**
 * Delivery channel for already-persisted notifications. In-app (database) storage is always on;
 * channels add real-time/out-of-band delivery. Add email or browser-push by implementing this interface.
 */
public interface NotificationChannel {

    void deliver(UUID recipientId, NotificationResponse notification);
}
