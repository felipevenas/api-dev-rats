package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.config.websocket.WebSocketSessionRegistry;
import com.devrats.dto.UserDataExportResponse;
import com.devrats.dto.chat.ChatAttachmentResponse;
import com.devrats.dto.chat.ChatAttachmentUrlResponse;
import com.devrats.dto.chat.ChatMessageResponse;
import com.devrats.dto.chat.ChatTypingResponse;
import com.devrats.dto.chat.ChatUserSummary;
import com.devrats.dto.chat.ConversationResponse;
import com.devrats.dto.chat.CreateConversationRequest;
import com.devrats.dto.chat.CursorPageResponse;
import com.devrats.dto.chat.MessagePreviewResponse;
import com.devrats.dto.chat.SendTextMessageRequest;
import com.devrats.entities.ChatAttachmentEntity;
import com.devrats.entities.ChatMessageEntity;
import com.devrats.entities.ConversationEntity;
import com.devrats.entities.ConversationReadEntity;
import com.devrats.entities.FriendshipEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.ChatEventType;
import com.devrats.enums.ChatMessageType;
import com.devrats.enums.ConversationType;
import com.devrats.enums.FriendshipStatus;
import com.devrats.enums.GroupStatus;
import com.devrats.enums.MemberStatus;
import com.devrats.enums.PresenceStatus;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.ForbiddenException;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.ChatAttachmentRepository;
import com.devrats.repositories.ChatMessageRepository;
import com.devrats.repositories.ConversationReadRepository;
import com.devrats.repositories.ConversationRepository;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.ChatRateLimitService;
import com.devrats.services.ChatService;
import com.devrats.services.GroupAccessService;
import com.devrats.services.PresenceService;
import com.devrats.services.chat.ChatAttachmentCommand;
import com.devrats.services.chat.ChatAttachmentPolicy;
import com.devrats.services.chat.ChatEvent;
import com.devrats.services.chat.ChatMessageResult;
import com.devrats.services.storage.ChatAttachmentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementação do chat.
 *
 * <p>Mensagens e conversas são sempre validadas contra o grafo social atual:
 * amizade ACCEPTED para DIRECT e membro ACTIVE para GROUP. O histórico não é
 * cacheado no servidor; o PostgreSQL é a fonte da verdade.</p>
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final int PREVIEW_MAX = 120;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ConversationReadRepository readRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAccessService groupAccessService;
    private final PresenceService presenceService;
    private final ChatAttachmentStorage storage;
    private final ChatAttachmentPolicy attachmentPolicy;
    private final ChatRateLimitService rateLimitService;
    private final ChatTypingServiceImpl typingService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties.Chat properties;
    private final Clock clock;

    public ChatServiceImpl(ConversationRepository conversationRepository,
                           ChatMessageRepository messageRepository,
                           ChatAttachmentRepository attachmentRepository,
                           ConversationReadRepository readRepository,
                           FriendshipRepository friendshipRepository,
                           UserRepository userRepository,
                           GroupMemberRepository groupMemberRepository,
                           GroupAccessService groupAccessService,
                           PresenceService presenceService,
                           ChatAttachmentStorage storage,
                           ChatAttachmentPolicy attachmentPolicy,
                           ChatRateLimitService rateLimitService,
                           ChatTypingServiceImpl typingService,
                           WebSocketSessionRegistry sessionRegistry,
                           ApplicationEventPublisher eventPublisher,
                           AppProperties appProperties,
                           Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.readRepository = readRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupAccessService = groupAccessService;
        this.presenceService = presenceService;
        this.storage = storage;
        this.attachmentPolicy = attachmentPolicy;
        this.rateLimitService = rateLimitService;
        this.typingService = typingService;
        this.sessionRegistry = sessionRegistry;
        this.eventPublisher = eventPublisher;
        this.properties = appProperties.getChat();
        this.clock = clock;
    }

    //---- Leitura -------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(UUID userId, Pageable pageable) {
        Page<ConversationEntity> page = conversationRepository.findVisibleForUser(userId,
                ConversationType.DIRECT, ConversationType.GROUP, GroupStatus.ACTIVE,
                FriendshipStatus.ACCEPTED, MemberStatus.ACTIVE, pageable);
        List<ConversationEntity> conversations = page.getContent();
        if (conversations.isEmpty()) {
            return page.map(conversation -> toConversation(conversation, userId, null, 0, Map.of()));
        }
        List<UUID> ids = conversations.stream().map(ConversationEntity::getId).toList();
        Map<UUID, ChatMessageEntity> lastMessages = messageRepository.findLastMessages(ids).stream()
                .collect(Collectors.toMap(message -> message.getConversation().getId(), Function.identity(),
                        (first, second) -> first));
        Map<UUID, Long> unread = unreadByConversation(userId, ids);
        Set<UUID> peerIds = conversations.stream()
                .filter(conversation -> conversation.getType() == ConversationType.DIRECT)
                .map(conversation -> peer(conversation, userId).getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, PresenceStatus> presence = presenceService.statusesOf(peerIds);
        return page.map(conversation -> toConversation(conversation, userId,
                lastMessages.get(conversation.getId()), unread.getOrDefault(conversation.getId(), 0L), presence));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID userId, UUID conversationId) {
        ConversationEntity conversation = requireAccess(userId, conversationId);
        return toConversationWithState(conversation, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ChatMessageResponse> listMessages(UUID userId, UUID conversationId,
                                                                String cursor, int limit) {
        ConversationEntity conversation = requireAccess(userId, conversationId);
        int pageSize = limit <= 0 ? properties.getPageSize() : Math.min(limit, properties.getMaxPageSize());
        int fetch = pageSize + 1;
        List<ChatMessageEntity> messages;
        if (cursor == null || cursor.isBlank()) {
            messages = messageRepository.findFirstPage(conversationId, fetch);
        } else {
            MessageCursor decoded = decodeCursor(cursor);
            messages = messageRepository.findBefore(conversationId, decoded.createdAt(), decoded.id(), fetch);
        }
        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(0, pageSize);
        }
        String nextCursor = messages.isEmpty() ? null : encodeCursor(messages.get(messages.size() - 1));
        List<ChatMessageResponse> items = toMessageResponses(messages);
        return new CursorPageResponse<>(items, nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return messageRepository.countUnreadTotal(userId);
    }

    //---- Conversas -----------------------------------------------------------------------------

    @Override
    @Transactional
    public ConversationResponse resolveConversation(UUID userId, CreateConversationRequest request) {
        if (request.type() == ConversationType.DIRECT) {
            return resolveDirect(userId, request.userId());
        }
        if (request.type() == ConversationType.GROUP) {
            return resolveGroup(userId, request.groupId());
        }
        throw new ValidationException("Tipo de conversa inválido");
    }

    private ConversationResponse resolveDirect(UUID userId, UUID otherUserId) {
        if (otherUserId == null) {
            throw new ValidationException("Informe o usuário da conversa");
        }
        if (userId.equals(otherUserId)) {
            throw new ValidationException("Você não pode conversar consigo mesmo");
        }
        UserEntity current = requireActiveUser(userId);
        UserEntity other = requireActiveUser(otherUserId);
        friendshipRepository.findPair(userId, otherUserId)
                .filter(pair -> pair.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        String directKey = directKey(userId, otherUserId);
        Optional<ConversationEntity> existing = conversationRepository.findByDirectKey(directKey);
        if (existing.isPresent()) {
            return toConversationWithState(existing.get(), userId);
        }
        Instant now = clock.instant();
        //---- ON CONFLICT DO NOTHING evita marcar a transação como rollback-only em corrida.
        conversationRepository.insertDirectIfAbsent(UUID.randomUUID(), current.getId(), other.getId(),
                directKey, now);
        ConversationEntity conversation = conversationRepository.findByDirectKey(directKey)
                .orElseThrow(() -> new ConflictException(ErrorCode.DATA_CONFLICT,
                        "Não foi possível criar a conversa"));
        createReadRow(conversation, current, now);
        return toConversationWithState(conversation, userId);
    }

    private ConversationResponse resolveGroup(UUID userId, UUID groupId) {
        if (groupId == null) {
            throw new ValidationException("Informe o grupo da conversa");
        }
        GroupMemberEntity member = groupAccessService.requireMember(groupId, userId);
        GroupEntity group = member.getGroup();
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.INVALID_CONVERSATION_STATE, "Grupo não está ativo");
        }
        Optional<ConversationEntity> existing = conversationRepository.findByGroupId(groupId);
        if (existing.isPresent()) {
            return toConversationWithState(existing.get(), userId);
        }
        Instant now = clock.instant();
        conversationRepository.insertGroupIfAbsent(UUID.randomUUID(), groupId, now);
        ConversationEntity conversation = conversationRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ConflictException(ErrorCode.DATA_CONFLICT,
                        "Não foi possível criar a conversa"));
        createReadRow(conversation, member.getUser(), now);
        return toConversationWithState(conversation, userId);
    }

    @Override
    @Transactional
    public void createGroupConversation(UUID groupId) {
        if (conversationRepository.findByGroupId(groupId).isPresent()) {
            return;
        }
        groupAccessService.getGroup(groupId);
        Instant now = clock.instant();
        int inserted = conversationRepository.insertGroupIfAbsent(UUID.randomUUID(), groupId, now);
        if (inserted == 0) {
            return;
        }
        ConversationEntity conversation = conversationRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ConflictException(ErrorCode.DATA_CONFLICT,
                        "Não foi possível criar a conversa"));
        groupMemberRepository.findAllByGroupIdAndStatusOrderByJoinedAtAsc(groupId, MemberStatus.ACTIVE)
                .forEach(member -> createReadRow(conversation, member.getUser(), now));
    }

    //---- Mensagens -----------------------------------------------------------------------------

    @Override
    @Transactional
    public ChatMessageResult sendText(UUID userId, UUID conversationId, SendTextMessageRequest request) {
        ConversationEntity conversation = requireAccess(userId, conversationId);
        if (request.type() != ChatMessageType.TEXT) {
            throw new ValidationException("Use o endpoint de anexos para este tipo de mensagem");
        }
        String body = normalizeBody(request.body(), true);
        Optional<ChatMessageEntity> existing = messageRepository
                .findByConversationIdAndSenderIdAndClientMessageId(conversationId, userId, request.clientMessageId());
        if (existing.isPresent()) {
            return new ChatMessageResult(toMessageResponse(existing.get()), false);
        }
        rateLimitService.checkMessage(userId);
        UserEntity sender = requireActiveUser(userId);
        Instant now = clock.instant();
        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setType(ChatMessageType.TEXT);
        message.setBody(body);
        message.setClientMessageId(request.clientMessageId());
        message.setCreatedAt(now);
        messageRepository.save(message);
        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);
        ChatMessageResponse response = toMessageResponse(message, List.of(), sender);
        publish(ChatEventType.MESSAGE_CREATED, conversation, recipients(conversation), response, null, null, null);
        return new ChatMessageResult(response, true);
    }

    @Override
    @Transactional
    public ChatMessageResult sendAttachment(UUID userId, UUID conversationId, ChatAttachmentCommand command) {
        ConversationEntity conversation = requireAccess(userId, conversationId);
        if (command.type() == null || command.type() == ChatMessageType.TEXT) {
            throw new ValidationException("Use o endpoint de texto para mensagens TEXT");
        }
        Optional<ChatMessageEntity> existing = messageRepository
                .findByConversationIdAndSenderIdAndClientMessageId(conversationId, userId, command.clientMessageId());
        if (existing.isPresent()) {
            return new ChatMessageResult(toMessageResponse(existing.get()), false);
        }
        if (!storage.isAvailable()) {
            throw new DomainException(ErrorCode.CHAT_STORAGE_UNAVAILABLE,
                    "Armazenamento de anexos não configurado");
        }
        rateLimitService.checkUpload(userId);
        ChatAttachmentPolicy.ValidatedAttachment validated =
                attachmentPolicy.validate(command.type(), command.file(), command.durationSeconds());
        String caption = normalizeBody(command.body(), false);
        UserEntity sender = requireActiveUser(userId);
        Instant now = clock.instant();

        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setType(command.type());
        message.setBody(caption);
        message.setClientMessageId(command.clientMessageId());
        message.setCreatedAt(now);
        messageRepository.saveAndFlush(message);

        String objectKey = objectKey(conversationId, message.getId(), validated.extension(), now);
        storage.upload(objectKey, validated.content(), validated.contentType());
        registerRollbackCleanup(objectKey);

        ChatAttachmentEntity attachment = new ChatAttachmentEntity();
        attachment.setMessage(message);
        attachment.setKind(validated.kind());
        attachment.setObjectKey(objectKey);
        attachment.setOriginalName(validated.originalName());
        attachment.setContentType(validated.contentType());
        attachment.setSizeBytes(validated.sizeBytes());
        attachment.setWidth(validated.width());
        attachment.setHeight(validated.height());
        attachment.setDurationSeconds(validated.durationSeconds());
        attachmentRepository.save(attachment);

        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);
        ChatMessageResponse response = toMessageResponse(message, List.of(attachment), sender);
        publish(ChatEventType.MESSAGE_CREATED, conversation, recipients(conversation), response, null, null, null);
        return new ChatMessageResult(response, true);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID conversationId, UUID lastMessageId) {
        requireAccess(userId, conversationId);
        ChatMessageEntity message = messageRepository.findByIdAndConversationId(lastMessageId, conversationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MESSAGE_NOT_FOUND));
        ConversationReadEntity read = readRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseGet(() -> {
                    ConversationReadEntity created = new ConversationReadEntity();
                    created.setConversation(message.getConversation());
                    created.setUser(requireActiveUser(userId));
                    return created;
                });
        if (read.getLastReadAt() != null && read.getLastReadAt().isAfter(message.getCreatedAt())) {
            return;
        }
        read.setLastReadAt(message.getCreatedAt());
        read.setLastReadMessageId(message.getId());
        readRepository.save(read);
    }

    @Override
    @Transactional
    public void deleteMessage(UUID userId, UUID messageId) {
        ChatMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MESSAGE_NOT_FOUND));
        ConversationEntity conversation = message.getConversation();
        requireAccess(userId, conversation.getId());
        if (!message.getSender().getId().equals(userId)) {
            throw new ForbiddenException("Você só pode apagar suas próprias mensagens");
        }
        if (message.getDeletedAt() != null) {
            return;
        }
        List<String> keys = attachmentRepository.findAllByMessageId(messageId).stream()
                .map(ChatAttachmentEntity::getObjectKey)
                .toList();
        attachmentRepository.deleteAll(attachmentRepository.findAllByMessageId(messageId));
        message.setBody(null);
        message.setDeletedAt(clock.instant());
        messageRepository.save(message);
        afterCommit(() -> keys.forEach(storage::delete));
        publish(ChatEventType.MESSAGE_DELETED, conversation, recipients(conversation), null, messageId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatAttachmentUrlResponse refreshAttachmentUrl(UUID userId, UUID attachmentId) {
        ChatAttachmentEntity attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ATTACHMENT_NOT_FOUND));
        ChatMessageEntity message = attachment.getMessage();
        requireAccess(userId, message.getConversation().getId());
        if (message.getDeletedAt() != null) {
            throw new NotFoundException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        if (!storage.isAvailable()) {
            throw new DomainException(ErrorCode.CHAT_STORAGE_UNAVAILABLE,
                    "Armazenamento de anexos não configurado");
        }
        String url = storage.presignGet(attachment.getObjectKey(), properties.getAttachmentUrlTtl(),
                contentDisposition(attachment), attachment.getContentType());
        return new ChatAttachmentUrlResponse(url, clock.instant().plus(properties.getAttachmentUrlTtl()));
    }

    //---- Tempo real ----------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public void typing(UUID userId, UUID conversationId) {
        ConversationEntity conversation = requireAccess(userId, conversationId);
        if (!typingService.allow(userId, conversationId)) {
            return;
        }
        UserEntity user = requireActiveUser(userId);
        List<UUID> target = recipients(conversation).stream()
                .filter(id -> !id.equals(userId))
                .toList();
        ChatTypingResponse typing = new ChatTypingResponse(userId, user.getDisplayName(),
                clock.instant().plusSeconds(4));
        publish(ChatEventType.TYPING, conversation, target, null, null, typing, null);
    }

    //---- Ciclo de vida de grupo/amizade --------------------------------------------------------

    @Override
    @Transactional
    public void handleGroupDeletion(UUID groupId) {
        conversationRepository.findByGroupId(groupId).ifPresent(conversation -> {
            List<String> keys = attachmentRepository.findObjectKeysByConversationId(conversation.getId());
            afterCommit(() -> keys.forEach(storage::delete));
            publish(ChatEventType.CONVERSATION_REVOKED, conversation, recipients(conversation),
                    null, null, null, "GROUP_DELETED");
        });
    }

    @Override
    @Transactional(readOnly = true)
    public void revokeGroupAccess(UUID groupId, UUID userId) {
        conversationRepository.findByGroupId(groupId).ifPresent(conversation ->
                publish(ChatEventType.CONVERSATION_REVOKED, conversation, List.of(userId),
                        null, null, null, "GROUP_ACCESS_REVOKED"));
    }

    @Override
    @Transactional(readOnly = true)
    public void revokeDirectAccess(UUID userId, UUID otherUserId) {
        conversationRepository.findByDirectKey(directKey(userId, otherUserId)).ifPresent(conversation ->
                publish(ChatEventType.CONVERSATION_REVOKED, conversation, List.of(userId, otherUserId),
                        null, null, null, "FRIENDSHIP_ENDED"));
    }

    @Override
    public void closeSessions(UUID userId) {
        sessionRegistry.closeUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDataExportResponse.ChatMessageExport> exportSentMessages(UUID userId) {
        List<ChatMessageEntity> messages = messageRepository.findAllBySenderIdOrderByCreatedAtDesc(userId);
        if (messages.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = messages.stream().map(ChatMessageEntity::getId).toList();
        Map<UUID, List<ChatAttachmentEntity>> attachments = new HashMap<>();
        //---- Exportação pode ter milhares de mensagens; limita o IN para não estourar o driver.
        for (int start = 0; start < ids.size(); start += 200) {
            List<UUID> batch = ids.subList(start, Math.min(start + 200, ids.size()));
            attachmentRepository.findAllByMessageIdIn(batch).forEach(attachment ->
                    attachments.computeIfAbsent(attachment.getMessage().getId(), ignored -> new ArrayList<>())
                            .add(attachment));
        }
        return messages.stream()
                .map(message -> new UserDataExportResponse.ChatMessageExport(
                        message.getConversation().getId(),
                        message.getType(),
                        message.getBody(),
                        message.getCreatedAt(),
                        message.getDeletedAt() != null,
                        attachments.getOrDefault(message.getId(), List.of()).stream()
                                .map(attachment -> new UserDataExportResponse.ChatAttachmentExport(
                                        attachment.getOriginalName(), attachment.getContentType(),
                                        attachment.getSizeBytes()))
                                .toList()))
                .toList();
    }

    //---- Internos ------------------------------------------------------------------------------

    private ConversationEntity requireAccess(UUID userId, UUID conversationId) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (conversation.getType() == ConversationType.DIRECT) {
            if (!conversation.getUserA().getId().equals(userId)
                    && !conversation.getUserB().getId().equals(userId)) {
                throw new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND);
            }
            UUID peerId = peer(conversation, userId).getId();
            boolean friends = friendshipRepository.findPair(userId, peerId)
                    .filter(pair -> pair.getStatus() == FriendshipStatus.ACCEPTED)
                    .isPresent();
            if (!friends) {
                throw new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND);
            }
        } else {
            GroupEntity group = conversation.getGroup();
            if (group == null || group.getStatus() != GroupStatus.ACTIVE) {
                throw new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND);
            }
            groupAccessService.requireMember(group.getId(), userId);
        }
        return conversation;
    }

    private UserEntity requireActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private String directKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private UserEntity peer(ConversationEntity conversation, UUID userId) {
        return conversation.getUserA().getId().equals(userId)
                ? conversation.getUserB() : conversation.getUserA();
    }

    private void createReadRow(ConversationEntity conversation, UserEntity user, Instant lastReadAt) {
        readRepository.insertIfAbsent(UUID.randomUUID(), conversation.getId(), user.getId(),
                lastReadAt, clock.instant());
    }

    private Map<UUID, Long> unreadByConversation(UUID userId, List<UUID> conversationIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadByConversation(userId, conversationIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private ConversationResponse toConversationWithState(ConversationEntity conversation, UUID userId) {
        ChatMessageEntity last = messageRepository.findLastMessages(List.of(conversation.getId())).stream()
                .findFirst().orElse(null);
        long unread = unreadByConversation(userId, List.of(conversation.getId()))
                .getOrDefault(conversation.getId(), 0L);
        Map<UUID, PresenceStatus> presence = conversation.getType() == ConversationType.DIRECT
                ? Map.of(peer(conversation, userId).getId(), presenceService.statusOf(peer(conversation, userId).getId()))
                : Map.of();
        return toConversation(conversation, userId, last, unread, presence);
    }

    private ConversationResponse toConversation(ConversationEntity conversation, UUID userId,
                                                ChatMessageEntity lastMessage, long unread,
                                                Map<UUID, PresenceStatus> presence) {
        if (conversation.getType() == ConversationType.DIRECT) {
            UserEntity peer = peer(conversation, userId);
            return new ConversationResponse(conversation.getId(), conversation.getType(),
                    peer.getDisplayName(), peer.getAvatarUrl(), null, toUserSummary(peer),
                    presence.getOrDefault(peer.getId(), PresenceStatus.OFFLINE),
                    lastMessage == null ? null : toPreview(lastMessage), unread,
                    conversation.getLastMessageAt(), conversation.getCreatedAt());
        }
        GroupEntity group = conversation.getGroup();
        return new ConversationResponse(conversation.getId(), conversation.getType(),
                group.getName(), null, group.getId(), null, null,
                lastMessage == null ? null : toPreview(lastMessage), unread,
                conversation.getLastMessageAt(), conversation.getCreatedAt());
    }

    private MessagePreviewResponse toPreview(ChatMessageEntity message) {
        boolean deleted = message.getDeletedAt() != null;
        String preview;
        if (deleted) {
            preview = "Mensagem apagada";
        } else {
            preview = switch (message.getType()) {
                case TEXT -> truncate(message.getBody());
                case IMAGE -> "Imagem";
                case VOICE -> "Mensagem de voz";
                case FILE -> "Documento";
            };
        }
        return new MessagePreviewResponse(message.getId(), message.getSender().getId(), message.getType(),
                preview, message.getCreatedAt(), deleted);
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= PREVIEW_MAX ? body : body.substring(0, PREVIEW_MAX - 1) + "…";
    }

    private List<ChatMessageResponse> toMessageResponses(List<ChatMessageEntity> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<UUID> messageIds = messages.stream().map(ChatMessageEntity::getId).toList();
        Map<UUID, List<ChatAttachmentEntity>> attachments = attachmentRepository.findAllByMessageIdIn(messageIds)
                .stream().collect(Collectors.groupingBy(attachment -> attachment.getMessage().getId()));
        Set<UUID> senderIds = messages.stream().map(message -> message.getSender().getId())
                .collect(Collectors.toSet());
        Map<UUID, UserEntity> senders = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return messages.stream()
                .map(message -> toMessageResponse(message,
                        attachments.getOrDefault(message.getId(), List.of()),
                        senders.getOrDefault(message.getSender().getId(), message.getSender())))
                .toList();
    }

    private ChatMessageResponse toMessageResponse(ChatMessageEntity message) {
        return toMessageResponse(message, attachmentRepository.findAllByMessageId(message.getId()),
                message.getSender());
    }

    private ChatMessageResponse toMessageResponse(ChatMessageEntity message, List<ChatAttachmentEntity> attachments,
                                                  UserEntity sender) {
        boolean deleted = message.getDeletedAt() != null;
        List<ChatAttachmentResponse> responses = deleted ? List.of() : attachments.stream()
                .map(this::toAttachmentResponse)
                .toList();
        return new ChatMessageResponse(message.getId(), message.getConversation().getId(),
                toUserSummary(sender), message.getType(), deleted ? null : message.getBody(),
                message.getClientMessageId(), message.getCreatedAt(), deleted, responses);
    }

    private ChatAttachmentResponse toAttachmentResponse(ChatAttachmentEntity attachment) {
        String url = null;
        Instant expiresAt = null;
        if (storage.isAvailable()) {
            try {
                url = storage.presignGet(attachment.getObjectKey(), properties.getAttachmentUrlTtl(),
                        contentDisposition(attachment), attachment.getContentType());
                expiresAt = clock.instant().plus(properties.getAttachmentUrlTtl());
            } catch (RuntimeException exception) {
                //---- Um anexo indisponível não pode derrubar a página inteira.
                log.warn("Falha ao assinar anexo de chat: attachmentId={}", attachment.getId());
            }
        }
        return new ChatAttachmentResponse(attachment.getId(), attachment.getKind(),
                attachment.getOriginalName(), attachment.getContentType(), attachment.getSizeBytes(),
                attachment.getWidth(), attachment.getHeight(), attachment.getDurationSeconds(), url, expiresAt);
    }

    private String contentDisposition(ChatAttachmentEntity attachment) {
        String name = attachment.getOriginalName() == null ? "arquivo" : attachment.getOriginalName();
        name = name.replace("\"", "").replace("\r", "").replace("\n", "");
        String disposition = attachment.getKind() == com.devrats.enums.AttachmentKind.FILE
                ? "attachment" : "inline";
        //---- RFC 6266: fallback ASCII + filename* percent-encoded para nomes não-ASCII.
        String fallback = name.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return disposition + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    private ChatUserSummary toUserSummary(UserEntity user) {
        return new ChatUserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }

    private List<UUID> recipients(ConversationEntity conversation) {
        if (conversation.getType() == ConversationType.DIRECT) {
            return List.of(conversation.getUserA().getId(), conversation.getUserB().getId());
        }
        return groupMemberRepository.findAllByGroupIdAndStatusOrderByJoinedAtAsc(
                        conversation.getGroup().getId(), MemberStatus.ACTIVE).stream()
                .map(member -> member.getUser().getId())
                .distinct()
                .toList();
    }

    private void publish(ChatEventType type, ConversationEntity conversation, List<UUID> recipients,
                         ChatMessageResponse message, UUID messageId, ChatTypingResponse typing, String reason) {
        if (recipients.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new ChatEvent(UUID.randomUUID().toString(), type, conversation.getId(),
                recipients, message, messageId, typing, reason, clock.instant()));
    }

    private String normalizeBody(String body, boolean required) {
        if (body == null) {
            if (required) {
                throw new ValidationException("A mensagem não pode estar vazia");
            }
            return null;
        }
        String value = body.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (value.isEmpty()) {
            if (required) {
                throw new ValidationException("A mensagem não pode estar vazia");
            }
            return null;
        }
        if (value.length() > properties.getMaxTextChars()) {
            throw new ValidationException("A mensagem deve ter no máximo " + properties.getMaxTextChars() + " caracteres");
        }
        return value;
    }

    private String objectKey(UUID conversationId, UUID messageId, String extension, Instant now) {
        String prefix = properties.getStorage().getKeyPrefix();
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "chat" : stripSlashes(prefix);
        ZonedDateTime date = ZonedDateTime.ofInstant(now, ZoneOffset.UTC);
        return normalizedPrefix + "/" + conversationId + "/" + KEY_DATE.format(date) + "/" + messageId + "/"
                + UUID.randomUUID() + "." + extension;
    }

    private static String stripSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String encodeCursor(ChatMessageEntity message) {
        String raw = message.getCreatedAt() + "|" + message.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private MessageCursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator <= 0) {
                throw new IllegalArgumentException("cursor");
            }
            return new MessageCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException exception) {
            throw new ValidationException("Cursor de mensagens inválido");
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storage.delete(objectKey);
                }
            }
        });
    }

    private record MessageCursor(Instant createdAt, UUID id) {
    }
}
