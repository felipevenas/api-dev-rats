package com.devstreak.controllers;

import com.devstreak.dto.CreateGroupRequest;
import com.devstreak.dto.GroupMemberResponse;
import com.devstreak.dto.GroupResponse;
import com.devstreak.dto.UpdateGroupRequest;
import com.devstreak.services.GroupService;
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
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateGroupRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(groupService.listMyGroups(userId));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> get(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID groupId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(groupService.getGroup(groupId, userId));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<GroupResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID groupId,
                                                @Valid @RequestBody UpdateGroupRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(groupService.updateGroup(groupId, userId, request));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResponse>> listMembers(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable UUID groupId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(groupService.listMembers(groupId, userId));
    }

    @DeleteMapping("/{groupId}/members/{memberUserId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID groupId,
                                             @PathVariable UUID memberUserId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        groupService.removeMember(groupId, userId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        groupService.leaveGroup(groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
