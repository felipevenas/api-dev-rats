package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.entities.UserEntity;
import com.devrats.entities.UserPresenceEntity;
import com.devrats.enums.PresenceStatus;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.UserPresenceRepository;
import com.devrats.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID ONLINE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AWAY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OFFLINE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID NEVER_SEEN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private UserPresenceRepository presenceRepository;

    @Mock
    private UserRepository userRepository;

    private PresenceServiceImpl service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getPresence().setOnlineWindow(Duration.ofMinutes(2));
        properties.getPresence().setAwayWindow(Duration.ofMinutes(10));
        service = new PresenceServiceImpl(presenceRepository, userRepository, properties, CLOCK);
    }

    @Test
    void shouldComputePresenceStatuses_withFixedClock() {
        when(presenceRepository.findAllByUserIdIn(List.of(ONLINE_ID, AWAY_ID, OFFLINE_ID, NEVER_SEEN_ID)))
                .thenReturn(List.of(
                        presence(ONLINE_ID, NOW.minusSeconds(60)),
                        presence(AWAY_ID, NOW.minus(Duration.ofMinutes(5))),
                        presence(OFFLINE_ID, NOW.minus(Duration.ofMinutes(11)))
                ));

        Map<UUID, PresenceStatus> statuses = service.statusesOf(
                List.of(ONLINE_ID, AWAY_ID, OFFLINE_ID, NEVER_SEEN_ID));

        assertThat(statuses)
                .containsEntry(ONLINE_ID, PresenceStatus.ONLINE)
                .containsEntry(AWAY_ID, PresenceStatus.AWAY)
                .containsEntry(OFFLINE_ID, PresenceStatus.OFFLINE)
                .containsEntry(NEVER_SEEN_ID, PresenceStatus.OFFLINE);
    }

    @Test
    void shouldTreatExactOnlineWindowAsOnline() {
        when(presenceRepository.findById(ONLINE_ID))
                .thenReturn(Optional.of(presence(ONLINE_ID, NOW.minus(Duration.ofMinutes(2)))));

        assertThat(service.statusOf(ONLINE_ID)).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void shouldReturnOfflineWhenThereIsNoHeartbeat() {
        when(presenceRepository.findById(NEVER_SEEN_ID)).thenReturn(Optional.empty());

        assertThat(service.statusOf(NEVER_SEEN_ID)).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    void shouldUpsertHeartbeatWithCurrentInstant() {
        when(userRepository.findById(ONLINE_ID)).thenReturn(Optional.of(activeUser(ONLINE_ID)));

        service.heartbeat(ONLINE_ID);

        verify(presenceRepository).upsert(ONLINE_ID, NOW);
    }

    @Test
    void shouldRejectHeartbeat_whenUserIsNotActive() {
        UserEntity deleted = activeUser(ONLINE_ID);
        deleted.setStatus(UserStatus.DELETED);
        when(userRepository.findById(ONLINE_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.heartbeat(ONLINE_ID))
                .isInstanceOf(NotFoundException.class);
        verify(presenceRepository, never()).upsert(ONLINE_ID, NOW);
    }

    private static UserEntity activeUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static UserPresenceEntity presence(UUID userId, Instant lastSeenAt) {
        UserPresenceEntity entity = new UserPresenceEntity();
        entity.setUserId(userId);
        entity.setLastSeenAt(lastSeenAt);
        return entity;
    }
}
