package com.devrats.dto;

import com.devrats.enums.GroupStatus;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        UUID creatorId,
        String timezone,
        int maxMembers,
        GroupStatus status,
        Instant createdAt,
        ChallengeResponse currentChallenge
) {
}
