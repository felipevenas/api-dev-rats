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
import com.devrats.services.email.PasswordResetRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AppProperties properties = new AppProperties();
    private final Instant now = Instant.parse("2026-11-06T12:00:00Z");

    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        properties.setFrontendUrl("https://devrats.app");
        properties.getAuth().setResetTokenTtl(Duration.ofMinutes(30));
        properties.getAuth().setResetRequestCooldown(Duration.ofMinutes(1));
        properties.getAuth().setExposeResetToken(true);
        service = new PasswordResetServiceImpl(userRepository, tokenRepository, refreshTokenRepository,
                passwordEncoder, properties, eventPublisher, auditService, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void shouldReturnAcceptedWithoutToken_whenEmailIsUnknown() {
        when(userRepository.findByEmailIgnoreCaseForUpdate("missing@test.local")).thenReturn(Optional.empty());

        PasswordResetResponse response = service.requestReset("missing@test.local");

        assertThat(response.resetToken()).isNull();
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldCreateTokenAndPublishEvent_whenUserExists() {
        UserEntity user = activeUser();
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUser_IdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(tokenRepository.save(any())).thenAnswer(invocation -> {
            PasswordResetTokenEntity token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            return token;
        });

        PasswordResetResponse response = service.requestReset(" FELIPE@EXAMPLE.COM ");

        assertThat(response.resetToken()).isNotBlank();
        verify(tokenRepository).markAllUsed(user.getId(), now);

        ArgumentCaptor<PasswordResetTokenEntity> tokenCaptor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetTokenEntity saved = tokenCaptor.getValue();
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));

        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordResetRequestedEvent event = eventCaptor.getValue();
        assertThat(event.recipient()).isEqualTo(user.getEmail());
        assertThat(event.resetUrl()).startsWith("https://devrats.app/reset-password#token=");
        assertThat(event.expiresInMinutes()).isEqualTo(30);
        verify(auditService).record(eq(user.getId()), eq("PASSWORD_RESET"), any(),
                eq(AuditAction.PASSWORD_RESET_REQUESTED), any());
    }

    @Test
    void shouldRespectCooldown_whenRecentTokenExists() {
        UserEntity user = activeUser();
        PasswordResetTokenEntity latest = new PasswordResetTokenEntity();
        latest.setCreatedAt(now.minus(Duration.ofSeconds(30)));
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUser_IdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.of(latest));

        PasswordResetResponse response = service.requestReset(user.getEmail());

        assertThat(response.resetToken()).isNull();
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldNotCreateToken_whenUserIsInactive() {
        UserEntity user = activeUser();
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        PasswordResetResponse response = service.requestReset(user.getEmail());

        assertThat(response.resetToken()).isNull();
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldChangePasswordAndRevokeSessions_whenTokenIsValid() {
        UserEntity user = activeUser();
        PasswordResetTokenEntity stored = storedToken(user, now.plus(Duration.ofMinutes(10)), null);
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("N3w!Passw0rd")).thenReturn("new-hash");

        service.confirmReset("raw-token", "N3w!Passw0rd");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(stored.getUsedAt()).isEqualTo(now);
        verify(userRepository).save(user);
        verify(tokenRepository).save(stored);
        verify(tokenRepository).markAllUsed(user.getId(), now);
        verify(refreshTokenRepository).revokeAllForUser(user.getId(), now);
        verify(auditService).record(eq(user.getId()), eq("PASSWORD_RESET"), eq(stored.getId()),
                eq(AuditAction.PASSWORD_RESET_COMPLETED), isNull());
    }

    @Test
    void shouldRejectExpiredToken() {
        UserEntity user = activeUser();
        PasswordResetTokenEntity stored = storedToken(user, now, null);
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.confirmReset("raw-token", "N3w!Passw0rd"))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void shouldRejectUsedToken() {
        UserEntity user = activeUser();
        PasswordResetTokenEntity stored = storedToken(user, now.plus(Duration.ofMinutes(10)), now.minusSeconds(1));
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.confirmReset("raw-token", "N3w!Passw0rd"))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void shouldRejectWeakPasswordBeforeLookingUpToken() {
        assertThatThrownBy(() -> service.confirmReset("raw-token", "weak"))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void shouldPurgeExpiredTokens() {
        when(tokenRepository.deleteExpiredBefore(now)).thenReturn(3);

        assertThat(service.purgeExpiredTokens()).isEqualTo(3);
    }

    private UserEntity activeUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("felipe@example.com");
        user.setDisplayName("Felipe");
        user.setUsername("felipe");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private PasswordResetTokenEntity storedToken(UserEntity user, Instant expiresAt, Instant usedAt) {
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return token;
    }
}
