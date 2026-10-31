package com.devrats.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ChallengeProgressResponse(
        UUID participantId,
        UUID userId,
        String displayName,
        String username,
        String avatarUrl,
        int validDays,
        int targetValidDays,
        int percent,
        int currentStreak,
        int bestStreak,
        LocalDate lastValidDate,
        Instant completedAt,
        int rank
) {
}
