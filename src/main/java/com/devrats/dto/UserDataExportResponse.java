package com.devrats.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDataExportResponse(
        UUID userId,
        String displayName,
        String username,
        String email,
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
