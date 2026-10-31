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

    String handleCallback(String code, String state);

    String handleSetup(Long installationId, String setupAction, String state);

    void disconnect(UUID userId);

    void syncRepositories(UUID userId);

    Page<GitHubRepositoryResponse> listRepositories(UUID userId, String query, Pageable pageable);
}
