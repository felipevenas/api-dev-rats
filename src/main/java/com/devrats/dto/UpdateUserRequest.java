package com.devrats.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 2, max = 120) String displayName,
        @Size(max = 64) String timezone,
        Boolean notifyDailyGoal,
        Boolean notifyMilestones,
        Boolean notifyChallengeUpdates,
        Boolean notifyIntegration
) {
}
