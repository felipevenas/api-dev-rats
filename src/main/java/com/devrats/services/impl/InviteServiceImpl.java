package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.CreateInviteRequest;
import com.devrats.dto.GroupResponse;
import com.devrats.dto.InvitePreviewResponse;
import com.devrats.dto.InviteResponse;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupInviteEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.GroupRole;
import com.devrats.enums.GroupStatus;
import com.devrats.enums.MemberStatus;
import com.devrats.enums.NotificationType;
import com.devrats.enums.ParticipantStatus;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.GroupInviteRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.GroupRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.GroupAccessService;
import com.devrats.services.GroupService;
import com.devrats.services.InviteService;
import com.devrats.services.NotificationService;
import com.devrats.utils.TokenHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InviteServiceImpl implements InviteService {

    private static final Duration DEFAULT_EXPIRY = Duration.ofDays(7);

    private final GroupInviteRepository inviteRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final GroupAccessService accessService;
    private final GroupService groupService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AppProperties properties;
    private final Clock clock;

    public InviteServiceImpl(GroupInviteRepository inviteRepository,
                             GroupMemberRepository memberRepository,
                             GroupRepository groupRepository,
                             ChallengeRepository challengeRepository,
                             ChallengeParticipantRepository participantRepository,
                             UserRepository userRepository,
                             GroupAccessService accessService,
                             GroupService groupService,
                             AuditService auditService,
                             NotificationService notificationService,
                             AppProperties properties,
                             Clock clock) {
        this.inviteRepository = inviteRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.groupService = groupService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InviteResponse createInvite(UUID groupId, UUID actorId, CreateInviteRequest request) {
        GroupMemberEntity owner = accessService.requireOwner(groupId, actorId);
        String rawToken = TokenHelper.randomToken();
        Instant expiresAt = request.expiresAt() == null ? clock.instant().plus(DEFAULT_EXPIRY) : request.expiresAt();
        if (!expiresAt.isAfter(clock.instant())) {
            throw new com.devrats.exceptions.ValidationException("A validade do convite deve estar no futuro");
        }
        GroupInviteEntity invite = new GroupInviteEntity();
        invite.setGroup(owner.getGroup());
        invite.setTokenHash(TokenHelper.hashToken(rawToken));
        invite.setCreatedBy(owner.getUser());
        invite.setExpiresAt(expiresAt);
        invite.setMaxUses(request.maxUses());
        inviteRepository.save(invite);
        auditService.record(actorId, "GROUP", groupId, AuditAction.INVITE_CREATED,
                Map.of("inviteId", invite.getId().toString()));
        return toResponse(invite, rawToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InviteResponse> listInvites(UUID groupId, UUID actorId) {
        accessService.requireOwner(groupId, actorId);
        return inviteRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(invite -> toResponse(invite, null))
                .toList();
    }

    @Override
    @Transactional
    public void revokeInvite(UUID groupId, UUID inviteId, UUID actorId) {
        accessService.requireOwner(groupId, actorId);
        GroupInviteEntity invite = inviteRepository.findByIdAndGroupId(inviteId, groupId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVITE_NOT_FOUND));
        invite.setRevokedAt(clock.instant());
        inviteRepository.save(invite);
        auditService.record(actorId, "GROUP", groupId, AuditAction.INVITE_REVOKED,
                Map.of("inviteId", inviteId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(String token) {
        GroupInviteEntity invite = inviteRepository.findByTokenHash(TokenHelper.hashToken(token))
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVITE_NOT_FOUND));
        String reason = invalidReason(invite);
        return new InvitePreviewResponse(invite.getGroup().getId(), invite.getGroup().getName(),
                invite.getCreatedBy().getDisplayName(), invite.getExpiresAt(), reason == null, reason);
    }

    @Override
    @Transactional
    public GroupResponse accept(String token, UUID userId) {
        GroupInviteEntity invite = inviteRepository.findByTokenHashForUpdate(TokenHelper.hashToken(token))
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVITE_NOT_FOUND));
        String reason = invalidReason(invite);
        if (reason != null) {
            throw invalidReasonException(reason);
        }
        GroupEntity group = groupRepository.findByIdForUpdate(invite.getGroup().getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.GROUP_NOT_FOUND));
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE, "Grupo não está ativo");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        GroupMemberEntity existing = memberRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);
        if (existing != null && existing.getStatus() == MemberStatus.ACTIVE) {
            return groupService.getGroup(group.getId(), userId);
        }
        if (existing != null && existing.getStatus() != MemberStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.ALREADY_MEMBER, "Você já participou deste grupo");
        }
        long memberCount = memberRepository.countByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE);
        if (memberCount >= group.getMaxMembers()) {
            throw new ConflictException(ErrorCode.GROUP_FULL);
        }

        GroupMemberEntity member = new GroupMemberEntity();
        member.setGroup(group);
        member.setUser(user);
        member.setRole(GroupRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);

        ChallengeEntity challenge = challengeRepository
                .findFirstByGroupIdAndStatusInOrderBySequenceNoDesc(group.getId(),
                        List.of(ChallengeStatus.DRAFT, ChallengeStatus.ACTIVE))
                .orElse(null);
        if (challenge != null) {
            if (isAdmissionClosed(challenge)) {
                throw new ConflictException(ErrorCode.ADMISSION_CLOSED);
            }
            createParticipant(challenge, member);
        }

        invite.setUses(invite.getUses() + 1);
        inviteRepository.save(invite);
        auditService.record(userId, "GROUP", group.getId(), AuditAction.INVITE_ACCEPTED,
                Map.of("inviteId", invite.getId().toString()));
        notificationService.notify(invite.getCreatedBy().getId(), NotificationType.INVITE_ACCEPTED, null,
                "invite-accepted:" + invite.getId() + ":" + userId,
                Map.of("userId", userId.toString(), "groupName", group.getName()));
        return groupService.getGroup(group.getId(), userId);
    }

    private void createParticipant(ChallengeEntity challenge, GroupMemberEntity member) {
        participantRepository.findByChallengeIdAndUserId(challenge.getId(), member.getUser().getId())
                .orElseGet(() -> {
                    ChallengeParticipantEntity participant = new ChallengeParticipantEntity();
                    participant.setChallenge(challenge);
                    participant.setUser(member.getUser());
                    participant.setGroupMember(member);
                    participant.setStatus(ParticipantStatus.ACTIVE);
                    return participantRepository.save(participant);
                });
    }

    private boolean isAdmissionClosed(ChallengeEntity challenge) {
        //---- While the challenge is a draft, invites are accepted even if the clock already passed startsAt;
        //---- the scheduled activation closes admission when it actually starts.
        if (challenge.getStatus() == ChallengeStatus.DRAFT) {
            return false;
        }
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            return true;
        }
        Instant deadline = challenge.getAdmissionEndsAt() == null ? challenge.getStartsAt() : challenge.getAdmissionEndsAt();
        return clock.instant().isAfter(deadline);
    }

    private String invalidReason(GroupInviteEntity invite) {
        Instant now = clock.instant();
        if (invite.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (invite.getExpiresAt().isBefore(now)) {
            return "EXPIRED";
        }
        if (invite.getMaxUses() != null && invite.getUses() >= invite.getMaxUses()) {
            return "EXHAUSTED";
        }
        if (invite.getGroup().getStatus() != GroupStatus.ACTIVE) {
            return "GROUP_ARCHIVED";
        }
        return null;
    }

    private RuntimeException invalidReasonException(String reason) {
        return switch (reason) {
            case "REVOKED" -> new ConflictException(ErrorCode.INVITE_REVOKED);
            case "EXPIRED" -> new ConflictException(ErrorCode.INVITE_EXPIRED);
            case "EXHAUSTED" -> new ConflictException(ErrorCode.INVITE_EXHAUSTED);
            default -> new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE);
        };
    }

    private InviteResponse toResponse(GroupInviteEntity invite, String rawToken) {
        String url = rawToken == null ? null : properties.getFrontendUrl() + "/invites/" + rawToken;
        return new InviteResponse(invite.getId(), rawToken, url, invite.getCreatedAt(), invite.getExpiresAt(),
                invite.getRevokedAt(), invite.getMaxUses(), invite.getUses());
    }
}
