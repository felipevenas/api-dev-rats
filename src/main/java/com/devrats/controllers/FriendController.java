package com.devrats.controllers;

import com.devrats.dto.FriendRequestResponse;
import com.devrats.dto.FriendResponse;
import com.devrats.dto.FriendshipStatusResponse;
import com.devrats.dto.PageResponse;
import com.devrats.dto.SendFriendRequestRequest;
import com.devrats.enums.FriendRequestDirection;
import com.devrats.services.FriendService;
import com.devrats.utils.AuthenticatedUserHelper;
import com.devrats.utils.PageHelper;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Amizades e solicitações.
 *
 * <p>A ordenação é definida no serviço; por isso o {@link Pageable} daqui não tem {@code Sort}.</p>
 */
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private static final int MAX_PAGE_SIZE = 100;

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<FriendResponse>> listFriends(@AuthenticationPrincipal Jwt jwt,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(PageHelper.toResponse(friendService.listFriends(userId, pageable(page, size))));
    }

    @GetMapping("/requests")
    public ResponseEntity<PageResponse<FriendRequestResponse>> listRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "received") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        FriendRequestDirection parsed = FriendRequestDirection.from(direction);
        return ResponseEntity.ok(PageHelper.toResponse(
                friendService.listRequests(userId, parsed, pageable(page, size))));
    }

    @GetMapping("/status")
    public ResponseEntity<List<FriendshipStatusResponse>> statuses(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("userIds") List<UUID> userIds) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(friendService.statuses(userId, userIds));
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendRequestResponse> sendRequest(@AuthenticationPrincipal Jwt jwt,
                                                             @Valid @RequestBody SendFriendRequestRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(friendService.sendRequest(userId, request));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendRequestResponse> accept(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID requestId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(friendService.acceptRequest(userId, requestId));
    }

    @PostMapping("/requests/{requestId}/decline")
    public ResponseEntity<FriendRequestResponse> decline(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID requestId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(friendService.declineRequest(userId, requestId));
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        friendService.cancelRequest(userId, requestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<Void> removeFriend(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID friendUserId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        friendService.removeFriend(userId, friendUserId);
        return ResponseEntity.noContent().build();
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
