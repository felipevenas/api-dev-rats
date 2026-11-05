package com.devrats.config.websocket;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * Principal do STOMP. O nome é o UUID do usuário para que
 * {@code convertAndSendToUser} encontre todas as sessões daquele usuário.
 */
public record ChatPrincipal(UUID userId, Instant expiresAt) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
