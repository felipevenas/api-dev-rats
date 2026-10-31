package com.devrats.dto;

import com.devrats.enums.GroupRole;
import com.devrats.enums.MemberStatus;

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
