package com.devrats.dto;

import java.util.UUID;

public record GitHubRepositoryResponse(
        UUID id,
        Long githubRepositoryId,
        String fullName,
        String ownerLogin,
        String visibility,
        boolean privateRepository,
        boolean fork,
        String accessStatus
) {
}
