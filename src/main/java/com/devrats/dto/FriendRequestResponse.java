package com.devrats.dto;

import com.devrats.enums.FriendRequestDirection;
import com.devrats.enums.FriendshipStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Solicitação de amizade vista por um dos participantes.
 *
 * <p>O campo {@code user} é o resumo público da contraparte — nunca o perfil
 * completo do titular.</p>
 */
public record FriendRequestResponse(
        UUID id,
        UserSummary user,
        FriendRequestDirection direction,
        FriendshipStatus status,
        Instant createdAt
) {

    public record UserSummary(
            UUID id,
            String username,
            String displayName,
            String avatarUrl
    ) {
    }
}
