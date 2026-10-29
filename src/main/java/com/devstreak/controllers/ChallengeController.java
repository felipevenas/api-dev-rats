package com.devstreak.controllers;

import com.devstreak.dto.AddRepositoryRequest;
import com.devstreak.dto.ChallengeResponse;
import com.devstreak.dto.CreateChallengeRequest;
import com.devstreak.dto.UpdateChallengeRequest;
import com.devstreak.services.ChallengeService;
import com.devstreak.utils.AuthenticatedUserHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping("/groups/{groupId}/challenges")
    public ResponseEntity<ChallengeResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID groupId,
                                                    @Valid @RequestBody CreateChallengeRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(challengeService.createDraft(groupId, userId, request));
    }

    @GetMapping("/groups/{groupId}/challenges")
    public ResponseEntity<List<ChallengeResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID groupId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(challengeService.listChallenges(groupId, userId));
    }

    @GetMapping("/challenges/{challengeId}")
    public ResponseEntity<ChallengeResponse> get(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID challengeId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(challengeService.getChallenge(challengeId, userId));
    }

    @PatchMapping("/challenges/{challengeId}")
    public ResponseEntity<ChallengeResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID challengeId,
                                                    @Valid @RequestBody UpdateChallengeRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(challengeService.updateChallenge(challengeId, userId, request));
    }

    @PostMapping("/challenges/{challengeId}/repositories")
    public ResponseEntity<ChallengeResponse> addRepository(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID challengeId,
                                                           @Valid @RequestBody AddRepositoryRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(challengeService.addRepository(challengeId, userId, request));
    }

    @DeleteMapping("/challenges/{challengeId}/repositories/{challengeRepositoryId}")
    public ResponseEntity<Void> removeRepository(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID challengeId,
                                                 @PathVariable UUID challengeRepositoryId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        challengeService.removeRepository(challengeId, userId, challengeRepositoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/challenges/{challengeId}/start")
    public ResponseEntity<ChallengeResponse> start(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID challengeId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(challengeService.startChallenge(challengeId, userId));
    }

    @PostMapping("/challenges/{challengeId}/cancel")
    public ResponseEntity<ChallengeResponse> cancel(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID challengeId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(challengeService.cancelChallenge(challengeId, userId));
    }
}
