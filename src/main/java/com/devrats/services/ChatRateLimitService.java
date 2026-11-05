package com.devrats.services;

import java.util.UUID;

public interface ChatRateLimitService {

    void checkMessage(UUID userId);

    void checkUpload(UUID userId);
}
