package com.rideshare.notification.service;

import com.rideshare.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pushes notifications to the recipient's private STOMP queue {@code /user/queue/notifications}.
 * Only the authenticated session of that user receives it (user destinations are resolved per principal).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationChannel implements NotificationChannel {

    public static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void deliver(UUID recipientId, NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSendToUser(recipientId.toString(), DESTINATION, notification);
        } catch (RuntimeException e) {
            // Real-time delivery is best effort; the notification is already stored.
            log.warn("WebSocket delivery failed for user {}: {}", recipientId, e.getMessage());
        }
    }
}
