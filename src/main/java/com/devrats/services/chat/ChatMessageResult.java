package com.devrats.services.chat;

import com.devrats.dto.chat.ChatMessageResponse;

public record ChatMessageResult(ChatMessageResponse message, boolean created) {
}
