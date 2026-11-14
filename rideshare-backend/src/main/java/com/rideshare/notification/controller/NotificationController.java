package com.rideshare.notification.controller;

import com.rideshare.common.response.ApiResponse;
import com.rideshare.common.response.PageResponse;
import com.rideshare.notification.dto.NotificationResponse;
import com.rideshare.notification.dto.UnreadCountResponse;
import com.rideshare.notification.service.NotificationService;
import com.rideshare.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "8. Notifications", description = "In-app notifications generated from booking, ride, payment and review "
        + "events. Also pushed live on STOMP /user/queue/notifications.")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List my notifications", description = "Newest first. Optional filter read=true|false.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by read status") @RequestParam(required = false) Boolean read,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved",
                notificationService.list(principal.id(), read, pageable)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                new UnreadCountResponse(notificationService.unreadCount(principal.id()))));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark one notification as read", description = "404 if it is not yours.")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@AuthenticationPrincipal UserPrincipal principal,
                                                                      @PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read",
                notificationService.markRead(principal.id(), notificationId)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        int updated = notificationService.markAllRead(principal.id());
        return ResponseEntity.ok(ApiResponse.success(updated + " notification(s) marked as read",
                new UnreadCountResponse(0)));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete one of my notifications", description = "404 if it is not yours.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID notificationId) {
        notificationService.delete(principal.id(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
