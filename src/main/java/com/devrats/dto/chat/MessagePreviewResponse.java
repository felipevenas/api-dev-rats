package com.devrats.dto.chat;

import com.devrats.enums.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

public record MessagePreviewResponse(
        UUID id,
        UUID senderId,
        ChatMessageType type,
        String preview,
        Instant createdAt,
        boolean deleted
) {
}
