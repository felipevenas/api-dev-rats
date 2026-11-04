package com.devrats.services;

import com.devrats.dto.PublicUserResponse;
import com.devrats.dto.UserSearchResult;

import java.util.List;
import java.util.UUID;

/**
 * Diretório público de usuários: busca por username e perfil público.
 */
public interface UserDirectoryService {

    /**
     * Busca por prefixo de username (somente {@code ACTIVE}, sem o próprio usuário).
     *
     * @param limit entre 1 e 10
     */
    List<UserSearchResult> search(UUID currentUserId, String query, int limit);

    /**
     * Perfil público por username. Username inexistente ou conta excluída
     * produzem o mesmo 404.
     */
    PublicUserResponse getPublicProfile(UUID currentUserId, String username);
}
