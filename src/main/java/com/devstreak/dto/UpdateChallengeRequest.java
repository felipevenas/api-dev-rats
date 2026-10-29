package com.devstreak.dto;

import com.devstreak.enums.VerificationMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateChallengeRequest(
        VerificationMode verificationMode,
        @Min(1) @Max(365) Integer targetValidDays,
        @Size(max = 64) String timezone,
        Instant startsAt,
        Instant endsAt,
        Instant admissionEndsAt,
        List<UUID> repositoryIds
) {
}
