package com.devrats.services;

import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupMemberEntity;

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
