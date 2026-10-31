package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.UserResponse;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AvatarSource;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AvatarService;
import com.devrats.services.storage.AvatarStorage;
import com.devrats.utils.AvatarImageProcessor;
import com.devrats.utils.UserViewHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

@Service
public class AvatarServiceImpl implements AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarServiceImpl.class);

    private final UserRepository userRepository;
    private final GitHubAccountRepository githubAccountRepository;
    private final AvatarStorage avatarStorage;
    private final AppProperties.Storage storageProperties;
    private final Clock clock;

    public AvatarServiceImpl(UserRepository userRepository,
                             GitHubAccountRepository githubAccountRepository,
                             AvatarStorage avatarStorage,
                             AppProperties appProperties,
                             Clock clock) {
        this.userRepository = userRepository;
        this.githubAccountRepository = githubAccountRepository;
        this.avatarStorage = avatarStorage;
        this.storageProperties = appProperties.getStorage();
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserResponse uploadAvatar(UUID userId, MultipartFile file) {
        UserEntity user = getUser(userId);
        byte[] jpeg = AvatarImageProcessor.process(readBytes(file), storageProperties.getMaxAvatarBytes());
        String key = buildObjectKey(userId);

        avatarStorage.upload(key, jpeg, "image/jpeg");

        String previousKey = user.getAvatarObjectKey();
        user.setAvatarUrl(buildPublicUrl(key));
        user.setAvatarSource(AvatarSource.CUSTOM);
        user.setAvatarObjectKey(key);
        userRepository.save(user);

        if (previousKey != null && !previousKey.equals(key)) {
            deleteQuietly(previousKey);
        }
        log.info("Avatar atualizado: userId={}, key={}", userId, key);
        return UserViewHelper.toResponse(user, githubAccountRepository.findByUserId(userId).orElse(null));
    }

    @Override
    @Transactional
    public UserResponse deleteAvatar(UUID userId) {
        UserEntity user = getUser(userId);
        String previousKey = user.getAvatarObjectKey();
        if (previousKey != null) {
            deleteQuietly(previousKey);
        }

        GitHubAccountEntity account = githubAccountRepository.findByUserId(userId).orElse(null);
        String githubAvatar = account == null || account.getConnectionStatus() != GitHubConnectionStatus.CONNECTED
                ? null : account.getAvatarUrl();
        if (githubAvatar != null && !githubAvatar.isBlank()) {
            user.setAvatarUrl(githubAvatar);
            user.setAvatarSource(AvatarSource.GITHUB);
        } else {
            user.setAvatarUrl(null);
            user.setAvatarSource(AvatarSource.NONE);
        }
        user.setAvatarObjectKey(null);
        userRepository.save(user);

        log.info("Avatar removido: userId={}, source={}", userId, user.getAvatarSource());
        return UserViewHelper.toResponse(user, account);
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.AVATAR_INVALID_FORMAT, "Envie um arquivo de imagem");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new DomainException(ErrorCode.AVATAR_INVALID_FORMAT, "Não foi possível ler a imagem");
        }
    }

    private void deleteQuietly(String key) {
        try {
            avatarStorage.delete(key);
        } catch (RuntimeException exception) {
            //---- Objeto órfão não pode impedir a troca ou a remoção do avatar.
            log.warn("Falha ao remover avatar anterior: key={}", key, exception);
        }
    }

    private String buildObjectKey(UUID userId) {
        String prefix = storageProperties.getKeyPrefix() == null || storageProperties.getKeyPrefix().isBlank()
                ? "avatars" : stripSlashes(storageProperties.getKeyPrefix());
        return prefix + "/" + userId + "/" + UUID.randomUUID() + ".jpg";
    }

    private String buildPublicUrl(String key) {
        String base = storageProperties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            //---- Sem CDN configurada a URL fica relativa à chave; o storage segue operante.
            return "/" + key + "?v=" + clock.instant().getEpochSecond();
        }
        return stripTrailingSlash(base) + "/" + key + "?v=" + clock.instant().getEpochSecond();
    }

    private static String stripSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
