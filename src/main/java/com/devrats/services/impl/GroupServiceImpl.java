package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.ChallengeResponse;
import com.devrats.dto.CreateGroupRequest;
import com.devrats.dto.GroupMemberResponse;
import com.devrats.dto.GroupResponse;
import com.devrats.dto.UpdateGroupRequest;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.GroupRole;
import com.devrats.enums.GroupStatus;
import com.devrats.enums.MemberStatus;
import com.devrats.enums.ParticipantStatus;
import com.devrats.exceptions.ForbiddenException;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.GroupRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.ChallengeService;
import com.devrats.services.GroupAccessService;
import com.devrats.services.GroupService;
import com.devrats.utils.ActivityDateHelper;
import com.devrats.utils.GroupViewHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ChallengeService challengeService;
    private final GroupAccessService accessService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;

    public GroupServiceImpl(GroupRepository groupRepository,
                            GroupMemberRepository memberRepository,
                            ChallengeRepository challengeRepository,
                            ChallengeParticipantRepository participantRepository,
                            UserRepository userRepository,
                            ChallengeService challengeService,
                            GroupAccessService accessService,
                            AuditService auditService,
                            AppProperties properties,
                            Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.challengeService = challengeService;
        this.accessService = accessService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GroupResponse createGroup(UUID creatorId, CreateGroupRequest request) {
        ActivityDateHelper.parseZone(request.timezone());
        UserEntity creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        int maxMembers = request.maxMembers() == null ? properties.getGroups().getMaxMembers() : request.maxMembers();

        GroupEntity group = new GroupEntity();
        group.setName(request.name().trim());
        group.setDescription(request.description());
        group.setCreator(creator);
        group.setTimezone(request.timezone());
        group.setMaxMembers(maxMembers);
        group.setStatus(GroupStatus.ACTIVE);
        groupRepository.save(group);

        GroupMemberEntity owner = new GroupMemberEntity();
        owner.setGroup(group);
        owner.setUser(creator);
        owner.setRole(GroupRole.OWNER);
        owner.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(owner);

        ChallengeResponse challenge = challengeService.createDraft(group.getId(), creatorId, request.challenge());
        auditService.record(creatorId, "GROUP", group.getId(), AuditAction.GROUP_CREATED,
                Map.of("name", group.getName()));
        return GroupViewHelper.toResponse(group, challenge);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> listMyGroups(UUID userId) {
        return groupRepository.findAllByMemberUserId(userId).stream()
                .map(group -> GroupViewHelper.toResponse(group, currentChallenge(group.getId(), userId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID groupId, UUID userId) {
        GroupEntity group = accessService.requireMember(groupId, userId).getGroup();
        return GroupViewHelper.toResponse(group, currentChallenge(groupId, userId));
    }

    @Override
    @Transactional
    public GroupResponse updateGroup(UUID groupId, UUID userId, UpdateGroupRequest request) {
        GroupMemberEntity owner = accessService.requireOwner(groupId, userId);
        GroupEntity group = owner.getGroup();
        if (request.name() != null) {
            group.setName(request.name().trim());
        }
        if (request.description() != null) {
            group.setDescription(request.description());
        }
        groupRepository.save(group);
        auditService.record(userId, "GROUP", groupId, AuditAction.GROUP_UPDATED, null);
        return GroupViewHelper.toResponse(group, currentChallenge(groupId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMemberResponse> listMembers(UUID groupId, UUID userId) {
        accessService.requireMember(groupId, userId);
        return memberRepository.findAllByGroupIdAndStatusOrderByJoinedAtAsc(groupId, MemberStatus.ACTIVE).stream()
                .map(GroupViewHelper::toMemberResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID actorId, UUID memberUserId) {
        accessService.requireOwner(groupId, actorId);
        GroupMemberEntity target = memberRepository.findByGroupIdAndUserId(groupId, memberUserId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        if (target.getRole() == GroupRole.OWNER) {
            throw new ForbiddenException("O criador do grupo não pode ser removido");
        }
        Instant now = clock.instant();
        target.setStatus(MemberStatus.REMOVED);
        target.setLeftAt(now);
        memberRepository.save(target);
        removeParticipant(groupId, memberUserId, now, ParticipantStatus.REMOVED);
        auditService.record(actorId, "GROUP", groupId, AuditAction.MEMBER_REMOVED,
                Map.of("memberUserId", memberUserId.toString()));
    }

    @Override
    @Transactional
    public void leaveGroup(UUID groupId, UUID userId) {
        GroupMemberEntity member = accessService.requireMember(groupId, userId);
        if (member.getRole() == GroupRole.OWNER) {
            throw new ForbiddenException("O criador não pode sair do grupo; cancele o desafio ou arquive o grupo");
        }
        Instant now = clock.instant();
        member.setStatus(MemberStatus.LEFT);
        member.setLeftAt(now);
        memberRepository.save(member);
        removeParticipant(groupId, userId, now, ParticipantStatus.LEFT);
        auditService.record(userId, "GROUP", groupId, AuditAction.MEMBER_LEFT, null);
    }

    private void removeParticipant(UUID groupId, UUID userId, Instant now, ParticipantStatus status) {
        challengeRepository.findFirstByGroupIdAndStatusInOrderBySequenceNoDesc(groupId,
                        List.of(ChallengeStatus.DRAFT, ChallengeStatus.ACTIVE))
                .flatMap(challenge -> participantRepository.findByChallengeIdAndUserId(challenge.getId(), userId))
                .ifPresent(participant -> {
                    participant.setStatus(status);
                    participant.setLeftAt(now);
                    participantRepository.save(participant);
                });
    }

    private ChallengeResponse currentChallenge(UUID groupId, UUID userId) {
        ChallengeEntity challenge = challengeRepository
                .findFirstByGroupIdAndStatusInOrderBySequenceNoDesc(groupId,
                        List.of(ChallengeStatus.DRAFT, ChallengeStatus.ACTIVE))
                .orElseGet(() -> challengeRepository.findAllByGroupIdOrderBySequenceNoDesc(groupId).stream()
                        .findFirst().orElse(null));
        return challenge == null ? null : challengeService.getChallenge(challenge.getId(), userId);
    }
}
