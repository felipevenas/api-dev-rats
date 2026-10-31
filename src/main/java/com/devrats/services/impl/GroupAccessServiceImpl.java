package com.devrats.services.impl;

import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.enums.GroupRole;
import com.devrats.enums.MemberStatus;
import com.devrats.enums.ParticipantStatus;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.ForbiddenException;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.GroupRepository;
import com.devrats.services.GroupAccessService;
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
