package com.devrats.dto;

import com.devrats.enums.PresenceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Amigo na listagem privada de amigos. Presença só existe porque o vínculo é ACEITO.
 */
public record FriendResponse(
        UUID friendshipId,
        UUID userId,
        String username,
        String displayName,
        String avatarUrl,
        PresenceStatus presence,
        Instant friendsSince
) {
}
