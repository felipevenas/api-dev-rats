package com.devrats.dto;

import com.devrats.enums.ExperienceLevel;
import com.devrats.enums.TechStack;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDataExportResponse(
        UUID userId,
        String displayName,
        String username,
        String email,
        String avatarUrl,
        String avatarSource,
        String bio,
        String location,
        String websiteUrl,
        ExperienceLevel experienceLevel,
        List<TechStack> primaryStack,
        String timezone,
        Instant createdAt,
        List<GroupMembership> groups
) {

    public record GroupMembership(
            UUID groupId,
            String groupName,
            String role,
            String membershipStatus,
            Instant joinedAt
    ) {
    }
}
