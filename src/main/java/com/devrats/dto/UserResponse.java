package com.devrats.dto;

import com.devrats.enums.ExperienceLevel;
import com.devrats.enums.TechStack;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String displayName,
        String username,
        String email,
        String avatarUrl,
        String bio,
        String location,
        String websiteUrl,
        ExperienceLevel experienceLevel,
        List<TechStack> primaryStack,
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
