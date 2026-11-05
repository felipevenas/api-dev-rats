package com.devrats.config.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Autentica o frame STOMP CONNECT com o access token JWT.
 *
 * <p>O handshake HTTP é liberado no SecurityConfig porque o browser não
 * consegue enviar Authorization no upgrade; a autenticação real acontece aqui,
 * antes de qualquer SUBSCRIBE/SEND.</p>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final WebSocketSessionRegistry sessionRegistry;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, WebSocketSessionRegistry sessionRegistry) {
        this.jwtDecoder = jwtDecoder;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, org.springframework.messaging.MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (requiresAuthentication(accessor.getCommand()) && accessor.getUser() == null) {
            throw new MessageDeliveryException("Não autenticado");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Token ausente");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Jwt jwt = jwtDecoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                throw new MessageDeliveryException("Token sem expiração");
            }
            ChatPrincipal principal = new ChatPrincipal(userId, expiresAt);
            accessor.setUser(principal);
            sessionRegistry.authenticate(accessor.getSessionId(), principal);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new MessageDeliveryException("Token inválido");
        }
    }

    private boolean requiresAuthentication(StompCommand command) {
        return StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command);
    }
}
