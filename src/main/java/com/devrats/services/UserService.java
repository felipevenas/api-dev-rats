package com.devrats.services;

import com.devrats.dto.UpdateUserRequest;
import com.devrats.dto.UserResponse;
import com.devrats.dto.UserDataExportResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getMe(UUID userId);

    UserResponse updateMe(UUID userId, UpdateUserRequest request);

    UserDataExportResponse exportData(UUID userId);

    void deleteMe(UUID userId, String password);
}
