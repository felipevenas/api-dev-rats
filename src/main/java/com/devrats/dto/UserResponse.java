package com.devrats.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String displayName,
        String username,
        String email,
        String avatarUrl,
        String timezone,
        boolean notifyDailyGoal,
        boolean notifyMilestones,
        boolean notifyChallengeUpdates,
        boolean notifyIntegration,
        boolean githubConnected,
        String githubUsername,
        Instant createdAt
) {
}
