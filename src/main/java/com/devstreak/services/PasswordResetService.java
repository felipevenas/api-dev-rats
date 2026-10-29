package com.devstreak.services;

import com.devstreak.dto.PasswordResetResponse;

public interface PasswordResetService {

    PasswordResetResponse requestReset(String email);

    void confirmReset(String token, String newPassword);
}
