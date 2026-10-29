package com.devstreak.services;

import com.devstreak.dto.AuthResponse;

public record AuthSession(AuthResponse response, String refreshToken) {
}
