package com.devrats.dto;

import com.devrats.enums.ExperienceLevel;
import com.devrats.enums.FriendshipStatusView;
import com.devrats.enums.PresenceStatus;
import com.devrats.enums.TechStack;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Perfil público de um usuário.
 *
 * <p>Deliberadamente fechado: nunca carrega e-mail, fuso, preferências de
 * notificação, status, tokens, {@code githubUsername} nem {@code last_seen_at}.</p>
 */
public record PublicUserResponse(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        String location,
        String websiteUrl,
        ExperienceLevel experienceLevel,
        List<TechStack> primaryStack,
        Instant createdAt,
        FriendshipStatusView friendshipStatus,
        PresenceStatus presence,
        long friendsCount
) {
}
