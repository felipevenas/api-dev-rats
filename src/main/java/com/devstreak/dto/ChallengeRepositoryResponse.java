package com.devstreak.dto;

import java.util.UUID;

public record ChallengeRepositoryResponse(
        UUID id,
        UUID githubRepositoryId,
        Long externalRepositoryId,
        String fullName,
        String ownerLogin,
        int slot
) {
}
