package com.devrats.config.websocket;

import com.devrats.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro local de sessões WebSocket.
 *
 * <p>É memória da instância: em múltiplas réplicas, presença e fechamento de
 * sessão precisam de Redis. A expiração do JWT é aplicada no servidor para que
 * um cliente malicioso não continue recebendo mensagens após o token vencer.</p>
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ChatPrincipal> principals = new ConcurrentHashMap<>();
    private final int maxSessionsPerUser;

    public WebSocketSessionRegistry(AppProperties properties) {
        this.maxSessionsPerUser = properties.getChat().getMaxSessionsPerUser();
    }

    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregisterSession(String sessionId) {
        sessions.remove(sessionId);
        principals.remove(sessionId);
    }

    public void authenticate(String sessionId, ChatPrincipal principal) {
        if (sessionId == null) {
            throw new MessageDeliveryException("Sessão inválida");
        }
        long current = principals.values().stream()
                .filter(value -> value.userId().equals(principal.userId()))
                .count();
        if (current >= maxSessionsPerUser) {
            throw new MessageDeliveryException("Limite de conexões simultâneas atingido");
        }
        principals.put(sessionId, principal);
    }

    public int activeSessions() {
        return principals.size();
    }

    public void closeUser(UUID userId) {
        principals.entrySet().stream()
                .filter(entry -> entry.getValue().userId().equals(userId))
                .forEach(entry -> close(entry.getKey(), 4003, "Conta encerrada"));
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void closeExpired() {
        Instant now = Instant.now();
        principals.entrySet().stream()
                .filter(entry -> !entry.getValue().expiresAt().isAfter(now))
                .forEach(entry -> close(entry.getKey(), 4001, "Token expirado"));
    }

    private void close(String sessionId, int code, String reason) {
        WebSocketSession session = sessions.get(sessionId);
        principals.remove(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.close(new CloseStatus(code, reason));
        } catch (IOException exception) {
            log.debug("Falha ao fechar sessão WebSocket: sessionId={}", sessionId, exception);
        }
    }
}
