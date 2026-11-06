package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.PasswordResetResponse;
import com.devrats.entities.PasswordResetTokenEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.PasswordResetTokenRepository;
import com.devrats.repositories.RefreshTokenRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.PasswordResetService;
import com.devrats.services.email.PasswordResetRequestedEvent;
import com.devrats.utils.PasswordPolicyHelper;
import com.devrats.utils.PasswordResetLinkHelper;
import com.devrats.utils.TokenHelper;
import com.devrats.utils.UserViewHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final Clock clock;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordResetTokenRepository tokenRepository,
                                    RefreshTokenRepository refreshTokenRepository,
                                    PasswordEncoder passwordEncoder,
                                    AppProperties properties,
                                    ApplicationEventPublisher eventPublisher,
                                    AuditService auditService,
                                    Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PasswordResetResponse requestReset(String email) {
        String normalizedEmail = UserViewHelper.normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return PasswordResetResponse.accepted(null);
        }

        //---- O lock serializa pedidos concorrentes da mesma conta sem segurar conexão durante o envio.
        Optional<UserEntity> found = userRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail);
        if (found.isEmpty()) {
            //---- Mesma resposta para e-mail desconhecido: sem enumeração de contas.
            return PasswordResetResponse.accepted(null);
        }
        UserEntity user = found.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            return PasswordResetResponse.accepted(null);
        }

        Instant now = clock.instant();
        Duration cooldown = properties.getAuth().getResetRequestCooldown();
        if (cooldown != null && !cooldown.isNegative() && !cooldown.isZero()
                && isInsideCooldown(user.getId(), now, cooldown)) {
            return PasswordResetResponse.accepted(null);
        }

        tokenRepository.markAllUsed(user.getId(), now);
        String raw = TokenHelper.randomToken();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user);
        token.setTokenHash(TokenHelper.hashToken(raw));
        Duration ttl = properties.getAuth().getResetTokenTtl();
        token.setExpiresAt(now.plus(ttl));
        PasswordResetTokenEntity saved = tokenRepository.save(token);

        auditService.record(user.getId(), "PASSWORD_RESET", saved.getId(), AuditAction.PASSWORD_RESET_REQUESTED,
                Map.<String, Object>of("expiresInMinutes", ttl.toMinutes()));
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                user.getId(),
                saved.getId(),
                user.getEmail(),
                user.getDisplayName(),
                PasswordResetLinkHelper.build(properties.getFrontendUrl(), raw),
                ttl.toMinutes()));

        log.info("Password reset token generated for user {}", user.getId());
        return PasswordResetResponse.accepted(properties.getAuth().isExposeResetToken() ? raw : null);
    }

    @Override
    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordPolicyHelper.validate(newPassword);
        PasswordResetTokenEntity stored = tokenRepository.findByTokenHashForUpdate(TokenHelper.hashToken(token))
                .orElseThrow(() -> new ValidationException("Token inválido ou expirado"));
        Instant now = clock.instant();
        if (stored.getUsedAt() != null || !stored.getExpiresAt().isAfter(now)) {
            throw new ValidationException("Token inválido ou expirado");
        }

        UserEntity user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        stored.setUsedAt(now);
        tokenRepository.save(stored);
        //---- Um reset concluído invalida qualquer outro link ainda pendente da conta.
        tokenRepository.markAllUsed(user.getId(), now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);

        auditService.record(user.getId(), "PASSWORD_RESET", stored.getId(), AuditAction.PASSWORD_RESET_COMPLETED, null);
        log.info("Password reset completed for user {}", user.getId());
    }

    @Override
    @Transactional
    public int purgeExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredBefore(clock.instant());
        if (deleted > 0) {
            log.info("Removed {} expired password reset tokens", deleted);
        }
        return deleted;
    }

    private boolean isInsideCooldown(UUID userId, Instant now, Duration cooldown) {
        return tokenRepository.findTopByUser_IdOrderByCreatedAtDesc(userId)
                .map(PasswordResetTokenEntity::getCreatedAt)
                .map(createdAt -> createdAt.plus(cooldown).isAfter(now))
                .orElse(false);
    }
}
