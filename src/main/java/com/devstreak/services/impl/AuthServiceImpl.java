package com.devstreak.services.impl;

import com.devstreak.config.JwtProperties;
import com.devstreak.dto.AuthResponse;
import com.devstreak.dto.LoginRequest;
import com.devstreak.dto.PasswordResetResponse;
import com.devstreak.dto.RegisterRequest;
import com.devstreak.dto.UserResponse;
import com.devstreak.entities.UserEntity;
import com.devstreak.enums.UserStatus;
import com.devstreak.exceptions.ConflictException;
import com.devstreak.exceptions.ErrorCode;
import com.devstreak.exceptions.UnauthorizedException;
import com.devstreak.exceptions.ValidationException;
import com.devstreak.repositories.GitHubAccountRepository;
import com.devstreak.repositories.UserRepository;
import com.devstreak.services.AuthService;
import com.devstreak.services.AuthSession;
import com.devstreak.services.JwtTokenService;
import com.devstreak.services.PasswordResetService;
import com.devstreak.services.RefreshTokenService;
import com.devstreak.utils.ActivityDateHelper;
import com.devstreak.utils.PasswordPolicyHelper;
import com.devstreak.utils.UserViewHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final GitHubAccountRepository githubAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository userRepository,
                           GitHubAccountRepository githubAccountRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenService jwtTokenService,
                           JwtProperties jwtProperties,
                           RefreshTokenService refreshTokenService,
                           PasswordResetService passwordResetService,
                           Clock clock) {
        this.userRepository = userRepository;
        this.githubAccountRepository = githubAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = UserViewHelper.normalizeEmail(request.email());
        ActivityDateHelper.parseZone(request.timezone());
        PasswordPolicyHelper.validate(request.password());
        if (userRepository.existsByEmailIgnoreCase(email)
                || userRepository.existsByUsernameIgnoreCase(request.username())) {
            //---- Do not reveal whether the e-mail or the username is the one already taken.
            throw new ConflictException(ErrorCode.DATA_CONFLICT, "E-mail ou nome de usuário indisponível");
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setUsername(request.username().toLowerCase());
        user.setDisplayName(request.displayName().trim());
        user.setTimezone(request.timezone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTermsAcceptedAt(clock.instant());
        user.setTermsVersion(request.acceptedTermsVersion());
        userRepository.save(user);
        return startSession(user, null);
    }

    @Override
    @Transactional
    public AuthSession login(LoginRequest request) {
        String email = UserViewHelper.normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            //---- Equalize the password check cost so unknown e-mails do not leak through timing.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "E-mail ou senha inválidos");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "E-mail ou senha inválidos");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Conta indisponível");
        }
        return startSession(user, null);
    }

    @Override
    @Transactional
    public AuthSession refresh(String refreshToken, String userAgent) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken, userAgent)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Sessão expirada"));
        UserEntity user = userRepository.findById(rotation.userId())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Conta indisponível"));
        UserResponse response = UserViewHelper.toResponse(user, githubAccountRepository.findByUserId(user.getId()).orElse(null));
        String accessToken = jwtTokenService.issueAccessToken(user.getId());
        return new AuthSession(AuthResponse.of(accessToken, jwtProperties.getAccessTtl().toSeconds(), response),
                rotation.refreshToken());
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
    }

    @Override
    public PasswordResetResponse requestPasswordReset(String email) {
        return passwordResetService.requestReset(email);
    }

    @Override
    public void confirmPasswordReset(String token, String newPassword) {
        passwordResetService.confirmReset(token, newPassword);
    }

    private AuthSession startSession(UserEntity user, String userAgent) {
        String accessToken = jwtTokenService.issueAccessToken(user.getId());
        String refreshToken = refreshTokenService.issue(user.getId(), userAgent);
        UserResponse response = UserViewHelper.toResponse(user, githubAccountRepository.findByUserId(user.getId()).orElse(null));
        return new AuthSession(AuthResponse.of(accessToken, jwtProperties.getAccessTtl().toSeconds(), response), refreshToken);
    }
}
