package com.devrats.dto.chat;

import java.time.Instant;

public record ChatAttachmentUrlResponse(
        String url,
        Instant expiresAt
) {
}
