package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Throttle efêmero de "digitando". Não persiste nada: o evento expira no cliente.
 */
@Service
public class ChatTypingServiceImpl {

    private final Cache<String, Instant> lastTyping;
    private final AppProperties.Chat properties;

    public ChatTypingServiceImpl(AppProperties appProperties) {
        this.properties = appProperties.getChat();
        this.lastTyping = Caffeine.newBuilder()
                .expireAfterWrite(properties.getTypingThrottle())
                .maximumSize(100_000)
                .build();
    }

    public boolean allow(UUID userId, UUID conversationId) {
        return lastTyping.asMap().putIfAbsent(userId + ":" + conversationId, Instant.now()) == null;
    }
}
