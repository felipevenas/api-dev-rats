package com.devrats.services.email;

import java.util.UUID;

public record PasswordResetRequestedEvent(
        UUID userId,
        UUID tokenId,
        String recipient,
        String displayName,
        String resetUrl,
        long expiresInMinutes
) {
}
