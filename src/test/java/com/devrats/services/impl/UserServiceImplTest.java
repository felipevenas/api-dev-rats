package com.devrats.services.impl;

import com.devrats.entities.UserEntity;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.UnauthorizedException;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.NotificationRepository;
import com.devrats.repositories.UserPresenceRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.RefreshTokenService;
import com.devrats.services.storage.AvatarStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GitHubAccountRepository githubAccountRepository = mock(GitHubAccountRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final ChallengeParticipantRepository participantRepository = mock(ChallengeParticipantRepository.class);
    private final FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
    private final UserPresenceRepository presenceRepository = mock(UserPresenceRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AvatarStorage avatarStorage = mock(AvatarStorage.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private UserServiceImpl service;
    private UUID userId;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, githubAccountRepository, groupMemberRepository,
                participantRepository, friendshipRepository, presenceRepository, notificationRepository,
                refreshTokenService, avatarStorage, passwordEncoder,
                Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC));
        userId = UUID.randomUUID();
        user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@test.local");
        user.setUsername("usuario");
        user.setDisplayName("Usuario");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
        when(githubAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(groupMemberRepository.findAllByUserIdAndStatus(userId, com.devrats.enums.MemberStatus.ACTIVE))
                .thenReturn(List.of());
        when(participantRepository.findAllByUserIdAndStatus(userId, com.devrats.enums.ParticipantStatus.ACTIVE))
                .thenReturn(List.of());
    }

    @Test
    void shouldRemoveSocialGraphAndNotifications_whenAccountDeleted() {
        service.deleteMe(userId, "senha");

        verify(friendshipRepository).deleteAllForUser(userId);
        verify(presenceRepository).deleteById(userId);
        verify(notificationRepository).deleteAllForUser(userId);
        verify(notificationRepository).deleteSocialByActor(userId.toString());
        verify(refreshTokenService).revokeAllForUser(userId);
    }

    @Test
    void shouldNotDeleteAnything_whenPasswordIsInvalid() {
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteMe(userId, "errada"))
                .isInstanceOf(UnauthorizedException.class);
        verify(friendshipRepository, never()).deleteAllForUser(any());
        verify(presenceRepository, never()).deleteById(any());
        verify(notificationRepository, never()).deleteAllForUser(any());
    }
}
