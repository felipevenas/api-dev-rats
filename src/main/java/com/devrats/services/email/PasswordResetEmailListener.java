package com.devrats.services.email;

import com.devrats.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailListener.class);

    private final EmailService emailService;

    public PasswordResetEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PasswordResetRequestedEvent event) {
        try {
            emailService.sendPasswordResetEmail(new PasswordResetEmail(
                    event.userId(),
                    event.recipient(),
                    event.displayName(),
                    event.resetUrl(),
                    event.expiresInMinutes(),
                    "password-reset-" + event.tokenId()));
        } catch (RuntimeException exception) {
            //---- O evento já foi confirmado no banco; falha de e-mail não pode vazar token nem derrubar a thread.
            log.error("Falha inesperada ao enviar e-mail de redefinição para o usuário {}", event.userId(), exception);
        }
    }
}
