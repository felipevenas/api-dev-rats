package com.devstreak.services;

import com.devstreak.dto.AddRepositoryRequest;
import com.devstreak.dto.ChallengeResponse;
import com.devstreak.dto.CreateChallengeRequest;
import com.devstreak.dto.UpdateChallengeRequest;

import java.util.List;
import java.util.UUID;

public interface ChallengeService {

    ChallengeResponse createDraft(UUID groupId, UUID actorId, CreateChallengeRequest request);

    ChallengeResponse getChallenge(UUID challengeId, UUID userId);

    List<ChallengeResponse> listChallenges(UUID groupId, UUID userId);

    ChallengeResponse updateChallenge(UUID challengeId, UUID actorId, UpdateChallengeRequest request);

    ChallengeResponse addRepository(UUID challengeId, UUID actorId, AddRepositoryRequest request);

    void removeRepository(UUID challengeId, UUID actorId, UUID challengeRepositoryId);

    ChallengeResponse startChallenge(UUID challengeId, UUID actorId);

    ChallengeResponse cancelChallenge(UUID challengeId, UUID actorId);

    void activateScheduledChallenges();

    void expireChallenges();
}
