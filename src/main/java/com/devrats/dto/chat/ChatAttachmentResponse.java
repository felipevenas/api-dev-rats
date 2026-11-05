package com.devrats.dto.chat;

import com.devrats.enums.AttachmentKind;

import java.time.Instant;
import java.util.UUID;

public record ChatAttachmentResponse(
        UUID id,
        AttachmentKind kind,
        String fileName,
        String contentType,
        long sizeBytes,
        Integer width,
        Integer height,
        Integer durationSeconds,
        String url,
        Instant urlExpiresAt
) {
}
