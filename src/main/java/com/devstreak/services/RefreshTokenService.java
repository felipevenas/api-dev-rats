package com.devstreak.services;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {

    record Rotation(UUID userId, String refreshToken) {
    }

    String issue(UUID userId, String userAgent);

    Optional<Rotation> rotate(String rawToken, String userAgent);

    void revokeSession(String rawToken);

    void revokeAllForUser(UUID userId);
}
