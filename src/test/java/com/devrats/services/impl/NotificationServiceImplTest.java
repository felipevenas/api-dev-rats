package com.devrats.services.impl;

import com.devrats.entities.NotificationEntity;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.NotificationRepository;
import com.devrats.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(notificationRepository,
            userRepository, objectMapper, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void shouldDeleteNotification_whenOwnedByUser() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationEntity notification = new NotificationEntity();
        notification.setId(notificationId);
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));

        service.delete(userId, notificationId);

        verify(notificationRepository).delete(notification);
    }

    @Test
    void shouldThrowNotFound_whenNotificationBelongsToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, notificationId))
                .isInstanceOf(NotFoundException.class);
        verify(notificationRepository, never()).delete(any());
    }
}
