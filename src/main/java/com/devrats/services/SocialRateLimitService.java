package com.devrats.services;

import java.util.UUID;

/**
 * Limite por usuário (além do limite por IP do {@code RateLimitFilter}).
 *
 * <p>O IP pode ser compartilhado (NAT, campus, empresa); o limite por identidade
 * protege a busca e o envio de solicitações contra abuso autenticado.</p>
 */
public interface SocialRateLimitService {

    void checkSearch(UUID userId);

    void checkProfile(UUID userId);

    void checkFriendRequest(UUID userId);
}
