package com.devrats.config;

import com.devrats.config.websocket.ChatWebSocketHandlerDecoratorFactory;
import com.devrats.config.websocket.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

/**
 * STOMP sobre WebSocket nativo em {@code /ws}.
 *
 * <p>O broker simples é local à instância. Para múltiplas réplicas, trocar por
 * um broker relay (RabbitMQ/ActiveMQ) ou uma ponte Pub/Sub com Redis. O
 * handshake é liberado no SecurityConfig e autenticado no frame CONNECT.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final StompAuthChannelInterceptor authInterceptor;
    private final ChatWebSocketHandlerDecoratorFactory decoratorFactory;

    public WebSocketConfig(AppProperties properties,
                           StompAuthChannelInterceptor authInterceptor,
                           ChatWebSocketHandlerDecoratorFactory decoratorFactory) {
        this.properties = properties;
        this.authInterceptor = authInterceptor;
        this.decoratorFactory = decoratorFactory;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = properties.getCors().getAllowedOrigins();
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        //---- Eventos e payloads de digitação são JSON serializado manualmente.
        //---- Evita depender do conversor Jackson legado do broker (o projeto usa Jackson 3).
        messageConverters.clear();
        messageConverters.add(new StringMessageConverter());
        return false;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(decoratorFactory);
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendTimeLimit(10_000);
        registration.setSendBufferSizeLimit(512 * 1024);
    }

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
