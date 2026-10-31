package com.devrats.controllers;

import com.devrats.dto.CreateInviteRequest;
import com.devrats.dto.GroupResponse;
import com.devrats.dto.InvitePreviewResponse;
import com.devrats.dto.InviteResponse;
import com.devrats.services.InviteService;
import com.devrats.utils.AuthenticatedUserHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/groups/{groupId}/invites")
    public ResponseEntity<InviteResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID groupId,
                                                 @Valid @RequestBody CreateInviteRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteService.createInvite(groupId, userId, request));
    }

    @GetMapping("/groups/{groupId}/invites")
    public ResponseEntity<List<InviteResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID groupId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(inviteService.listInvites(groupId, userId));
    }

    @DeleteMapping("/groups/{groupId}/invites/{inviteId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID groupId,
                                       @PathVariable UUID inviteId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        inviteService.revokeInvite(groupId, inviteId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invites/{token}")
    public ResponseEntity<InvitePreviewResponse> preview(@PathVariable String token) {
        return ResponseEntity.ok(inviteService.preview(token));
    }

    @PostMapping("/invites/{token}/accept")
    public ResponseEntity<GroupResponse> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(inviteService.accept(token, userId));
    }
}
