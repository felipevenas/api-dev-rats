package com.devrats.dto.chat;

import com.devrats.enums.ChatMessageType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        ChatUserSummary sender,
        ChatMessageType type,
        String body,
        UUID clientMessageId,
        Instant createdAt,
        boolean deleted,
        List<ChatAttachmentResponse> attachments
) {
}
