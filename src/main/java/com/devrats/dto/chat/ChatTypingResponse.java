package com.devrats.dto.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatTypingResponse(
        UUID userId,
        String displayName,
        Instant expiresAt
) {
}
