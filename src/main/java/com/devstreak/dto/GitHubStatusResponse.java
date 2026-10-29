package com.devstreak.dto;

import java.time.Instant;
import java.util.List;

public record GitHubStatusResponse(
        boolean appConfigured,
        boolean connected,
        String githubUsername,
        Long githubUserId,
        int installationCount,
        int repositoryCount,
        List<String> permissions,
        Instant connectedAt,
        Instant lastSyncedAt,
        String message
) {
}
