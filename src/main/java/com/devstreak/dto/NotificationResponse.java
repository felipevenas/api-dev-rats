package com.devstreak.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        UUID challengeId,
        String payload,
        Instant readAt,
        Instant createdAt
) {
}
