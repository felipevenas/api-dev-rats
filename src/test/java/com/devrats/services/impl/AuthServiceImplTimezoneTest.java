package com.devrats.services.impl;

import com.devrats.config.JwtProperties;
import com.devrats.dto.RegisterRequest;
import com.devrats.entities.UserEntity;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.JwtTokenService;
import com.devrats.services.PasswordResetService;
import com.devrats.services.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTimezoneTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GitHubAccountRepository githubAccountRepository = mock(GitHubAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, githubAccountRepository, passwordEncoder,
                jwtTokenService, new JwtProperties(), refreshTokenService, passwordResetService,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(githubAccountRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(jwtTokenService.issueAccessToken(any())).thenReturn("access-token");
        when(refreshTokenService.issue(any(), any())).thenReturn("refresh-token");
    }

    @Test
    void shouldRegisterWithBrasilia_whenTimezoneIsMissing() {
        service.register(request(null));

        assertThat(savedUser().getTimezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void shouldRegisterWithBrasilia_whenTimezoneIsBlank() {
        service.register(request("   "));

        assertThat(savedUser().getTimezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void shouldRegisterWithInformedTimezone_whenValid() {
        service.register(request("Europe/Lisbon"));

        assertThat(savedUser().getTimezone()).isEqualTo("Europe/Lisbon");
    }

    @Test
    void shouldRejectInvalidTimezoneOnRegister() {
        assertThatThrownBy(() -> service.register(request("Mars/Olympus")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Fuso horário inválido");
    }

    private UserEntity savedUser() {
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private RegisterRequest request(String timezone) {
        return RegisterRequest.basic("new-user@test.local", "Str0ng!Passw0rd", "New User",
                "newuser", timezone, "2026-10");
    }
}
