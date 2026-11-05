package com.devrats.controllers;

import com.devrats.config.websocket.ChatPrincipal;
import com.devrats.dto.chat.TypingRequest;
import com.devrats.services.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;

/**
 * Frame STOMP de "digitando". O envio de mensagens usa REST para preservar
 * idempotência, validação e ProblemDetail; o WebSocket é o canal de eventos.
 */
@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload String payload, Principal principal) {
        if (!(principal instanceof ChatPrincipal chatPrincipal)) {
            return;
        }
        try {
            TypingRequest request = objectMapper.readValue(payload, TypingRequest.class);
            chatService.typing(chatPrincipal.userId(), request.conversationId());
        } catch (RuntimeException exception) {
            //---- Evento efêmero: payload inválido ou acesso revogado não derruba a sessão.
            log.debug("Evento de digitação ignorado para userId={}", chatPrincipal.userId());
        }
    }
}
