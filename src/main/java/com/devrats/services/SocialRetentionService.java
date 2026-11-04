package com.devrats.services;

/**
 * Aplica as janelas de retenção dos dados sociais (LGPD).
 */
public interface SocialRetentionService {

    void purgeExpired();
}
