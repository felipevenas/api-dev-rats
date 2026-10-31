package com.devrats.services;

import java.util.UUID;

public interface ChallengeCompletionService {

    void checkAndComplete(UUID challengeId);
}
