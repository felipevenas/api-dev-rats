package com.devstreak.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ChallengeResultResponse(
        UUID challengeId,
        UUID winnerUserId,
        String winnerDisplayName,
        String winnerUsername,
        Instant achievedAt,
        LocalDate winningActivityDate,
        int validDaysAtWin,
        String status,
        Instant finalizedAt
) {
}
