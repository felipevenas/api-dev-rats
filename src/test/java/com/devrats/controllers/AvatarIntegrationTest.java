package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.config.CacheConfig;
import com.devrats.dto.github.GitHubTokenResponse;
import com.devrats.dto.github.GitHubUser;
import com.devrats.services.GitHubIntegrationService;
import com.devrats.services.github.GitHubApiClient;
import com.devrats.services.storage.AvatarStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvatarIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private AvatarStorage avatarStorage;

    @MockitoBean
    private GitHubApiClient githubApiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private GitHubIntegrationService githubIntegrationService;

    @Test
    void shouldUploadAvatar_whenImageIsValid() throws Exception {
        UUID userId = registerUser();

        mockMvc.perform(uploadAvatar(userId, pngFile(400, 300)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarSource").value("CUSTOM"))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith(
                        "https://cdn.test.local/avatars/" + userId + "/")));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(avatarStorage).upload(keyCaptor.capture(), contentCaptor.capture(), eq("image/jpeg"));

        String key = keyCaptor.getValue();
        assertThat(key).matches("avatars/" + userId + "/[0-9a-f-]{36}\\.jpg");
        assertThat(contentCaptor.getValue()[1] & 0xFF).isEqualTo(0xD8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_source FROM users WHERE id = ?", String.class, userId)).isEqualTo("CUSTOM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_object_key FROM users WHERE id = ?", String.class, userId)).isEqualTo(key);
    }

    @Test
    void shouldRejectUpload_whenContentTypeLiesAboutFormat() throws Exception {
        UUID userId = registerUser();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                "nao-e-imagem".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(uploadAvatar(userId, file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_INVALID_FORMAT"));
    }

    @Test
    void shouldRejectUpload_whenImageIsBiggerThanLimit() throws Exception {
        UUID userId = registerUser();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[2_097_153]);

        mockMvc.perform(uploadAvatar(userId, file))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("AVATAR_TOO_LARGE"));
    }

    @Test
    void shouldRestoreGitHubAvatar_whenCustomAvatarIsDeleted() throws Exception {
        UUID userId = registerUser();
        jdbcTemplate.update("UPDATE users SET avatar_url = ?, avatar_source = 'CUSTOM', avatar_object_key = ? WHERE id = ?",
                "https://cdn.test.local/avatars/custom.jpg", "avatars/" + userId + "/custom.jpg", userId);
        jdbcTemplate.update("""
                INSERT INTO github_accounts (id, user_id, github_user_id, github_username, avatar_url,
                    connection_status, connected_at)
                VALUES (?, ?, ?, ?, ?, 'CONNECTED', now())
                """, UUID.randomUUID(), userId, 900_000L + userId.hashCode() % 100_000,
                "devrats-avatar", "https://avatars.githubusercontent.com/u/42.png");

        mockMvc.perform(delete("/api/v1/me/avatar").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarSource").value("GITHUB"))
                .andExpect(jsonPath("$.avatarUrl").value("https://avatars.githubusercontent.com/u/42.png"));

        verify(avatarStorage).delete("avatars/" + userId + "/custom.jpg");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_object_key FROM users WHERE id = ?", String.class, userId)).isNull();
    }

    @Test
    void shouldKeepCustomAvatar_whenGitHubAccountIsConnected() throws Exception {
        UUID userId = registerUser();
        jdbcTemplate.update("UPDATE users SET avatar_url = ?, avatar_source = 'CUSTOM', avatar_object_key = ? WHERE id = ?",
                "https://cdn.test.local/avatars/custom.jpg", "avatars/" + userId + "/custom.jpg", userId);
        stubGitHubCallback("state-custom-" + userId, userId);

        githubIntegrationService.handleCallback("code", "state-custom-" + userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_source FROM users WHERE id = ?", String.class, userId)).isEqualTo("CUSTOM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_url FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("https://cdn.test.local/avatars/custom.jpg");
    }

    @Test
    void shouldUseGitHubAvatar_whenUserHasNoCustomAvatar() throws Exception {
        UUID userId = registerUser();
        stubGitHubCallback("state-github-" + userId, userId);

        githubIntegrationService.handleCallback("code", "state-github-" + userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_source FROM users WHERE id = ?", String.class, userId)).isEqualTo("GITHUB");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_url FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("https://avatars.githubusercontent.com/u/99.png");
    }

    private void stubGitHubCallback(String state, UUID userId) {
        cacheManager.getCache(CacheConfig.OAUTH_STATES).put(state, userId);
        when(githubApiClient.exchangeCode("code"))
                .thenReturn(new GitHubTokenResponse("user-token", null, null, null, "bearer", "read:user"));
        when(githubApiClient.getAuthenticatedUser("user-token"))
                .thenReturn(new GitHubUser((UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE),
                        "devrats-avatar", "User", "https://avatars.githubusercontent.com/u/99.png"));
    }

    private UUID registerUser() throws Exception {
        String email = "avatar-" + UUID.randomUUID() + "@test.local";
        String username = "avatar" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"email":"%s","password":"Str0ng!Passw0rd","displayName":"Avatar Test",
                 "username":"%s","timezone":"America/Sao_Paulo","acceptedTermsVersion":"2026-10"}
                """.formatted(email, username);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("user").path("id").asString());
    }

    private MockMultipartHttpServletRequestBuilder uploadAvatar(UUID userId, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/me/avatar");
        builder.file(file);
        builder.with(authenticatedAs(userId));
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        return builder;
    }

    private MockMultipartFile pngFile(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "avatar.png", "image/png", output.toByteArray());
    }
}
