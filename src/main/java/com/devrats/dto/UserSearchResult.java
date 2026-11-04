package com.devrats.dto;

import com.devrats.enums.ExperienceLevel;
import com.devrats.enums.FriendshipStatusView;
import com.devrats.enums.TechStack;

import java.util.List;
import java.util.UUID;

/**
 * Resultado da busca por username. Sem e-mail, sem status de conta e sem presença.
 */
public record UserSearchResult(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        ExperienceLevel experienceLevel,
        List<TechStack> primaryStack,
        FriendshipStatusView friendshipStatus
) {
}
