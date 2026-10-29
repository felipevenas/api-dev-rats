package com.devstreak.controllers;

import com.devstreak.dto.DeleteAccountRequest;
import com.devstreak.dto.UpdateUserRequest;
import com.devstreak.dto.UserDataExportResponse;
import com.devstreak.dto.UserResponse;
import com.devstreak.services.UserService;
import com.devstreak.utils.AuthenticatedUserHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @PatchMapping
    public ResponseEntity<UserResponse> updateMe(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody UpdateUserRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(userService.updateMe(userId, request));
    }

    @GetMapping("/export")
    public ResponseEntity<UserDataExportResponse> exportData(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(userService.exportData(userId));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody DeleteAccountRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        userService.deleteMe(userId, request.password());
        return ResponseEntity.noContent().build();
    }
}
