package com.devrats.dto.chat;

import com.devrats.enums.ChatEventType;

import java.time.Instant;
import java.util.UUID;

public record ChatEventResponse(
        String eventId,
        ChatEventType type,
        UUID conversationId,
        ChatMessageResponse message,
        UUID messageId,
        ChatTypingResponse typing,
        String reason,
        Instant occurredAt
) {
}
