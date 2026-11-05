package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.exceptions.RateLimitException;
import com.devrats.services.ChatRateLimitService;
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
 * Limite por usuário para escrita de mensagens e uploads.
 *
 * <p>Igual ao rate limit social, o estado é local à instância. Em múltiplas
 * réplicas esse limite precisa migrar para Redis.</p>
 */
@Service
public class ChatRateLimitServiceImpl implements ChatRateLimitService {

    private static final String MESSAGE_PREFIX = "chat-message:";
    private static final String UPLOAD_PREFIX = "chat-upload:";

    private final AppProperties.Chat properties;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(50_000)
            .build();

    public ChatRateLimitServiceImpl(AppProperties appProperties) {
        this.properties = appProperties.getChat();
    }

    @Override
    public void checkMessage(UUID userId) {
        consume(MESSAGE_PREFIX + userId, properties.getMessagesPerMinute(), Duration.ofMinutes(1));
    }

    @Override
    public void checkUpload(UUID userId) {
        consume(UPLOAD_PREFIX + userId, properties.getUploadsPerMinute(), Duration.ofMinutes(1));
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
