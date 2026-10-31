package com.devrats.services;

import com.devrats.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AvatarService {

    UserResponse uploadAvatar(UUID userId, MultipartFile file);

    UserResponse deleteAvatar(UUID userId);
}
