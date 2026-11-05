package com.devrats.config.websocket;

import com.devrats.enums.UserStatus;
import com.devrats.repositories.UserRepository;
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
 * Autentica o frame STOMP CONNECT e autoriza os destinos do chat.
 *
 * <p>O handshake HTTP é liberado no SecurityConfig porque o browser não
 * consegue enviar Authorization no upgrade; a autenticação real acontece aqui.
 * SUBSCRIBE só pode ir para a fila privada do próprio usuário e SEND só pode
 * chamar o handler de digitação — destinos de broker diretos são rejeitados.</p>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CHAT_SUBSCRIPTION = "/user/queue/chat";
    private static final String TYPING_DESTINATION = "/app/chat.typing";

    private final JwtDecoder jwtDecoder;
    private final WebSocketSessionRegistry sessionRegistry;
    private final UserRepository userRepository;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder,
                                       WebSocketSessionRegistry sessionRegistry,
                                       UserRepository userRepository) {
        this.jwtDecoder = jwtDecoder;
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, org.springframework.messaging.MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
            return message;
        }

        if (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            ChatPrincipal principal = requirePrincipal(accessor);
            requireActiveAccount(principal.userId());
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                requireDestination(accessor, CHAT_SUBSCRIPTION);
            } else {
                requireDestination(accessor, TYPING_DESTINATION);
            }
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
            requireActiveAccount(userId);
            ChatPrincipal principal = new ChatPrincipal(userId, expiresAt);
            accessor.setUser(principal);
            sessionRegistry.authenticate(accessor.getSessionId(), principal);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new MessageDeliveryException("Token inválido");
        }
    }

    private ChatPrincipal requirePrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal principal) {
            return principal;
        }
        throw new MessageDeliveryException("Não autenticado");
    }

    private void requireActiveAccount(UUID userId) {
        //---- Token válido de conta removida/suspensa não pode abrir nem usar o canal.
        if (!userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)) {
            throw new MessageDeliveryException("Conta inativa");
        }
    }

    private void requireDestination(StompHeaderAccessor accessor, String expected) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.equals(expected)) {
            throw new MessageDeliveryException("Destino não autorizado");
        }
    }
}
