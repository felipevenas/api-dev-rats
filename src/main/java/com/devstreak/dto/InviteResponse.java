package com.devstreak.dto;

import java.time.Instant;
import java.util.UUID;

public record InviteResponse(
        UUID id,
        String token,
        String url,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Integer maxUses,
        int uses
) {
}
