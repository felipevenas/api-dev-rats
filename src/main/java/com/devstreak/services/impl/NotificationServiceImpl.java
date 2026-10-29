package com.devstreak.services.impl;

import com.devstreak.dto.NotificationResponse;
import com.devstreak.entities.NotificationEntity;
import com.devstreak.entities.UserEntity;
import com.devstreak.enums.NotificationType;
import com.devstreak.repositories.NotificationRepository;
import com.devstreak.repositories.UserRepository;
import com.devstreak.services.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper,
                                   Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void notify(UUID userId, NotificationType type, UUID challengeId, String dedupeKey, Map<String, Object> payload) {
        if (notificationRepository.findByUserIdAndDedupeKey(userId, dedupeKey).isPresent()) {
            return;
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || !wants(user, type)) {
            return;
        }
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(user);
        notification.setType(type);
        notification.setChallengeId(challengeId);
        notification.setDedupeKey(dedupeKey);
        notification.setPayload(payload == null || payload.isEmpty() ? null : objectMapper.writeValueAsString(payload));
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID userId, Pageable pageable) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        NotificationEntity notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new com.devstreak.exceptions.NotFoundException(
                        com.devstreak.exceptions.ErrorCode.RESOURCE_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.findAllByUserIdAndReadAtIsNull(userId).forEach(notification -> {
            notification.setReadAt(clock.instant());
            notificationRepository.save(notification);
        });
    }

    private boolean wants(UserEntity user, NotificationType type) {
        return switch (type) {
            case DAILY_GOAL -> user.isNotifyDailyGoal();
            case MILESTONE -> user.isNotifyMilestones();
            case CHALLENGE_COMPLETED, CHALLENGE_CANCELLED, CHALLENGE_EXPIRED, MEMBER_JOINED, INVITE_ACCEPTED ->
                    user.isNotifyChallengeUpdates();
            case INTEGRATION_ATTENTION -> user.isNotifyIntegration();
        };
    }

    private NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(notification.getId(), notification.getType().name(),
                notification.getChallengeId(), notification.getPayload(), notification.getReadAt(),
                notification.getCreatedAt());
    }
}
