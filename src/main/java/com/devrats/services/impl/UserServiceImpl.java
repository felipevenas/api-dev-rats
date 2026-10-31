package com.devrats.services.impl;

import com.devrats.dto.UpdateUserRequest;
import com.devrats.dto.UserDataExportResponse;
import com.devrats.dto.UserResponse;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AvatarSource;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.MemberStatus;
import com.devrats.enums.ParticipantStatus;
import com.devrats.enums.TechStack;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.UnauthorizedException;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.RefreshTokenRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.RefreshTokenService;
import com.devrats.services.UserService;
import com.devrats.services.storage.AvatarStorage;
import com.devrats.utils.ActivityDateHelper;
import com.devrats.utils.UserViewHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final GitHubAccountRepository githubAccountRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final RefreshTokenService refreshTokenService;
    private final AvatarStorage avatarStorage;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserServiceImpl(UserRepository userRepository,
                           GitHubAccountRepository githubAccountRepository,
                           GroupMemberRepository groupMemberRepository,
                           ChallengeParticipantRepository participantRepository,
                           RefreshTokenService refreshTokenService,
                           AvatarStorage avatarStorage,
                           PasswordEncoder passwordEncoder,
                           Clock clock) {
        this.userRepository = userRepository;
        this.githubAccountRepository = githubAccountRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.participantRepository = participantRepository;
        this.refreshTokenService = refreshTokenService;
        this.avatarStorage = avatarStorage;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        UserEntity user = getUser(userId);
        return UserViewHelper.toResponse(user, githubAccountRepository.findByUserId(userId).orElse(null));
    }

    @Override
    @Transactional
    public UserResponse updateMe(UUID userId, UpdateUserRequest request) {
        UserEntity user = getUser(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.timezone() != null) {
            ActivityDateHelper.parseZone(request.timezone());
            user.setTimezone(request.timezone());
        }
        if (request.notifyDailyGoal() != null) {
            user.setNotifyDailyGoal(request.notifyDailyGoal());
        }
        if (request.notifyMilestones() != null) {
            user.setNotifyMilestones(request.notifyMilestones());
        }
        if (request.notifyChallengeUpdates() != null) {
            user.setNotifyChallengeUpdates(request.notifyChallengeUpdates());
        }
        if (request.notifyIntegration() != null) {
            user.setNotifyIntegration(request.notifyIntegration());
        }
        //---- Perfil público: texto em branco limpa o campo; lista vazia limpa a stack.
        if (request.bio() != null) {
            user.setBio(UserViewHelper.normalizeOptionalText(request.bio()));
        }
        if (request.location() != null) {
            user.setLocation(UserViewHelper.normalizeOptionalText(request.location()));
        }
        if (request.websiteUrl() != null) {
            user.setWebsiteUrl(UserViewHelper.normalizeOptionalText(request.websiteUrl()));
        }
        if (Boolean.TRUE.equals(request.clearExperienceLevel())) {
            user.setExperienceLevel(null);
        } else if (request.experienceLevel() != null) {
            user.setExperienceLevel(request.experienceLevel());
        }
        if (request.primaryStack() != null) {
            user.setPrimaryStack(TechStack.toCsv(request.primaryStack()));
        }
        userRepository.save(user);
        return UserViewHelper.toResponse(user, githubAccountRepository.findByUserId(userId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataExportResponse exportData(UUID userId) {
        UserEntity user = getUser(userId);
        List<UserDataExportResponse.GroupMembership> groups = groupMemberRepository.findAllByUserId(userId).stream()
                .map(this::toMembership)
                .toList();
        return new UserDataExportResponse(user.getId(), user.getDisplayName(), user.getUsername(), user.getEmail(),
                user.getBio(), user.getLocation(), user.getWebsiteUrl(), user.getExperienceLevel(),
                TechStack.fromCsv(user.getPrimaryStack()), user.getTimezone(), user.getCreatedAt(), groups);
    }

    @Override
    @Transactional
    public void deleteMe(UUID userId, String password) {
        UserEntity user = getUser(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Senha inválida");
        }
        Instant now = clock.instant();
        String suffix = user.getId().toString().substring(0, 8);
        String avatarObjectKey = user.getAvatarObjectKey();
        user.setStatus(UserStatus.DELETED);
        user.setEmail("deleted+" + user.getId() + "@invalid.local");
        user.setUsername("deleted_" + suffix);
        user.setDisplayName("Conta removida");
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setLocation(null);
        user.setWebsiteUrl(null);
        user.setExperienceLevel(null);
        user.setPrimaryStack(null);
        user.setAvatarSource(AvatarSource.NONE);
        user.setAvatarObjectKey(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        userRepository.save(user);
        deleteAvatarObjectQuietly(avatarObjectKey);

        refreshTokenService.revokeAllForUser(userId);
        githubAccountRepository.findByUserId(userId).ifPresent(account -> disconnect(account, now));
        groupMemberRepository.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE).forEach(member -> {
            member.setStatus(MemberStatus.LEFT);
            member.setLeftAt(now);
            groupMemberRepository.save(member);
        });
        participantRepository.findAllByUserIdAndStatus(userId, ParticipantStatus.ACTIVE).forEach(participant -> {
            participant.setStatus(ParticipantStatus.LEFT);
            participant.setLeftAt(now);
            participantRepository.save(participant);
        });
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private UserDataExportResponse.GroupMembership toMembership(GroupMemberEntity member) {
        return new UserDataExportResponse.GroupMembership(member.getGroup().getId(), member.getGroup().getName(),
                member.getRole().name(), member.getStatus().name(), member.getJoinedAt());
    }

    private void deleteAvatarObjectQuietly(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            avatarStorage.delete(objectKey);
        } catch (RuntimeException exception) {
            //---- A conta já foi anonimizada; um objeto órfão não pode desfazer a exclusão.
            log.warn("Falha ao remover avatar da conta excluída: key={}", objectKey, exception);
        }
    }

    private void disconnect(GitHubAccountEntity account, Instant now) {
        account.setConnectionStatus(GitHubConnectionStatus.REVOKED);
        account.setEncryptedAccessToken(null);
        account.setEncryptedRefreshToken(null);
        account.setDisconnectedAt(now);
        githubAccountRepository.save(account);
    }
}
