package com.devrats.services;

import com.devrats.dto.PasswordResetResponse;

public interface PasswordResetService {

    PasswordResetResponse requestReset(String email);

    void confirmReset(String token, String newPassword);
}
