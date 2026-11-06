package com.devrats.services.impl;

import com.devrats.services.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupScheduler.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetTokenCleanupScheduler(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(fixedDelay = 86_400_000, initialDelay = 600_000)
    public void purgeExpired() {
        try {
            passwordResetService.purgeExpiredTokens();
        } catch (RuntimeException exception) {
            log.warn("Falha ao limpar tokens de redefinição expirados", exception);
        }
    }
}
