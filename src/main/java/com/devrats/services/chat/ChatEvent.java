package com.devrats.services.chat;

import com.devrats.dto.chat.ChatMessageResponse;
import com.devrats.dto.chat.ChatTypingResponse;
import com.devrats.enums.ChatEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento interno publicado após o commit e entregue pelo WebSocket.
 * {@code recipients} nunca vai para o cliente.
 */
public record ChatEvent(
        String eventId,
        ChatEventType type,
        UUID conversationId,
        List<UUID> recipients,
        ChatMessageResponse message,
        UUID messageId,
        ChatTypingResponse typing,
        String reason,
        Instant occurredAt
) {
}
