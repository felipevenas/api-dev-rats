package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.enums.FriendshipStatus;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.UserPresenceRepository;
import com.devrats.services.SocialRetentionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Remove dados sociais que passaram da janela de retenção declarada na política
 * de privacidade: heartbeats antigos e solicitações recusadas/canceladas.
 */
@Service
public class SocialRetentionServiceImpl implements SocialRetentionService {

    private final UserPresenceRepository presenceRepository;
    private final FriendshipRepository friendshipRepository;
    private final AppProperties properties;
    private final Clock clock;

    public SocialRetentionServiceImpl(UserPresenceRepository presenceRepository,
                                      FriendshipRepository friendshipRepository,
                                      AppProperties properties,
                                      Clock clock) {
        this.presenceRepository = presenceRepository;
        this.friendshipRepository = friendshipRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void purgeExpired() {
        Instant now = clock.instant();
        Instant presenceCutoff = now.minus(Duration.ofDays(properties.getRetention().getPresenceDays()));
        Instant timelineCutoff = now.minus(Duration.ofDays(properties.getRetention().getSocialTimelineDays()));
        presenceRepository.deleteByLastSeenAtBefore(presenceCutoff);
        friendshipRepository.deleteStaleRequests(
                List.of(FriendshipStatus.DECLINED, FriendshipStatus.CANCELLED), timelineCutoff);
    }
}
