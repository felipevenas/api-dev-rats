package com.devrats.controllers;

import com.devrats.dto.PageResponse;
import com.devrats.dto.chat.ChatAttachmentUrlResponse;
import com.devrats.dto.chat.ChatMessageResponse;
import com.devrats.dto.chat.ConversationResponse;
import com.devrats.dto.chat.CreateConversationRequest;
import com.devrats.dto.chat.CursorPageResponse;
import com.devrats.dto.chat.MarkReadRequest;
import com.devrats.dto.chat.SendTextMessageRequest;
import com.devrats.enums.ChatMessageType;
import com.devrats.services.ChatService;
import com.devrats.services.chat.ChatAttachmentCommand;
import com.devrats.services.chat.ChatMessageResult;
import com.devrats.utils.AuthenticatedUserHelper;
import com.devrats.utils.PageHelper;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Chat privado e de grupo. O usuário sempre vem do JWT; o corpo nunca contém
 * remetente. Todas as respostas são privadas ({@code Cache-Control: no-store}).
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<PageResponse<ConversationResponse>> listConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(PageHelper.toResponse(chatService.listConversations(userId, pageable(page, size))));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> resolveConversation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateConversationRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(chatService.resolveConversation(userId, request));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(chatService.getConversation(userId, conversationId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<CursorPageResponse<ChatMessageResponse>> listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") int limit) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(chatService.listMessages(userId, conversationId, before, limit));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ChatMessageResponse> sendText(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendTextMessageRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        ChatMessageResult result = chatService.sendText(userId, conversationId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.message());
    }

    @PostMapping(path = "/conversations/{conversationId}/messages/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponse> sendAttachment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam("clientMessageId") UUID clientMessageId,
            @RequestParam("type") ChatMessageType type,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestParam("file") MultipartFile file) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        ChatMessageResult result = chatService.sendAttachment(userId, conversationId,
                new ChatAttachmentCommand(clientMessageId, type, body, durationSeconds, file));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.message());
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID conversationId,
                                         @Valid @RequestBody MarkReadRequest request) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        chatService.markRead(userId, conversationId, request.lastMessageId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID messageId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        chatService.deleteMessage(userId, messageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(Map.of("total", chatService.countUnread(userId)));
    }

    @GetMapping("/attachments/{attachmentId}/url")
    public ResponseEntity<ChatAttachmentUrlResponse> attachmentUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID attachmentId) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(chatService.refreshAttachmentUrl(userId, attachmentId));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
