package com.devrats.services.impl;

import com.devrats.config.EmailProperties;
import com.devrats.exceptions.EmailDeliveryException;
import com.devrats.services.EmailService;
import com.devrats.services.email.EmailContent;
import com.devrats.services.email.EmailMessage;
import com.devrats.services.email.EmailSendResult;
import com.devrats.services.email.EmailTag;
import com.devrats.services.email.PasswordResetEmail;
import com.devrats.services.email.PasswordResetEmailTemplate;
import com.devrats.services.email.ResendEmailClient;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String TYPE = "password_reset";

    private final EmailProperties properties;
    private final ResendEmailClient client;
    private final PasswordResetEmailTemplate template;
    private final MeterRegistry meterRegistry;

    public EmailServiceImpl(EmailProperties properties,
                            ResendEmailClient client,
                            PasswordResetEmailTemplate template,
                            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.client = client;
        this.template = template;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void logConfiguration() {
        if (properties.getMode() == EmailProperties.EmailMode.OFF) {
            log.info("E-mail transacional desligado (EMAIL_MODE=off)");
            return;
        }
        if (!properties.isConfigured()) {
            log.error("E-mail transacional em modo {} sem configuração completa; os envios serão ignorados",
                    properties.getMode());
            return;
        }
        if (properties.getMode() == EmailProperties.EmailMode.LIVE && properties.isSandboxSender()) {
            log.warn("EMAIL_MODE=live com remetente resend.dev; o Resend só entrega ao dono da conta");
        }
    }

    @Override
    public void sendPasswordResetEmail(PasswordResetEmail email) {
        if (properties.getMode() == EmailProperties.EmailMode.OFF) {
            log.debug("E-mail de redefinição ignorado: EMAIL_MODE=off (userId={})", email.userId());
            count("skipped");
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("E-mail de redefinição ignorado: configuração incompleta (userId={})", email.userId());
            count("skipped");
            return;
        }

        boolean testMode = properties.getMode() == EmailProperties.EmailMode.TEST;
        String recipient = testMode ? properties.getTestRecipient() : email.to();
        EmailContent content = template.build(email, testMode, email.to());
        EmailMessage message = new EmailMessage(
                properties.getFrom(),
                recipient,
                content.subject(),
                content.html(),
                content.text(),
                trimToNull(properties.getReplyTo()),
                email.idempotencyKey(),
                List.of(new EmailTag("type", TYPE)));

        try {
            EmailSendResult result = client.send(message);
            log.info("E-mail de redefinição enviado (userId={}, deliveryId={}, mode={})",
                    email.userId(), result.id(), properties.getMode());
            count("sent");
        } catch (EmailDeliveryException exception) {
            log.warn("Falha ao enviar e-mail de redefinição (userId={}, code={}, status={}, retryable={})",
                    email.userId(), exception.getCode(), exception.getStatus(), exception.isRetryable());
            count("failed");
        }
    }

    private void count(String result) {
        meterRegistry.counter("email.send", "type", TYPE, "result", result).increment();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
