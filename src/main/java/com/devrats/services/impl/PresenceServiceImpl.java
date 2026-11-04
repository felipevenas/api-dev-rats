package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.entities.UserPresenceEntity;
import com.devrats.enums.PresenceStatus;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.UserPresenceRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.PresenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PresenceServiceImpl implements PresenceService {

    private final UserPresenceRepository presenceRepository;
    private final UserRepository userRepository;
    private final AppProperties properties;
    private final Clock clock;

    public PresenceServiceImpl(UserPresenceRepository presenceRepository, UserRepository userRepository,
                               AppProperties properties, Clock clock) {
        this.presenceRepository = presenceRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void heartbeat(UUID userId) {
        //---- Um access token recém-emitido pode sobreviver à exclusão da conta; não recriar presença.
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        presenceRepository.upsert(userId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public PresenceStatus statusOf(UUID userId) {
        return presenceRepository.findById(userId)
                .map(UserPresenceEntity::getLastSeenAt)
                .map(this::compute)
                .orElse(PresenceStatus.OFFLINE);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PresenceStatus> statusesOf(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PresenceStatus> statuses = new HashMap<>();
        userIds.forEach(id -> statuses.put(id, PresenceStatus.OFFLINE));
        presenceRepository.findAllByUserIdIn(userIds)
                .forEach(presence -> statuses.put(presence.getUserId(), compute(presence.getLastSeenAt())));
        return statuses;
    }

    /**
     * ONLINE até {@code online-window}, AWAY até {@code away-window}, OFFLINE acima disso.
     * Um heartbeat no futuro (clock skew) é tratado como ONLINE.
     */
    private PresenceStatus compute(Instant lastSeenAt) {
        Duration elapsed = Duration.between(lastSeenAt, clock.instant());
        if (elapsed.isNegative()) {
            return PresenceStatus.ONLINE;
        }
        if (elapsed.compareTo(properties.getPresence().getAwayWindow()) > 0) {
            return PresenceStatus.OFFLINE;
        }
        if (elapsed.compareTo(properties.getPresence().getOnlineWindow()) <= 0) {
            return PresenceStatus.ONLINE;
        }
        return PresenceStatus.AWAY;
    }
}
