package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.FriendshipStatusResponse;
import com.devrats.dto.PublicUserResponse;
import com.devrats.dto.UserSearchResult;
import com.devrats.entities.UserEntity;
import com.devrats.enums.FriendshipStatus;
import com.devrats.enums.FriendshipStatusView;
import com.devrats.enums.PresenceStatus;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.FriendService;
import com.devrats.services.PresenceService;
import com.devrats.services.SocialRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceImplTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private FriendService friendService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private SocialRateLimitService socialRateLimitService;

    private UserDirectoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDirectoryServiceImpl(userRepository, friendshipRepository, friendService,
                presenceService, socialRateLimitService);
    }

    @Test
    void shouldNotExposePresenceToNonFriends() {
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(user("bruno")));
        when(friendService.statuses(ACTOR_ID, List.of(TARGET_ID)))
                .thenReturn(List.of(new FriendshipStatusResponse(TARGET_ID, FriendshipStatusView.NONE)));
        when(friendshipRepository.countFriends(TARGET_ID, FriendshipStatus.ACCEPTED, UserStatus.ACTIVE))
                .thenReturn(3L);

        PublicUserResponse profile = service.getPublicProfile(ACTOR_ID, "Bruno");

        assertThat(profile.presence()).isNull();
        assertThat(profile.friendshipStatus()).isEqualTo(FriendshipStatusView.NONE);
        assertThat(profile.friendsCount()).isEqualTo(3L);
        verify(presenceService, never()).statusOf(any());
    }

    @Test
    void shouldExposePresenceToFriends() {
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(user("bruno")));
        when(friendService.statuses(ACTOR_ID, List.of(TARGET_ID)))
                .thenReturn(List.of(new FriendshipStatusResponse(TARGET_ID, FriendshipStatusView.FRIENDS)));
        when(presenceService.statusOf(TARGET_ID)).thenReturn(PresenceStatus.ONLINE);
        when(friendshipRepository.countFriends(TARGET_ID, FriendshipStatus.ACCEPTED, UserStatus.ACTIVE))
                .thenReturn(1L);

        PublicUserResponse profile = service.getPublicProfile(ACTOR_ID, "bruno");

        assertThat(profile.presence()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(profile.friendshipStatus()).isEqualTo(FriendshipStatusView.FRIENDS);
    }

    @Test
    void shouldReturnUniform404_whenUsernameIsUnknownOrInvalid() {
        when(userRepository.findByUsernameAndStatus("ghost", UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicProfile(ACTOR_ID, "ghost"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getPublicProfile(ACTOR_ID, "a"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldEscapeLikeWildcardsAndExcludeSelf() {
        when(userRepository.searchByUsernamePrefix(eq(UserStatus.ACTIVE), anyString(), eq(ACTOR_ID), any(Pageable.class)))
                .thenReturn(List.of(user("a_b")));
        when(friendService.statuses(eq(ACTOR_ID), any())).thenReturn(List.of());

        List<UserSearchResult> results = service.search(ACTOR_ID, "A_b", 50);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchByUsernamePrefix(eq(UserStatus.ACTIVE), pattern.capture(), eq(ACTOR_ID),
                pageable.capture());
        assertThat(pattern.getValue()).isEqualTo("a\\_b%");
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().username()).isEqualTo("a_b");
    }

    @Test
    void shouldRejectQueryShorterThanTwoCharacters() {
        assertThatThrownBy(() -> service.search(ACTOR_ID, "a", 10))
                .isInstanceOf(ValidationException.class);
    }

    private static UserEntity user(String username) {
        UserEntity user = new UserEntity();
        user.setId(TARGET_ID);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
