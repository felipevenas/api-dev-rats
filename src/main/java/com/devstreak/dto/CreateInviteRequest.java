package com.devstreak.dto;

import jakarta.validation.constraints.Min;

import java.time.Instant;

public record CreateInviteRequest(
        Instant expiresAt,
        @Min(1) Integer maxUses
) {
}
