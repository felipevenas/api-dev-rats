package com.devstreak.services.impl;

import com.devstreak.config.JwtProperties;
import com.devstreak.entities.RefreshTokenEntity;
import com.devstreak.entities.UserEntity;
import com.devstreak.repositories.RefreshTokenRepository;
import com.devstreak.repositories.UserRepository;
import com.devstreak.services.RefreshTokenService;
import com.devstreak.utils.TokenHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository,
                                   UserRepository userRepository,
                                   JwtProperties jwtProperties,
                                   Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String issue(UUID userId, String userAgent) {
        UserEntity user = userRepository.getReferenceById(userId);
        return createToken(user, UUID.randomUUID(), userAgent);
    }

    @Override
    @Transactional
    public Optional<Rotation> rotate(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshTokenEntity> found = refreshTokenRepository.findByTokenHashForUpdate(
                TokenHelper.hashToken(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenEntity current = found.get();
        Instant now = clock.instant();
        if (current.getRevokedAt() != null) {
            //---- A rotated token was presented again: assume theft and revoke every token in the family.
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }
        if (current.getExpiresAt().isBefore(now)) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }
        current.setRevokedAt(now);
        refreshTokenRepository.save(current);
        String newToken = createToken(current.getUser(), current.getFamilyId(), userAgent);
        return Optional.of(new Rotation(current.getUser().getId(), newToken));
    }

    @Override
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHelper.hashToken(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, clock.instant());
    }

    private String createToken(UserEntity user, UUID familyId, String userAgent) {
        String raw = TokenHelper.randomToken();
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setFamilyId(familyId);
        token.setTokenHash(TokenHelper.hashToken(raw));
        token.setExpiresAt(clock.instant().plus(jwtProperties.getRefreshTtl()));
        token.setUserAgent(userAgent);
        refreshTokenRepository.save(token);
        return raw;
    }
}
