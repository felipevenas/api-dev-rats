package com.devrats.controllers;

import com.devrats.dto.DeleteAccountRequest;
import com.devrats.dto.UpdateUserRequest;
import com.devrats.dto.UserDataExportResponse;
import com.devrats.dto.UserResponse;
import com.devrats.services.AvatarService;
import com.devrats.services.UserService;
import com.devrats.utils.AuthenticatedUserHelper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

    public UserController(UserService userService, AvatarService avatarService) {
        this.userService = userService;
        this.avatarService = avatarService;
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

    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam("file") MultipartFile file) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(avatarService.uploadAvatar(userId, file));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<UserResponse> deleteAvatar(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(avatarService.deleteAvatar(userId));
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
