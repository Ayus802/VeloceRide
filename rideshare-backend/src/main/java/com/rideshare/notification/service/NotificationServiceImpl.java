package com.rideshare.notification.service;

import com.rideshare.common.enums.NotificationType;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.AfterCommit;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.notification.dto.NotificationResponse;
import com.rideshare.notification.entity.Notification;
import com.rideshare.notification.repository.NotificationRepository;
import com.rideshare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_TITLE = 150;
    private static final int MAX_MESSAGE = 1000;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;

    @Override
    @Transactional
    public NotificationResponse notify(UUID recipientId, NotificationType type, String title, String message,
                                       UUID referenceId) {
        Notification n = new Notification();
        n.setRecipient(userRepository.getReferenceById(recipientId));
        n.setNotificationType(type);
        n.setTitle(truncate(title, MAX_TITLE));
        n.setMessage(truncate(message, MAX_MESSAGE));
        n.setReferenceId(referenceId);
        notificationRepository.save(n);
        NotificationResponse response = NotificationResponse.from(n);
        AfterCommit.run(() -> channels.forEach(c -> c.deliver(recipientId, response)));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, Boolean read, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, Set.of("createdAt"), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = read == null
                ? notificationRepository.findByRecipientId(userId, page)
                : notificationRepository.findByRecipientIdAndRead(userId, read, page);
        return PageResponse.of(result, NotificationResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification n = loadOwn(userId, notificationId);
        n.setRead(true);
        return NotificationResponse.from(n);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        notificationRepository.delete(loadOwn(userId, notificationId));
    }

    /** 404 (not 403) for other users' notifications, so ids cannot be probed. */
    private Notification loadOwn(UUID userId, UUID notificationId) {
        return notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification"));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
