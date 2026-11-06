package com.devrats.services.email;

public record PasswordResetEmail(
        java.util.UUID userId,
        String to,
        String displayName,
        String resetUrl,
        long expiresInMinutes,
        String idempotencyKey
) {
}
