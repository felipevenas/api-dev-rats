package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.FriendRequestResponse;
import com.devrats.dto.FriendResponse;
import com.devrats.dto.FriendshipStatusResponse;
import com.devrats.dto.SendFriendRequestRequest;
import com.devrats.entities.FriendshipEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.FriendRequestDirection;
import com.devrats.enums.FriendshipStatus;
import com.devrats.enums.FriendshipStatusView;
import com.devrats.enums.NotificationType;
import com.devrats.enums.PresenceStatus;
import com.devrats.enums.UserStatus;
import com.devrats.enums.AuditAction;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.FriendService;
import com.devrats.services.NotificationService;
import com.devrats.services.PresenceService;
import com.devrats.services.SocialRateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Máquina de estados do grafo social.
 *
 * <p>Transições:</p>
 * <pre>
 * nenhum            -> PENDING
 * PENDING (dest.)   -> ACCEPTED | DECLINED
 * PENDING (rem.)    -> CANCELLED
 * PENDING cruzado   -> ACCEPTED (automático)
 * ACCEPTED          -> removido
 * DECLINED/CANCELLED-> PENDING (após cooldown, quando aplicável)
 * </pre>
 *
 * <p>Segurança: quem não participa recebe 404 (a autorização está no {@code WHERE});
 * quem participa em estado incompatível recebe 409. Nenhum dado sensível é logado.</p>
 */
@Service
public class FriendServiceImpl implements FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendServiceImpl.class);

    private static final String ENTITY_TYPE = "FRIENDSHIP";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{2,30}$");
    private static final int MAX_STATUS_IDS = 50;

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PresenceService presenceService;
    private final SocialRateLimitService socialRateLimitService;
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public FriendServiceImpl(FriendshipRepository friendshipRepository,
                             UserRepository userRepository,
                             NotificationService notificationService,
                             AuditService auditService,
                             PresenceService presenceService,
                             SocialRateLimitService socialRateLimitService,
                             AppProperties properties,
                             Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.presenceService = presenceService;
        this.socialRateLimitService = socialRateLimitService;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    //---- Leitura -------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<FriendResponse> listFriends(UUID userId, Pageable pageable) {
        Page<FriendshipEntity> page = friendshipRepository.findFriends(userId, FriendshipStatus.ACCEPTED,
                UserStatus.ACTIVE, pageable);
        List<UUID> friendIds = page.getContent().stream()
                .map(friendship -> counterpart(friendship, userId).getId())
                .toList();
        Map<UUID, PresenceStatus> presence = presenceService.statusesOf(friendIds);
        return page.map(friendship -> {
            UserEntity friend = counterpart(friendship, userId);
            Instant friendsSince = friendship.getRespondedAt() == null
                    ? friendship.getCreatedAt() : friendship.getRespondedAt();
            return new FriendResponse(friendship.getId(), friend.getId(), friend.getUsername(),
                    friend.getDisplayName(), friend.getAvatarUrl(),
                    presence.getOrDefault(friend.getId(), PresenceStatus.OFFLINE), friendsSince);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> listRequests(UUID userId, FriendRequestDirection direction, Pageable pageable) {
        Page<FriendshipEntity> page = direction == FriendRequestDirection.SENT
                ? friendshipRepository.findSentRequests(userId, FriendshipStatus.PENDING, UserStatus.ACTIVE, pageable)
                : friendshipRepository.findReceivedRequests(userId, FriendshipStatus.PENDING, UserStatus.ACTIVE, pageable);
        return page.map(friendship -> toResponse(friendship, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendshipStatusResponse> statuses(UUID userId, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (userIds.size() > MAX_STATUS_IDS) {
            throw new ValidationException("Informe no máximo " + MAX_STATUS_IDS + " usuários");
        }
        Set<UUID> uniqueIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> otherIds = uniqueIds.stream()
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, FriendshipEntity> pairs = otherIds.isEmpty()
                ? Map.of()
                : friendshipRepository.findPairsWithStatuses(userId, otherIds,
                        List.of(FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED), UserStatus.ACTIVE).stream()
                .collect(Collectors.toMap(friendship -> counterpart(friendship, userId).getId(),
                        friendship -> friendship, (first, second) -> first));
        return uniqueIds.stream()
                .map(id -> new FriendshipStatusResponse(id, resolveStatus(userId, id, pairs.get(id))))
                .toList();
    }

    //---- Escrita -------------------------------------------------------------------------------

    /**
     * Envio de solicitação.
     *
     * <p>Fora de uma transação declarativa de propósito: a violação do índice único
     * do par (corrida entre duas inserções) precisa ser capturada e reconciliada em
     * uma transação nova. Todo o trabalho real roda dentro do {@link TransactionTemplate}.</p>
     */
    @Override
    public FriendRequestResponse sendRequest(UUID userId, SendFriendRequestRequest request) {
        socialRateLimitService.checkFriendRequest(userId);
        String username = normalizeUsername(request.username());
        UserEntity actor = requireActiveUser(userId);
        UserEntity target = userRepository.findByUsernameAndStatus(username, UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        if (target.getId().equals(actor.getId())) {
            throw new ValidationException("Você não pode enviar uma solicitação para si mesmo");
        }
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> doSendRequest(actor, target)));
        } catch (DataIntegrityViolationException exception) {
            //---- Sem valores no log: apenas o fato do conflito.
            log.debug("[friends] conflito de unicidade ao criar solicitação; reconciliando o par");
            return Objects.requireNonNull(transactionTemplate.execute(status -> reconcileConcurrentSend(actor, target)));
        }
    }

    @Override
    @Transactional
    public FriendRequestResponse acceptRequest(UUID userId, UUID requestId) {
        requireActiveUser(userId);
        FriendshipEntity friendship = findPendingForAddressee(userId, requestId);
        Instant now = clock.instant();
        ensureFriendCapacity(friendship.getRequester().getId());
        ensureFriendCapacity(friendship.getAddressee().getId());
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(now);
        friendship.setUpdatedAt(now);
        friendshipRepository.save(friendship);
        notifyAccepted(friendship, friendship.getAddressee());
        auditService.record(userId, ENTITY_TYPE, friendship.getId(), AuditAction.FRIEND_REQUEST_ACCEPTED,
                pairMetadata(friendship));
        return toResponse(friendship, userId);
    }

    @Override
    @Transactional
    public FriendRequestResponse declineRequest(UUID userId, UUID requestId) {
        requireActiveUser(userId);
        FriendshipEntity friendship = findPendingForAddressee(userId, requestId);
        Instant now = clock.instant();
        friendship.setStatus(FriendshipStatus.DECLINED);
        friendship.setRespondedAt(now);
        friendship.setUpdatedAt(now);
        friendshipRepository.save(friendship);
        auditService.record(userId, ENTITY_TYPE, friendship.getId(), AuditAction.FRIEND_REQUEST_DECLINED,
                pairMetadata(friendship));
        return toResponse(friendship, userId);
    }

    @Override
    @Transactional
    public void cancelRequest(UUID userId, UUID requestId) {
        requireActiveUser(userId);
        FriendshipEntity friendship = findPendingForRequester(userId, requestId);
        Instant now = clock.instant();
        friendship.setStatus(FriendshipStatus.CANCELLED);
        friendship.setRespondedAt(now);
        friendship.setUpdatedAt(now);
        friendshipRepository.save(friendship);
        auditService.record(userId, ENTITY_TYPE, friendship.getId(), AuditAction.FRIEND_REQUEST_CANCELLED,
                pairMetadata(friendship));
    }

    @Override
    @Transactional
    public void removeFriend(UUID userId, UUID friendUserId) {
        requireActiveUser(userId);
        if (userId.equals(friendUserId)) {
            throw new NotFoundException(ErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        FriendshipEntity friendship = friendshipRepository.findPairForUpdate(userId, friendUserId)
                .filter(pair -> pair.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.FRIENDSHIP_NOT_FOUND));
        Map<String, Object> metadata = pairMetadata(friendship);
        friendshipRepository.delete(friendship);
        auditService.record(userId, ENTITY_TYPE, friendship.getId(), AuditAction.FRIEND_REMOVED, metadata);
    }

    //---- Internos ------------------------------------------------------------------------------

    private FriendRequestResponse doSendRequest(UserEntity actor, UserEntity target) {
        Instant now = clock.instant();
        Optional<FriendshipEntity> existing = friendshipRepository.findPairForUpdate(actor.getId(), target.getId());
        if (existing.isPresent()) {
            return handleExistingPair(existing.get(), actor, target, now);
        }
        ensurePendingCapacity(actor.getId());
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setRequester(actor);
        friendship.setAddressee(target);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendship.setCreatedAt(now);
        friendship.setUpdatedAt(now);
        //---- flush imediato: a violação do índice único precisa aparecer aqui, não no commit.
        friendshipRepository.saveAndFlush(friendship);
        notifyRequestSent(friendship, actor);
        auditService.record(actor.getId(), ENTITY_TYPE, friendship.getId(), AuditAction.FRIEND_REQUEST_SENT,
                pairMetadata(friendship));
        return toResponse(friendship, actor.getId(), FriendRequestDirection.SENT);
    }

    private FriendRequestResponse handleExistingPair(FriendshipEntity friendship, UserEntity actor,
                                                     UserEntity target, Instant now) {
        return switch (friendship.getStatus()) {
            case ACCEPTED -> throw new ConflictException(ErrorCode.INVALID_FRIENDSHIP_STATE, "Vocês já são amigos");
            case PENDING -> {
                if (friendship.getRequester().getId().equals(actor.getId())) {
                    throw new ConflictException(ErrorCode.INVALID_FRIENDSHIP_STATE,
                            "Já existe uma solicitação pendente");
                }
                //---- Solicitação cruzada: aceita a mesma linha, sem criar outra.
                autoAccept(friendship, now);
                yield toResponse(friendship, actor.getId(), FriendRequestDirection.SENT);
            }
            case DECLINED -> {
                ensureCooldownElapsed(friendship, now);
                yield reopen(friendship, actor, target, now);
            }
            case CANCELLED -> reopen(friendship, actor, target, now);
        };
    }

    private FriendRequestResponse reopen(FriendshipEntity friendship, UserEntity actor, UserEntity target, Instant now) {
        friendship.setRequester(actor);
        friendship.setAddressee(target);
        friendship.setStatus(FriendshipStatus.PENDING);
        //---- Renova created_at: a chave de dedupe da notificação usa esse instante.
        friendship.setCreatedAt(now);
        friendship.setUpdatedAt(now);
        friendship.setRespondedAt(null);
        friendshipRepository.save(friendship);
        notifyRequestSent(friendship, actor);
        auditService.record(actor.getId(), ENTITY_TYPE, friendship.getId(), AuditAction.FRIEND_REQUEST_SENT,
                pairMetadata(friendship));
        return toResponse(friendship, actor.getId(), FriendRequestDirection.SENT);
    }

    private void autoAccept(FriendshipEntity friendship, Instant now) {
        ensureFriendCapacity(friendship.getRequester().getId());
        ensureFriendCapacity(friendship.getAddressee().getId());
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(now);
        friendship.setUpdatedAt(now);
        friendshipRepository.save(friendship);
        notifyAccepted(friendship, friendship.getAddressee());
        auditService.record(friendship.getAddressee().getId(), ENTITY_TYPE, friendship.getId(),
                AuditAction.FRIEND_REQUEST_ACCEPTED, pairMetadata(friendship));
    }

    /**
     * Caminho de recuperação quando duas inserções concorrentes disputaram o par.
     * Relê a linha vencedora e devolve uma resposta idempotente.
     */
    private FriendRequestResponse reconcileConcurrentSend(UserEntity actor, UserEntity target) {
        Instant now = clock.instant();
        FriendshipEntity friendship = friendshipRepository.findPair(actor.getId(), target.getId())
                .orElseThrow(() -> new ConflictException(ErrorCode.DATA_CONFLICT,
                        "Não foi possível enviar a solicitação"));
        return switch (friendship.getStatus()) {
            case ACCEPTED -> toResponse(friendship, actor.getId(), FriendRequestDirection.SENT);
            case PENDING -> {
                if (friendship.getRequester().getId().equals(actor.getId())) {
                    yield toResponse(friendship, actor.getId(), FriendRequestDirection.SENT);
                }
                autoAccept(friendship, now);
                yield toResponse(friendship, actor.getId(), FriendRequestDirection.SENT);
            }
            case DECLINED -> {
                ensureCooldownElapsed(friendship, now);
                yield reopen(friendship, actor, target, now);
            }
            case CANCELLED -> reopen(friendship, actor, target, now);
        };
    }

    private void ensureCooldownElapsed(FriendshipEntity friendship, Instant now) {
        Instant respondedAt = friendship.getRespondedAt();
        if (respondedAt == null) {
            return;
        }
        //---- Mensagem neutra de propósito: não confirma que houve recusa.
        if (respondedAt.plus(properties.getFriends().getDeclinedCooldown()).isAfter(now)) {
            throw new ConflictException(ErrorCode.FRIENDSHIP_COOLDOWN,
                    "Não é possível enviar a solicitação agora. Tente novamente mais tarde.");
        }
    }

    private void ensurePendingCapacity(UUID requesterId) {
        long pending = friendshipRepository.countByRequesterIdAndStatus(requesterId, FriendshipStatus.PENDING);
        if (pending >= properties.getFriends().getMaxPendingOutgoing()) {
            throw new ConflictException(ErrorCode.FRIEND_REQUEST_LIMIT_REACHED);
        }
    }

    private void ensureFriendCapacity(UUID userId) {
        long friends = friendshipRepository.countFriends(userId, FriendshipStatus.ACCEPTED, UserStatus.ACTIVE);
        if (friends >= properties.getFriends().getMaxPerUser()) {
            throw new ConflictException(ErrorCode.FRIEND_LIMIT_REACHED);
        }
    }

    /**
     * Busca a solicitação pendente com lock. Quando a linha existe mas não está
     * pendente, devolve 409; quando não existe/não pertence ao usuário, 404.
     */
    private FriendshipEntity findPendingForAddressee(UUID userId, UUID requestId) {
        return friendshipRepository.findByIdAndAddresseeIdAndStatus(requestId, userId, FriendshipStatus.PENDING)
                .orElseThrow(() -> friendshipRepository.findByIdAndAddresseeId(requestId, userId).isPresent()
                        ? new ConflictException(ErrorCode.INVALID_FRIENDSHIP_STATE,
                                "A solicitação não está pendente")
                        : new NotFoundException(ErrorCode.FRIENDSHIP_NOT_FOUND));
    }

    private FriendshipEntity findPendingForRequester(UUID userId, UUID requestId) {
        return friendshipRepository.findByIdAndRequesterIdAndStatus(requestId, userId, FriendshipStatus.PENDING)
                .orElseThrow(() -> friendshipRepository.findByIdAndRequesterId(requestId, userId).isPresent()
                        ? new ConflictException(ErrorCode.INVALID_FRIENDSHIP_STATE,
                                "A solicitação não está pendente")
                        : new NotFoundException(ErrorCode.FRIENDSHIP_NOT_FOUND));
    }

    private UserEntity requireActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Nome de usuário inválido");
        }
        return normalized;
    }

    private FriendshipStatusView resolveStatus(UUID viewerId, UUID otherId, FriendshipEntity friendship) {
        if (otherId.equals(viewerId)) {
            return FriendshipStatusView.SELF;
        }
        if (friendship == null) {
            return FriendshipStatusView.NONE;
        }
        return switch (friendship.getStatus()) {
            case ACCEPTED -> FriendshipStatusView.FRIENDS;
            case PENDING -> friendship.getRequester().getId().equals(viewerId)
                    ? FriendshipStatusView.PENDING_OUTGOING
                    : FriendshipStatusView.PENDING_INCOMING;
            case DECLINED, CANCELLED -> FriendshipStatusView.NONE;
        };
    }

    private UserEntity counterpart(FriendshipEntity friendship, UUID viewerId) {
        return friendship.getRequester().getId().equals(viewerId)
                ? friendship.getAddressee() : friendship.getRequester();
    }

    private FriendRequestResponse toResponse(FriendshipEntity friendship, UUID viewerId) {
        boolean viewerIsRequester = friendship.getRequester().getId().equals(viewerId);
        return toResponse(friendship, viewerId,
                viewerIsRequester ? FriendRequestDirection.SENT : FriendRequestDirection.RECEIVED);
    }

    private FriendRequestResponse toResponse(FriendshipEntity friendship, UUID viewerId,
                                             FriendRequestDirection direction) {
        UserEntity other = counterpart(friendship, viewerId);
        return new FriendRequestResponse(friendship.getId(),
                new FriendRequestResponse.UserSummary(other.getId(), other.getUsername(), other.getDisplayName(),
                        other.getAvatarUrl()),
                direction, friendship.getStatus(), friendship.getCreatedAt());
    }

    private void notifyRequestSent(FriendshipEntity friendship, UserEntity actor) {
        String dedupeKey = "friend-request:" + friendship.getId() + ":" + friendship.getCreatedAt().toEpochMilli();
        notificationService.notify(friendship.getAddressee().getId(), NotificationType.FRIEND_REQUEST, null,
                dedupeKey, actorPayload(friendship, actor));
    }

    private void notifyAccepted(FriendshipEntity friendship, UserEntity actor) {
        String dedupeKey = "friend-accepted:" + friendship.getId();
        notificationService.notify(friendship.getRequester().getId(), NotificationType.FRIEND_REQUEST_ACCEPTED, null,
                dedupeKey, actorPayload(friendship, actor));
    }

    private Map<String, Object> actorPayload(FriendshipEntity friendship, UserEntity actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("friendshipId", friendship.getId().toString());
        payload.put("actorUserId", actor.getId().toString());
        payload.put("actorUsername", actor.getUsername());
        payload.put("actorDisplayName", actor.getDisplayName());
        payload.put("actorAvatarUrl", actor.getAvatarUrl());
        return payload;
    }

    private Map<String, Object> pairMetadata(FriendshipEntity friendship) {
        //---- Somente UUIDs: nunca username, e-mail ou qualquer PII.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requesterId", friendship.getRequester().getId().toString());
        metadata.put("addresseeId", friendship.getAddressee().getId().toString());
        return metadata;
    }
}
