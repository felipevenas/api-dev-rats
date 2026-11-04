package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.exceptions.RateLimitException;
import com.devrats.services.SocialRateLimitService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Limite por usuário autenticado para ações sociais caras.
 *
 * <p>Sem estado compartilhado entre réplicas — igual ao rate limit por IP atual.
 * O {@code Clock} não é usado aqui: o bucket4j mede tempo monotônico.</p>
 */
@Service
public class SocialRateLimitServiceImpl implements SocialRateLimitService {

    private static final String SEARCH_PREFIX = "search:";
    private static final String PROFILE_PREFIX = "profile:";
    private static final String FRIEND_REQUEST_PREFIX = "friend-request:";

    private final AppProperties properties;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(50_000)
            .build();

    public SocialRateLimitServiceImpl(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void checkSearch(UUID userId) {
        consume(SEARCH_PREFIX + userId,
                properties.getSocial().getSearchPerMinute(), Duration.ofMinutes(1));
    }

    @Override
    public void checkProfile(UUID userId) {
        consume(PROFILE_PREFIX + userId,
                properties.getSocial().getProfilePerMinute(), Duration.ofMinutes(1));
    }

    @Override
    public void checkFriendRequest(UUID userId) {
        consume(FRIEND_REQUEST_PREFIX + userId,
                properties.getSocial().getFriendRequestPerHour(), Duration.ofHours(1));
    }

    private void consume(String key, int capacity, Duration window) {
        Bucket bucket = buckets.get(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, window).build())
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            throw new RateLimitException(Duration.ofSeconds(seconds));
        }
    }
}
