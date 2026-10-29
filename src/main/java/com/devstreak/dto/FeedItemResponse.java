package com.devstreak.dto;

import java.time.Instant;
import java.util.UUID;

public record FeedItemResponse(
        UUID id,
        String type,
        String message,
        Instant createdAt
) {
}
