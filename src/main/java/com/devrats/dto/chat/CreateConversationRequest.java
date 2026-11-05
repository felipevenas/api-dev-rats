package com.devrats.dto.chat;

import com.devrats.enums.ConversationType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
        @NotNull ConversationType type,
        UUID userId,
        UUID groupId
) {
}
