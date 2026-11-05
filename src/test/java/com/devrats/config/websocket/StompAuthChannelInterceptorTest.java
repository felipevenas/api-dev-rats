package com.devrats.config.websocket;

import com.devrats.config.AppProperties;
import com.devrats.enums.UserStatus;
import com.devrats.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry(new AppProperties());
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(jwtDecoder, registry, userRepository);

    @Test
    void shouldAuthenticateConnectFrameWithBearerToken() {
        UUID userId = UUID.randomUUID();
        when(jwtDecoder.decode("token")).thenReturn(jwt(userId));
        when(userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(true);
        StompHeaderAccessor accessor = connectAccessor("Bearer token");

        interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class));

        assertThat(accessor.getUser()).isInstanceOf(ChatPrincipal.class);
        assertThat(((ChatPrincipal) accessor.getUser()).userId()).isEqualTo(userId);
        assertThat(registry.activeSessions()).isEqualTo(1);
    }

    @Test
    void shouldRejectConnectWithoutToken() {
        StompHeaderAccessor accessor = connectAccessor(null);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor),
                mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void shouldRejectConnectWithInvalidToken() {
        when(jwtDecoder.decode("bad")).thenThrow(new JwtException("invalid"));
        StompHeaderAccessor accessor = connectAccessor("Bearer bad");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor),
                mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void shouldRejectConnectWhenAccountIsInactive() {
        UUID userId = UUID.randomUUID();
        when(jwtDecoder.decode("token")).thenReturn(jwt(userId));
        when(userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(false);
        StompHeaderAccessor accessor = connectAccessor("Bearer token");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor),
                mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void shouldRejectSendWithoutPrincipal() {
        StompHeaderAccessor accessor = commandAccessor(StompCommand.SEND, "/app/chat.typing", null);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor),
                mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void shouldAllowOnlyChatSubscription() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(true);

        assertThatCode(() -> interceptor.preSend(
                message(commandAccessor(StompCommand.SUBSCRIBE, "/user/queue/chat", principal(userId))),
                mock(org.springframework.messaging.MessageChannel.class)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> interceptor.preSend(
                message(commandAccessor(StompCommand.SUBSCRIBE, "/queue/chat-user-victim", principal(userId))),
                mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void shouldAllowOnlyTypingSendDestination() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(true);

        assertThatCode(() -> interceptor.preSend(
                message(commandAccessor(StompCommand.SEND, "/app/chat.typing", principal(userId))),
                mock(org.springframework.messaging.MessageChannel.class)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> interceptor.preSend(
                message(commandAccessor(StompCommand.SEND, "/user/victim/queue/chat", principal(userId))),
                mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private StompHeaderAccessor connectAccessor(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private StompHeaderAccessor commandAccessor(StompCommand command, String destination,
                                                ChatPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setDestination(destination);
        if (principal != null) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private ChatPrincipal principal(UUID userId) {
        return new ChatPrincipal(userId, Instant.now().plusSeconds(900));
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }
}
