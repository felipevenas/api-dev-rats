package com.devrats.dto.chat;

import java.util.UUID;

public record ChatUserSummary(
        UUID id,
        String username,
        String displayName,
        String avatarUrl
) {
}
