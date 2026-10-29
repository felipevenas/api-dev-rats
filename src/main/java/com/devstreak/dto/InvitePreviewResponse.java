package com.devstreak.dto;

import java.time.Instant;
import java.util.UUID;

public record InvitePreviewResponse(
        UUID groupId,
        String groupName,
        String inviterName,
        Instant expiresAt,
        boolean valid,
        String reason
) {
}
