package com.devstreak.services;

import com.devstreak.entities.ChallengeEntity;
import com.devstreak.entities.ChallengeParticipantEntity;
import com.devstreak.entities.GroupEntity;
import com.devstreak.entities.GroupMemberEntity;

import java.util.UUID;

public interface GroupAccessService {

    GroupEntity getGroup(UUID groupId);

    GroupMemberEntity requireMember(UUID groupId, UUID userId);

    GroupMemberEntity requireOwner(UUID groupId, UUID userId);

    ChallengeEntity getChallenge(UUID challengeId);

    ChallengeEntity requireChallengeMember(UUID challengeId, UUID userId);

    ChallengeEntity requireChallengeOwner(UUID challengeId, UUID userId);

    ChallengeParticipantEntity requireParticipant(UUID challengeId, UUID userId);
}
