package com.devstreak.services;

import com.devstreak.dto.LoginRequest;
import com.devstreak.dto.PasswordResetResponse;
import com.devstreak.dto.RegisterRequest;

public interface AuthService {

    AuthSession register(RegisterRequest request);

    AuthSession login(LoginRequest request);

    AuthSession refresh(String refreshToken, String userAgent);

    void logout(String refreshToken);

    PasswordResetResponse requestPasswordReset(String email);

    void confirmPasswordReset(String token, String newPassword);
}
