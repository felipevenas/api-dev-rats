package com.devrats.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        List<DashboardGroup> groups,
        GitHubStatusResponse github
) {

    public record DashboardGroup(
            UUID groupId,
            String groupName,
            UUID challengeId,
            String challengeStatus,
            int targetValidDays,
            int validDays,
            int percent,
            int currentStreak,
            int bestStreak,
            int rank,
            LocalDate lastValidDate,
            Instant startsAt,
            Instant endsAt
    ) {
    }
}
