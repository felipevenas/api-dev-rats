package com.devrats.services;

import com.devrats.dto.UserDataExportResponse;
import com.devrats.dto.chat.ChatAttachmentUrlResponse;
import com.devrats.dto.chat.ChatMessageResponse;
import com.devrats.dto.chat.ConversationResponse;
import com.devrats.dto.chat.CreateConversationRequest;
import com.devrats.dto.chat.CursorPageResponse;
import com.devrats.dto.chat.SendTextMessageRequest;
import com.devrats.services.chat.ChatAttachmentCommand;
import com.devrats.services.chat.ChatMessageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Casos de uso do chat.
 *
 * <p>Toda operação valida a participação no {@code WHERE}/serviço: quem não é
 * amigo ou membro ativo recebe 404, nunca 403. Conteúdo de mensagem nunca é
 * escrito em log ou auditoria.</p>
 */
public interface ChatService {

    Page<ConversationResponse> listConversations(UUID userId, Pageable pageable);

    ConversationResponse getConversation(UUID userId, UUID conversationId);

    ConversationResponse resolveConversation(UUID userId, CreateConversationRequest request);

    CursorPageResponse<ChatMessageResponse> listMessages(UUID userId, UUID conversationId, String cursor, int limit);

    ChatMessageResult sendText(UUID userId, UUID conversationId, SendTextMessageRequest request);

    ChatMessageResult sendAttachment(UUID userId, UUID conversationId, ChatAttachmentCommand command);

    void markRead(UUID userId, UUID conversationId, UUID lastMessageId);

    void deleteMessage(UUID userId, UUID messageId);

    long countUnread(UUID userId);

    ChatAttachmentUrlResponse refreshAttachmentUrl(UUID userId, UUID attachmentId);

    void typing(UUID userId, UUID conversationId);

    void createGroupConversation(UUID groupId);

    void handleGroupDeletion(UUID groupId);

    void revokeGroupAccess(UUID groupId, UUID userId);

    void revokeDirectAccess(UUID userId, UUID otherUserId);

    void closeSessions(UUID userId);

    List<UserDataExportResponse.ChatMessageExport> exportSentMessages(UUID userId);
}
