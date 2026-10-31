package com.devrats.controllers;

import com.devrats.dto.GitHubAuthorizeResponse;
import com.devrats.dto.GitHubStatusResponse;
import com.devrats.services.GitHubIntegrationService;
import com.devrats.utils.AuthenticatedUserHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/github")
public class GitHubIntegrationController {

    private final GitHubIntegrationService integrationService;

    public GitHubIntegrationController(GitHubIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping
    public ResponseEntity<GitHubStatusResponse> getStatus(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(integrationService.getStatus(userId));
    }

    @GetMapping("/authorize-url")
    public ResponseEntity<GitHubAuthorizeResponse> getAuthorizeUrl(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(integrationService.getAuthorizeUrl(userId));
    }

    @GetMapping("/install-url")
    public ResponseEntity<GitHubAuthorizeResponse> getInstallUrl(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(integrationService.getInstallUrl(userId));
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam String code, @RequestParam String state) {
        return new RedirectView(integrationService.handleCallback(code, state));
    }

    @GetMapping("/setup")
    public RedirectView setup(@RequestParam("installation_id") Long installationId,
                              @RequestParam(value = "setup_action", required = false) String setupAction,
                              @RequestParam(value = "state", required = false) String state) {
        return new RedirectView(integrationService.handleSetup(installationId, setupAction, state));
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> sync(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        integrationService.syncRepositories(userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        integrationService.disconnect(userId);
        return ResponseEntity.noContent().build();
    }
}
