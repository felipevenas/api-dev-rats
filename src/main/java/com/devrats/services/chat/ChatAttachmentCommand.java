package com.devrats.services.chat;

import com.devrats.dto.chat.ChatMessageResponse;
import com.devrats.enums.ChatMessageType;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record ChatAttachmentCommand(
        UUID clientMessageId,
        ChatMessageType type,
        String body,
        Integer durationSeconds,
        MultipartFile file
) {
}
