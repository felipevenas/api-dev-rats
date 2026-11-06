package com.devrats.services.impl;

import com.devrats.config.EmailProperties;
import com.devrats.exceptions.EmailDeliveryException;
import com.devrats.services.email.EmailMessage;
import com.devrats.services.email.EmailSendResult;
import com.devrats.services.email.PasswordResetEmail;
import com.devrats.services.email.PasswordResetEmailTemplate;
import com.devrats.services.email.ResendEmailClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    private final ResendEmailClient client = mock(ResendEmailClient.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private EmailProperties properties;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new EmailProperties();
        properties.setMode(EmailProperties.EmailMode.LIVE);
        properties.setApiKey("re_test");
        properties.setFrom("DevRats <nao-responda@eufelipedesign.cloud>");
        properties.setReplyTo("qa@example.com");
        properties.setTestRecipient("qa@example.com");
        service = new EmailServiceImpl(properties, client, new PasswordResetEmailTemplate(), meterRegistry);
    }

    @Test
    void shouldSkip_whenModeIsOff() {
        properties.setMode(EmailProperties.EmailMode.OFF);

        service.sendPasswordResetEmail(email());

        verifyNoInteractions(client);
        assertThat(counter("skipped")).isEqualTo(1.0);
    }

    @Test
    void shouldRedirectToTestRecipientAndPrefixSubject_whenTestMode() {
        properties.setMode(EmailProperties.EmailMode.TEST);
        when(client.send(any())).thenReturn(new EmailSendResult("email-1"));

        service.sendPasswordResetEmail(email());

        EmailMessage message = captureMessage();
        assertThat(message.to()).isEqualTo("qa@example.com");
        assertThat(message.subject()).startsWith("[TESTE]");
        assertThat(message.html()).contains("MODO DE TESTE");
        assertThat(message.replyTo()).isEqualTo("qa@example.com");
        assertThat(counter("sent")).isEqualTo(1.0);
    }

    @Test
    void shouldSendToTheUser_whenLiveMode() {
        when(client.send(any())).thenReturn(new EmailSendResult("email-2"));

        service.sendPasswordResetEmail(email());

        EmailMessage message = captureMessage();
        assertThat(message.to()).isEqualTo("user@example.com");
        assertThat(message.subject()).isEqualTo("Redefina sua senha no DevRats");
        assertThat(counter("sent")).isEqualTo(1.0);
    }

    @Test
    void shouldAbsorbProviderFailure() {
        when(client.send(any())).thenThrow(new EmailDeliveryException("validation_error", "Invalid from", 422, false));

        service.sendPasswordResetEmail(email());

        verify(client).send(any());
        assertThat(counter("failed")).isEqualTo(1.0);
    }

    @Test
    void shouldSkip_whenConfigurationIsIncomplete() {
        properties.setMode(EmailProperties.EmailMode.TEST);
        properties.setTestRecipient("");

        service.sendPasswordResetEmail(email());

        verify(client, never()).send(any());
        assertThat(counter("skipped")).isEqualTo(1.0);
    }

    private EmailMessage captureMessage() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(client).send(captor.capture());
        return captor.getValue();
    }

    private double counter(String result) {
        return meterRegistry.get("email.send")
                .tag("type", "password_reset")
                .tag("result", result)
                .counter()
                .count();
    }

    private PasswordResetEmail email() {
        return new PasswordResetEmail(
                UUID.randomUUID(),
                "user@example.com",
                "Felipe",
                "https://devrats.app/reset-password#token=abc123",
                30,
                "password-reset-token-id");
    }
}
