package com.devrats.controllers;

import com.devrats.services.PresenceService;
import com.devrats.utils.AuthenticatedUserHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Heartbeat de presença. O usuário vem exclusivamente do JWT — não há corpo.
 */
@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        presenceService.heartbeat(userId);
        return ResponseEntity.noContent().build();
    }
}
