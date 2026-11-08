package com.devrats.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudyPhotoResponse(
        UUID id,
        UUID challengeId,
        UUID userId,
        String displayName,
        String username,
        String avatarUrl,
        LocalDate activityDate,
        String title,
        String description,
        String imageUrl,
        Instant imageUrlExpiresAt,
        Integer width,
        Integer height,
        String repositoryFullName,
        String commitUrl,
        Instant createdAt,
        boolean canDelete
) {
}
