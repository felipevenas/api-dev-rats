package com.devrats.dto.chat;

import com.devrats.enums.ConversationType;
import com.devrats.enums.PresenceStatus;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        String title,
        String avatarUrl,
        UUID groupId,
        ChatUserSummary peer,
        PresenceStatus presence,
        MessagePreviewResponse lastMessage,
        long unreadCount,
        Instant lastMessageAt,
        Instant createdAt
) {
}
