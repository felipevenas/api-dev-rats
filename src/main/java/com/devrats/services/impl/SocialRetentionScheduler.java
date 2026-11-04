package com.devrats.services.impl;

import com.devrats.services.SocialRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Executa a retenção social uma vez por dia, fora do pico.
 */
@Component
public class SocialRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SocialRetentionScheduler.class);

    private final SocialRetentionService socialRetentionService;

    public SocialRetentionScheduler(SocialRetentionService socialRetentionService) {
        this.socialRetentionService = socialRetentionService;
    }

    @Scheduled(fixedDelay = 86_400_000, initialDelay = 300_000)
    public void purgeExpired() {
        try {
            socialRetentionService.purgeExpired();
        } catch (RuntimeException exception) {
            //---- Uma falha de retenção não pode derrubar o scheduler nem vazar dados de usuário.
            log.warn("Falha ao aplicar retenção social", exception);
        }
    }
}
