package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.enums.FriendshipStatus;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.UserPresenceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SocialRetentionServiceImplTest {

    private final UserPresenceRepository presenceRepository = mock(UserPresenceRepository.class);
    private final FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
    private final AppProperties properties = new AppProperties();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final SocialRetentionServiceImpl service = new SocialRetentionServiceImpl(
            presenceRepository, friendshipRepository, properties, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void shouldPurgePresenceAndStaleRequestsUsingConfiguredWindows() {
        properties.getRetention().setPresenceDays(30);
        properties.getRetention().setSocialTimelineDays(180);

        service.purgeExpired();

        verify(presenceRepository).deleteByLastSeenAtBefore(now.minus(Duration.ofDays(30)));
        verify(friendshipRepository).deleteStaleRequests(
                List.of(FriendshipStatus.DECLINED, FriendshipStatus.CANCELLED),
                now.minus(Duration.ofDays(180)));
    }
}
