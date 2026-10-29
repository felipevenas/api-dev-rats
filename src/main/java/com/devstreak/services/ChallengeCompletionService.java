package com.devstreak.services;

import java.util.UUID;

public interface ChallengeCompletionService {

    void checkAndComplete(UUID challengeId);
}
