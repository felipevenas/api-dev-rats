package com.devrats.dto.chat;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TypingRequest(
        @NotNull UUID conversationId
) {
}
