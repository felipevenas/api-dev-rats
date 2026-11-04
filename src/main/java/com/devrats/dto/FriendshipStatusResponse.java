package com.devrats.dto;

import com.devrats.enums.FriendshipStatusView;

import java.util.UUID;

public record FriendshipStatusResponse(UUID userId, FriendshipStatusView status) {
}
