package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.PasswordResetResponse;
import com.devrats.entities.PasswordResetTokenEntity;
import com.devrats.entities.UserEntity;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.PasswordResetTokenRepository;
import com.devrats.repositories.RefreshTokenRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.PasswordResetService;
import com.devrats.utils.PasswordPolicyHelper;
import com.devrats.utils.TokenHelper;
import com.devrats.utils.UserViewHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordResetTokenRepository tokenRepository,
                                    RefreshTokenRepository refreshTokenRepository,
                                    PasswordEncoder passwordEncoder,
                                    AppProperties properties,
                                    Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PasswordResetResponse requestReset(String email) {
        Optional<UserEntity> user = userRepository.findByEmailIgnoreCase(UserViewHelper.normalizeEmail(email));
        if (user.isEmpty()) {
            //---- Same response for unknown e-mail: no account enumeration.
            return PasswordResetResponse.accepted(null);
        }
        Instant now = clock.instant();
        tokenRepository.markAllUsed(user.get().getId(), now);
        String raw = TokenHelper.randomToken();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user.get());
        token.setTokenHash(TokenHelper.hashToken(raw));
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokenRepository.save(token);
        log.info("Password reset token generated for user {}", user.get().getId());
        return PasswordResetResponse.accepted(properties.getAuth().isExposeResetToken() ? raw : null);
    }

    @Override
    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordPolicyHelper.validate(newPassword);
        PasswordResetTokenEntity stored = tokenRepository.findByTokenHashForUpdate(TokenHelper.hashToken(token))
                .orElseThrow(() -> new ValidationException("Token inválido ou expirado"));
        Instant now = clock.instant();
        if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(now)) {
            throw new ValidationException("Token inválido ou expirado");
        }
        UserEntity user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        stored.setUsedAt(now);
        tokenRepository.save(stored);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        log.info("Password reset completed for user {}", user.getId());
    }
}
