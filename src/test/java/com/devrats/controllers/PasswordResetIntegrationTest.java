package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.entities.PasswordResetTokenEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.UserStatus;
import com.devrats.repositories.PasswordResetTokenRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.utils.TokenHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIntegrationTest extends IntegrationTestBase {

    private static final String VALID_PASSWORD = "Str0ng!Passw0rd";
    private static final String NEW_PASSWORD = "N3w!Passw0rd";
    private static final String GENERIC_MESSAGE =
            "Se houver uma conta com este e-mail, enviaremos um link para redefinir a senha.";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldResetPasswordAndInvalidateToken() throws Exception {
        Credentials credentials = createUser(UserStatus.ACTIVE);

        MvcResult result = requestReset(credentials.email())
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andExpect(jsonPath("$.resetToken").isNotEmpty())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("resetToken").asString();

        confirmReset(token, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        login(credentials.email(), NEW_PASSWORD)
                .andExpect(status().isOk());
        login(credentials.email(), VALID_PASSWORD)
                .andExpect(status().isUnauthorized());

        confirmReset(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnGenericResponse_whenEmailIsUnknown() throws Exception {
        requestReset("missing-" + UUID.randomUUID() + "@test.local")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andExpect(jsonPath("$.resetToken").doesNotExist());
    }

    @Test
    void shouldNotIssueToken_whenUserIsInactive() throws Exception {
        Credentials credentials = createUser(UserStatus.SUSPENDED);

        requestReset(credentials.email())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resetToken").doesNotExist());
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        UserEntity user = createUserEntity(UserStatus.ACTIVE);
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user);
        token.setTokenHash(TokenHelper.hashToken("expired-token"));
        token.setExpiresAt(Instant.now().minusSeconds(60));
        tokenRepository.save(token);

        confirmReset("expired-token", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnknownToken() throws Exception {
        confirmReset("does-not-exist", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(email)));
    }

    private ResultActions confirmReset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","newPassword":"%s"}
                        """.formatted(token, newPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"identifier":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private Credentials createUser(UserStatus status) {
        UserEntity user = createUserEntity(status);
        return new Credentials(user.getEmail());
    }

    private UserEntity createUserEntity(UserStatus status) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity();
        user.setEmail("reset-" + suffix + "@test.local");
        user.setUsername("reset" + suffix.substring(0, 10));
        user.setDisplayName("Reset Test");
        user.setPasswordHash(passwordEncoder.encode(VALID_PASSWORD));
        user.setTimezone("America/Sao_Paulo");
        user.setStatus(status);
        return userRepository.save(user);
    }

    private record Credentials(String email) {
    }
}
