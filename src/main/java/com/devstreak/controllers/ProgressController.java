package com.devstreak.controllers;

import com.devstreak.dto.ActivityCalendarResponse;
import com.devstreak.dto.ChallengeProgressResponse;
import com.devstreak.dto.ChallengeResultResponse;
import com.devstreak.dto.DashboardResponse;
import com.devstreak.dto.FeedItemResponse;
import com.devstreak.services.ProgressService;
import com.devstreak.utils.AuthenticatedUserHelper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/challenges/{challengeId}/progress")
    public ResponseEntity<List<ChallengeProgressResponse>> progress(@AuthenticationPrincipal Jwt jwt,
                                                                   @PathVariable UUID challengeId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(progressService.listProgress(challengeId, userId));
    }

    @GetMapping("/challenges/{challengeId}/leaderboard")
    public ResponseEntity<List<ChallengeProgressResponse>> leaderboard(@AuthenticationPrincipal Jwt jwt,
                                                                      @PathVariable UUID challengeId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(progressService.listLeaderboard(challengeId, userId));
    }

    @GetMapping("/challenges/{challengeId}/activity")
    public ResponseEntity<ActivityCalendarResponse> activity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID challengeId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID actorId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(progressService.getActivity(challengeId, actorId, userId, from, to));
    }

    @GetMapping("/challenges/{challengeId}/result")
    public ResponseEntity<ChallengeResultResponse> result(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable UUID challengeId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(progressService.getResult(challengeId, userId));
    }

    @GetMapping("/challenges/{challengeId}/feed")
    public ResponseEntity<List<FeedItemResponse>> feed(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID challengeId,
                                                       @RequestParam(defaultValue = "20") int limit) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(progressService.getFeed(challengeId, userId, limit));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(progressService.getDashboard(userId));
    }
}
