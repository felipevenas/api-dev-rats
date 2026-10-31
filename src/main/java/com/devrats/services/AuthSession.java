package com.devrats.services;

import com.devrats.dto.AuthResponse;

public record AuthSession(AuthResponse response, String refreshToken) {
}
