package com.devstreak.dto;

import com.devstreak.enums.VerificationMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateChallengeRequest(
        @NotNull VerificationMode verificationMode,
        @Min(1) @Max(365) int targetValidDays,
        @NotNull Instant startsAt,
        Instant endsAt,
        Instant admissionEndsAt,
        List<UUID> repositoryIds
) {
}
