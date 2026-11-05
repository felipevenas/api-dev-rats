package com.devrats.services.chat;

import com.devrats.dto.chat.ChatEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Entrega os eventos de chat pelo WebSocket depois do commit do banco.
 *
 * <p>O corpo é serializado manualmente e enviado como String para não depender
 * do conversor Jackson do broker (o projeto usa Jackson 3, enquanto o broker
 * pode registrar o conversor legado). O cliente faz o parse do JSON.</p>
 */
@Component
public class ChatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatEventPublisher(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(ChatEvent event) {
        if (event.recipients() == null || event.recipients().isEmpty()) {
            return;
        }
        ChatEventResponse response = new ChatEventResponse(
                event.eventId(),
                event.type(),
                event.conversationId(),
                event.message(),
                event.messageId(),
                event.typing(),
                event.reason(),
                event.occurredAt());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(response);
        } catch (RuntimeException exception) {
            log.warn("Falha ao serializar evento de chat: type={}", event.type(), exception);
            return;
        }
        for (UUID recipient : event.recipients()) {
            messagingTemplate.convertAndSendToUser(recipient.toString(), "/queue/chat", payload);
        }
    }
}
