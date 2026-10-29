package com.devstreak.services;

import com.devstreak.dto.UpdateUserRequest;
import com.devstreak.dto.UserResponse;
import com.devstreak.dto.UserDataExportResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getMe(UUID userId);

    UserResponse updateMe(UUID userId, UpdateUserRequest request);

    UserDataExportResponse exportData(UUID userId);

    void deleteMe(UUID userId, String password);
}
