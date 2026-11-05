package com.devrats.dto.chat;

import com.devrats.enums.ChatMessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendTextMessageRequest(
        @NotNull UUID clientMessageId,
        @NotNull ChatMessageType type,
        @Size(max = 2000) String body
) {
}
