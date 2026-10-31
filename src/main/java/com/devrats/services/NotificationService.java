package com.devrats.services;

import com.devrats.dto.NotificationResponse;
import com.devrats.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    void notify(UUID userId, NotificationType type, UUID challengeId, String dedupeKey, Map<String, Object> payload);

    Page<NotificationResponse> list(UUID userId, Pageable pageable);

    void markRead(UUID userId, UUID notificationId);

    void markAllRead(UUID userId);
}
