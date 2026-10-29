package com.devstreak.dto;

import com.devstreak.enums.GroupRole;
import com.devstreak.enums.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberResponse(
        UUID id,
        UUID userId,
        String displayName,
        String username,
        String avatarUrl,
        GroupRole role,
        MemberStatus status,
        Instant joinedAt
) {
}
