package com.devrats.services;

import com.devrats.dto.GitHubAuthorizeResponse;
import com.devrats.dto.GitHubRepositoryResponse;
import com.devrats.dto.GitHubStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GitHubIntegrationService {

    GitHubStatusResponse getStatus(UUID userId);

    GitHubAuthorizeResponse getAuthorizeUrl(UUID userId);

    GitHubAuthorizeResponse getInstallUrl(UUID userId);

    /**
     * Finaliza o OAuth e devolve sempre uma URL de redirecionamento do frontend:
     * {@code ?github=connected} em caso de sucesso ou {@code ?github=error&reason=...} em caso de falha.
     */
    String handleCallback(String code, String state, String error);

    /**
     * Finaliza a instalação da App e devolve sempre uma URL de redirecionamento do frontend:
     * {@code ?github=installed} em caso de sucesso ou {@code ?github=error&reason=...} em caso de falha.
     */
    String handleSetup(String installationId, String setupAction, String state, String error);

    void disconnect(UUID userId);

    void syncRepositories(UUID userId);

    Page<GitHubRepositoryResponse> listRepositories(UUID userId, String query, Pageable pageable);
}
