package com.devrats.dto;

import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.VerificationMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChallengeResponse(
        UUID id,
        UUID groupId,
        int sequenceNo,
        VerificationMode verificationMode,
        int targetValidDays,
        String timezone,
        ChallengeStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant admissionEndsAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        boolean allowForks,
        List<ChallengeRepositoryResponse> repositories,
        int participantCount
) {
}
