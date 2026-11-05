package com.devrats.config.websocket;

import com.devrats.config.AppProperties;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry(new AppProperties());
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(jwtDecoder, registry);

    @Test
    void shouldAuthenticateConnectFrameWithBearerToken() {
        UUID userId = UUID.randomUUID();
        when(jwtDecoder.decode("token")).thenReturn(jwt(userId));
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
    void shouldRejectSendWithoutPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor),
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
