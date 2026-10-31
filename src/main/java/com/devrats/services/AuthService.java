package com.devrats.services;

import com.devrats.dto.LoginRequest;
import com.devrats.dto.PasswordResetResponse;
import com.devrats.dto.RegisterRequest;

public interface AuthService {

    AuthSession register(RegisterRequest request);

    AuthSession login(LoginRequest request);

    AuthSession refresh(String refreshToken, String userAgent);

    void logout(String refreshToken);

    PasswordResetResponse requestPasswordReset(String email);

    void confirmPasswordReset(String token, String newPassword);
}
