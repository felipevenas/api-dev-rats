package com.devstreak.services.impl;

import com.devstreak.entities.ChallengeEntity;
import com.devstreak.entities.ChallengeParticipantEntity;
import com.devstreak.entities.GroupEntity;
import com.devstreak.entities.GroupMemberEntity;
import com.devstreak.enums.GroupRole;
import com.devstreak.enums.MemberStatus;
import com.devstreak.enums.ParticipantStatus;
import com.devstreak.exceptions.ErrorCode;
import com.devstreak.exceptions.ForbiddenException;
import com.devstreak.exceptions.NotFoundException;
import com.devstreak.repositories.ChallengeParticipantRepository;
import com.devstreak.repositories.ChallengeRepository;
import com.devstreak.repositories.GroupMemberRepository;
import com.devstreak.repositories.GroupRepository;
import com.devstreak.services.GroupAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GroupAccessServiceImpl implements GroupAccessService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;

    public GroupAccessServiceImpl(GroupRepository groupRepository,
                                  GroupMemberRepository memberRepository,
                                  ChallengeRepository challengeRepository,
                                  ChallengeParticipantRepository participantRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    public GroupEntity getGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.GROUP_NOT_FOUND));
    }

    @Override
    public GroupMemberEntity requireMember(UUID groupId, UUID userId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                //---- Non-members receive 404 so group existence is not leaked.
                .orElseThrow(() -> new NotFoundException(ErrorCode.GROUP_NOT_FOUND));
    }

    @Override
    public GroupMemberEntity requireOwner(UUID groupId, UUID userId) {
        GroupMemberEntity member = requireMember(groupId, userId);
        if (member.getRole() != GroupRole.OWNER) {
            throw new ForbiddenException("Somente o criador do grupo pode executar esta ação");
        }
        return member;
    }

    @Override
    public ChallengeEntity getChallenge(UUID challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    @Override
    public ChallengeEntity requireChallengeMember(UUID challengeId, UUID userId) {
        ChallengeEntity challenge = getChallenge(challengeId);
        requireMember(challenge.getGroup().getId(), userId);
        return challenge;
    }

    @Override
    public ChallengeEntity requireChallengeOwner(UUID challengeId, UUID userId) {
        ChallengeEntity challenge = getChallenge(challengeId);
        requireOwner(challenge.getGroup().getId(), userId);
        return challenge;
    }

    @Override
    public ChallengeParticipantEntity requireParticipant(UUID challengeId, UUID userId) {
        requireChallengeMember(challengeId, userId);
        return participantRepository.findByChallengeIdAndUserId(challengeId, userId)
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND));
    }
}
