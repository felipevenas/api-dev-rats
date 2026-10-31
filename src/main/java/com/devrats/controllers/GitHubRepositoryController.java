package com.devrats.controllers;

import com.devrats.dto.GitHubRepositoryResponse;
import com.devrats.dto.PageResponse;
import com.devrats.services.GitHubIntegrationService;
import com.devrats.utils.AuthenticatedUserHelper;
import com.devrats.utils.PageHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/github/repositories")
public class GitHubRepositoryController {

    private final GitHubIntegrationService integrationService;

    public GitHubRepositoryController(GitHubIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<GitHubRepositoryResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("fullName").ascending());
        return ResponseEntity.ok(PageHelper.toResponse(integrationService.listRepositories(userId, query, pageable)));
    }
}
