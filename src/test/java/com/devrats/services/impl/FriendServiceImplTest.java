package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.FriendRequestResponse;
import com.devrats.dto.FriendshipStatusResponse;
import com.devrats.dto.SendFriendRequestRequest;
import com.devrats.entities.FriendshipEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.FriendRequestDirection;
import com.devrats.enums.FriendshipStatus;
import com.devrats.enums.FriendshipStatusView;
import com.devrats.enums.NotificationType;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.NotificationService;
import com.devrats.services.PresenceService;
import com.devrats.services.SocialRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FRIENDSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STRANGER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private SocialRateLimitService socialRateLimitService;

    private FriendServiceImpl service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getFriends().setMaxPerUser(1000);
        properties.getFriends().setMaxPendingOutgoing(50);
        properties.getFriends().setDeclinedCooldown(Duration.ofHours(72));
        service = new FriendServiceImpl(friendshipRepository, userRepository, notificationService, auditService,
                presenceService, socialRateLimitService, properties, CLOCK, new DirectTransactionManager());
    }

    @Test
    void shouldRejectSelfRequest() {
        UserEntity actor = user(ACTOR_ID, "ana");
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameAndStatus("ana", UserStatus.ACTIVE)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.sendRequest(ACTOR_ID, new SendFriendRequestRequest("Ana")))
                .isInstanceOf(ValidationException.class);

        verify(friendshipRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotifyAddressee_whenRequestSent() {
        UserEntity actor = user(ACTOR_ID, "ana");
        UserEntity target = user(TARGET_ID, "bruno");
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.empty());
        when(friendshipRepository.countByRequesterIdAndStatus(ACTOR_ID, FriendshipStatus.PENDING)).thenReturn(0L);
        when(friendshipRepository.saveAndFlush(any(FriendshipEntity.class))).thenAnswer(invocation -> {
            FriendshipEntity entity = invocation.getArgument(0);
            entity.setId(FRIENDSHIP_ID);
            return entity;
        });

        FriendRequestResponse response = service.sendRequest(ACTOR_ID, new SendFriendRequestRequest("Bruno"));

        assertThat(response.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(response.direction()).isEqualTo(FriendRequestDirection.SENT);
        assertThat(response.user().username()).isEqualTo("bruno");
        verify(notificationService).notify(eq(TARGET_ID), eq(NotificationType.FRIEND_REQUEST), isNull(),
                eq("friend-request:" + FRIENDSHIP_ID + ":" + NOW.toEpochMilli()),
                org.mockito.ArgumentMatchers.argThat((Map<String, Object> payload) ->
                        ACTOR_ID.toString().equals(payload.get("actorUserId"))
                                && "ana".equals(payload.get("actorUsername"))
                                && FRIENDSHIP_ID.toString().equals(payload.get("friendshipId"))));
        verify(auditService).record(eq(ACTOR_ID), eq("FRIENDSHIP"), eq(FRIENDSHIP_ID),
                eq(AuditAction.FRIEND_REQUEST_SENT), anyMap());
    }

    @Test
    void shouldAutoAccept_whenReverseRequestExists() {
        UserEntity actor = user(ACTOR_ID, "ana");
        UserEntity target = user(TARGET_ID, "bruno");
        FriendshipEntity reverse = friendship(FRIENDSHIP_ID, target, actor);
        reverse.setStatus(FriendshipStatus.PENDING);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.of(reverse));
        when(friendshipRepository.countFriends(any(UUID.class), eq(FriendshipStatus.ACCEPTED), eq(UserStatus.ACTIVE)))
                .thenReturn(0L);
        when(friendshipRepository.save(any(FriendshipEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FriendRequestResponse response = service.sendRequest(ACTOR_ID, new SendFriendRequestRequest("bruno"));

        assertThat(response.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(reverse.getRespondedAt()).isEqualTo(NOW);
        assertThat(reverse.getUpdatedAt()).isEqualTo(NOW);
        verify(notificationService).notify(eq(TARGET_ID), eq(NotificationType.FRIEND_REQUEST_ACCEPTED), isNull(),
                eq("friend-accepted:" + FRIENDSHIP_ID), anyMap());
        verify(friendshipRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldOnlyAllowAddresseeToAccept() {
        when(userRepository.findById(STRANGER_ID)).thenReturn(Optional.of(user(STRANGER_ID, "carla")));
        when(friendshipRepository.findByIdAndAddresseeIdAndStatus(
                FRIENDSHIP_ID, STRANGER_ID, FriendshipStatus.PENDING)).thenReturn(Optional.empty());
        when(friendshipRepository.findByIdAndAddresseeId(FRIENDSHIP_ID, STRANGER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptRequest(STRANGER_ID, FRIENDSHIP_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(exception -> ((NotFoundException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FRIENDSHIP_NOT_FOUND);
    }

    @Test
    void shouldRejectAccept_whenRequestIsNotPending() {
        FriendshipEntity accepted = friendship(FRIENDSHIP_ID, user(ACTOR_ID, "ana"), user(TARGET_ID, "bruno"));
        accepted.setStatus(FriendshipStatus.ACCEPTED);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "bruno")));
        when(friendshipRepository.findByIdAndAddresseeIdAndStatus(
                FRIENDSHIP_ID, TARGET_ID, FriendshipStatus.PENDING)).thenReturn(Optional.empty());
        when(friendshipRepository.findByIdAndAddresseeId(FRIENDSHIP_ID, TARGET_ID))
                .thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service.acceptRequest(TARGET_ID, FRIENDSHIP_ID))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FRIENDSHIP_STATE);
    }

    @Test
    void shouldCancelOnlyByRequester() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "bruno")));
        when(friendshipRepository.findByIdAndRequesterIdAndStatus(
                FRIENDSHIP_ID, TARGET_ID, FriendshipStatus.PENDING)).thenReturn(Optional.empty());
        when(friendshipRepository.findByIdAndRequesterId(FRIENDSHIP_ID, TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelRequest(TARGET_ID, FRIENDSHIP_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldNotifyRequester_whenAccepted() {
        FriendshipEntity pending = friendship(FRIENDSHIP_ID, user(ACTOR_ID, "ana"), user(TARGET_ID, "bruno"));
        pending.setStatus(FriendshipStatus.PENDING);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "bruno")));
        when(friendshipRepository.findByIdAndAddresseeIdAndStatus(
                FRIENDSHIP_ID, TARGET_ID, FriendshipStatus.PENDING)).thenReturn(Optional.of(pending));
        when(friendshipRepository.countFriends(any(UUID.class), eq(FriendshipStatus.ACCEPTED), eq(UserStatus.ACTIVE)))
                .thenReturn(0L);
        when(friendshipRepository.save(any(FriendshipEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FriendRequestResponse response = service.acceptRequest(TARGET_ID, FRIENDSHIP_ID);

        assertThat(response.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(response.direction()).isEqualTo(FriendRequestDirection.RECEIVED);
        verify(notificationService).notify(eq(ACTOR_ID), eq(NotificationType.FRIEND_REQUEST_ACCEPTED), isNull(),
                eq("friend-accepted:" + FRIENDSHIP_ID),
                org.mockito.ArgumentMatchers.argThat((Map<String, Object> payload) ->
                        TARGET_ID.toString().equals(payload.get("actorUserId"))));
        verify(auditService).record(eq(TARGET_ID), eq("FRIENDSHIP"), eq(FRIENDSHIP_ID),
                eq(AuditAction.FRIEND_REQUEST_ACCEPTED), anyMap());
    }

    @Test
    void shouldRemoveFriendship() {
        FriendshipEntity accepted = friendship(FRIENDSHIP_ID, user(ACTOR_ID, "ana"), user(TARGET_ID, "bruno"));
        accepted.setStatus(FriendshipStatus.ACCEPTED);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(user(ACTOR_ID, "ana")));
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.of(accepted));

        service.removeFriend(ACTOR_ID, TARGET_ID);

        verify(friendshipRepository).delete(accepted);
        verify(auditService).record(eq(ACTOR_ID), eq("FRIENDSHIP"), eq(FRIENDSHIP_ID),
                eq(AuditAction.FRIEND_REMOVED), anyMap());
    }

    @Test
    void shouldRejectRemovingWhenPairIsNotAccepted() {
        FriendshipEntity pending = friendship(FRIENDSHIP_ID, user(ACTOR_ID, "ana"), user(TARGET_ID, "bruno"));
        pending.setStatus(FriendshipStatus.PENDING);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(user(ACTOR_ID, "ana")));
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.removeFriend(ACTOR_ID, TARGET_ID))
                .isInstanceOf(NotFoundException.class);
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void shouldEnforceCooldownAfterDeclined() {
        UserEntity actor = user(ACTOR_ID, "ana");
        UserEntity target = user(TARGET_ID, "bruno");
        FriendshipEntity declined = friendship(FRIENDSHIP_ID, actor, target);
        declined.setStatus(FriendshipStatus.DECLINED);
        declined.setRespondedAt(NOW.minus(Duration.ofHours(1)));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.of(declined));

        assertThatThrownBy(() -> service.sendRequest(ACTOR_ID, new SendFriendRequestRequest("bruno")))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FRIENDSHIP_COOLDOWN);
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void shouldReopenDeclinedPairAfterCooldown() {
        UserEntity actor = user(ACTOR_ID, "ana");
        UserEntity target = user(TARGET_ID, "bruno");
        FriendshipEntity declined = friendship(FRIENDSHIP_ID, target, actor);
        declined.setStatus(FriendshipStatus.DECLINED);
        declined.setRespondedAt(NOW.minus(Duration.ofHours(73)));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.of(declined));
        when(friendshipRepository.save(any(FriendshipEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FriendRequestResponse response = service.sendRequest(ACTOR_ID, new SendFriendRequestRequest("bruno"));

        assertThat(response.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(declined.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(declined.getRequester().getId()).isEqualTo(ACTOR_ID);
        assertThat(declined.getCreatedAt()).isEqualTo(NOW);
        assertThat(declined.getRespondedAt()).isNull();
        verify(notificationService).notify(eq(TARGET_ID), eq(NotificationType.FRIEND_REQUEST), isNull(),
                eq("friend-request:" + FRIENDSHIP_ID + ":" + NOW.toEpochMilli()), anyMap());
    }

    @Test
    void shouldReconcileConcurrentInsertIdempotently() {
        UserEntity actor = user(ACTOR_ID, "ana");
        UserEntity target = user(TARGET_ID, "bruno");
        FriendshipEntity winner = friendship(FRIENDSHIP_ID, actor, target);
        winner.setStatus(FriendshipStatus.PENDING);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        //---- Primeira tentativa: o índice único do par rejeita a segunda inserção.
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.empty());
        when(friendshipRepository.countByRequesterIdAndStatus(ACTOR_ID, FriendshipStatus.PENDING)).thenReturn(0L);
        when(friendshipRepository.saveAndFlush(any(FriendshipEntity.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate pair uq_friendships_pair"));
        //---- Reconciliação: relê a linha vencedora e devolve a mesma solicitação.
        when(friendshipRepository.findPair(ACTOR_ID, TARGET_ID)).thenReturn(Optional.of(winner));

        FriendRequestResponse response = service.sendRequest(ACTOR_ID, new SendFriendRequestRequest("bruno"));

        assertThat(response.id()).isEqualTo(FRIENDSHIP_ID);
        assertThat(response.status()).isEqualTo(FriendshipStatus.PENDING);
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void shouldEnforcePendingOutgoingLimit() {
        UserEntity actor = user(ACTOR_ID, "ana");
        UserEntity target = user(TARGET_ID, "bruno");
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userRepository.findByUsernameAndStatus("bruno", UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(friendshipRepository.findPairForUpdate(ACTOR_ID, TARGET_ID)).thenReturn(Optional.empty());
        when(friendshipRepository.countByRequesterIdAndStatus(ACTOR_ID, FriendshipStatus.PENDING)).thenReturn(50L);

        assertThatThrownBy(() -> service.sendRequest(ACTOR_ID, new SendFriendRequestRequest("bruno")))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_REQUEST_LIMIT_REACHED);
    }

    @Test
    void shouldMapStatuses() {
        FriendshipEntity pending = friendship(FRIENDSHIP_ID, user(ACTOR_ID, "ana"), user(TARGET_ID, "bruno"));
        pending.setStatus(FriendshipStatus.PENDING);
        FriendshipEntity accepted = friendship(UUID.randomUUID(), user(STRANGER_ID, "carla"), user(ACTOR_ID, "ana"));
        accepted.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findPairsWithStatuses(
                eq(ACTOR_ID), anyCollection(), anyCollection(), eq(UserStatus.ACTIVE)))
                .thenReturn(List.of(pending, accepted));

        List<FriendshipStatusResponse> statuses = service.statuses(ACTOR_ID,
                List.of(ACTOR_ID, TARGET_ID, STRANGER_ID, UUID.randomUUID()));

        assertThat(statuses).extracting(FriendshipStatusResponse::status)
                .containsExactly(FriendshipStatusView.SELF, FriendshipStatusView.PENDING_OUTGOING,
                        FriendshipStatusView.FRIENDS, FriendshipStatusView.NONE);
    }

    @Test
    void shouldRejectTooManyStatusIds() {
        List<UUID> ids = java.util.stream.IntStream.range(0, 51).mapToObj(ignored -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> service.statuses(ACTOR_ID, ids))
                .isInstanceOf(ValidationException.class);
    }

    private static UserEntity user(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username.substring(0, 1).toUpperCase() + username.substring(1));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static FriendshipEntity friendship(UUID id, UserEntity requester, UserEntity addressee) {
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setId(id);
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setCreatedAt(NOW.minus(Duration.ofDays(1)));
        friendship.setUpdatedAt(NOW.minus(Duration.ofDays(1)));
        return friendship;
    }

    /**
     * Executa o callback no mesmo thread, sem um gerenciador real
     * (o serviço só usa o template para delimitar a escrita).
     */
    private static final class DirectTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
