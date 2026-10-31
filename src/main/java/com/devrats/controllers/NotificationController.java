package com.devrats.controllers;

import com.devrats.dto.NotificationResponse;
import com.devrats.dto.PageResponse;
import com.devrats.services.NotificationService;
import com.devrats.utils.AuthenticatedUserHelper;
import com.devrats.utils.PageHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(PageHelper.toResponse(notificationService.list(userId, pageable)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID notificationId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        notificationService.markRead(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
