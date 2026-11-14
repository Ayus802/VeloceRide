package com.rideshare.notification.dto;

import com.rideshare.common.enums.NotificationType;
import com.rideshare.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        @Schema(example = "New booking request") String title,
        @Schema(example = "Ravi requested 2 seat(s) on Delhi -> Jaipur") String message,
        NotificationType notificationType,
        @Schema(description = "Related booking/ride/payment/review id") UUID referenceId,
        boolean isRead,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.getNotificationType(),
                n.getReferenceId(), n.isRead(), n.getCreatedAt());
    }
}
