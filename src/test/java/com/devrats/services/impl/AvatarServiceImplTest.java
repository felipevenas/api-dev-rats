package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.UserResponse;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AvatarSource;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.storage.AvatarStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitHubAccountRepository githubAccountRepository;

    @Mock
    private AvatarStorage avatarStorage;

    private AppProperties appProperties;
    private AvatarServiceImpl service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getStorage().setPublicBaseUrl("https://cdn.test.local/");
        appProperties.getStorage().setKeyPrefix("avatars");
        service = new AvatarServiceImpl(userRepository, githubAccountRepository, avatarStorage,
                appProperties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldUploadAndNormalizeAvatar_whenImageIsValid() throws IOException {
        UserEntity user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.uploadAvatar(USER_ID, pngFile(256, 256));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(avatarStorage).upload(keyCaptor.capture(), any(byte[].class), eq("image/jpeg"));
        String key = keyCaptor.getValue();

        assertThat(key).startsWith("avatars/" + USER_ID + "/").endsWith(".jpg");
        assertThat(user.getAvatarUrl())
                .isEqualTo("https://cdn.test.local/" + key + "?v=" + NOW.getEpochSecond());
        assertThat(user.getAvatarSource()).isEqualTo(AvatarSource.CUSTOM);
        assertThat(user.getAvatarObjectKey()).isEqualTo(key);
        assertThat(response.avatarSource()).isEqualTo("CUSTOM");
        assertThat(response.avatarUrl()).isEqualTo(user.getAvatarUrl());
    }

    @Test
    void shouldDeletePreviousObject_whenUploadReplacesAvatar() throws IOException {
        UserEntity user = user();
        user.setAvatarSource(AvatarSource.CUSTOM);
        user.setAvatarObjectKey("avatars/" + USER_ID + "/old.jpg");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.uploadAvatar(USER_ID, pngFile(128, 128));

        verify(avatarStorage).delete("avatars/" + USER_ID + "/old.jpg");
    }

    @Test
    void shouldReject_whenContentIsNotAnImage() {
        UserEntity user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                "conteudo-invalido".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadAvatar(USER_ID, file))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AVATAR_INVALID_FORMAT));
        verifyNoInteractions(avatarStorage);
    }

    @Test
    void shouldReject_whenImageExceedsMaxBytes() {
        appProperties.getStorage().setMaxAvatarBytes(1024);
        UserEntity user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[2048]);

        assertThatThrownBy(() -> service.uploadAvatar(USER_ID, file))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AVATAR_TOO_LARGE));
        verifyNoInteractions(avatarStorage);
    }

    @Test
    void shouldRestoreGitHubAvatar_whenCustomAvatarIsDeleted() {
        UserEntity user = user();
        user.setAvatarSource(AvatarSource.CUSTOM);
        user.setAvatarObjectKey("avatars/" + USER_ID + "/current.jpg");
        user.setAvatarUrl("https://cdn.test.local/avatars/current.jpg");
        GitHubAccountEntity account = new GitHubAccountEntity();
        account.setAvatarUrl("https://avatars.githubusercontent.com/u/1");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(githubAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        UserResponse response = service.deleteAvatar(USER_ID);

        verify(avatarStorage).delete("avatars/" + USER_ID + "/current.jpg");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/1");
        assertThat(user.getAvatarSource()).isEqualTo(AvatarSource.GITHUB);
        assertThat(user.getAvatarObjectKey()).isNull();
        assertThat(response.avatarSource()).isEqualTo("GITHUB");
    }

    @Test
    void shouldClearAvatar_whenUserHasNoGitHubAccount() {
        UserEntity user = user();
        user.setAvatarSource(AvatarSource.CUSTOM);
        user.setAvatarObjectKey("avatars/" + USER_ID + "/current.jpg");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.deleteAvatar(USER_ID);

        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getAvatarSource()).isEqualTo(AvatarSource.NONE);
        assertThat(user.getAvatarObjectKey()).isNull();
        assertThat(response.avatarSource()).isEqualTo("NONE");
    }

    @Test
    void shouldKeepDeleting_whenStorageFails() {
        UserEntity user = user();
        user.setAvatarSource(AvatarSource.CUSTOM);
        user.setAvatarObjectKey("avatars/" + USER_ID + "/current.jpg");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DomainException(ErrorCode.AVATAR_STORAGE_UNAVAILABLE))
                .when(avatarStorage).delete("avatars/" + USER_ID + "/current.jpg");

        UserResponse response = service.deleteAvatar(USER_ID);

        assertThat(response.avatarSource()).isEqualTo("NONE");
        assertThat(user.getAvatarObjectKey()).isNull();
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setStatus(UserStatus.ACTIVE);
        user.setAvatarSource(AvatarSource.NONE);
        return user;
    }

    private MultipartFile pngFile(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "avatar.png", "image/png", output.toByteArray());
    }
}
