package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.entities.UserEntity;
import com.devrats.repositories.UserRepository;
import com.devrats.utils.SignatureHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndWebhookIntegrationTest extends IntegrationTestBase {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String VALID_PASSWORD = "Str0ng!Passw0rd";
    private static final String INVALID_CREDENTIALS_MESSAGE = "E-mail, usuário ou senha inválidos";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldRegisterLoginAndReadProfile() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@test.local";
        String username = "auth" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"email":"%s","password":"Str0ng!Passw0rd","displayName":"Auth Test",
                 "username":"%s","timezone":"America/Sao_Paulo","acceptedTermsVersion":"2026-10"}
                """.formatted(email, username);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        String userId = objectMapper.readTree(result.getResponse().getContentAsString()).path("user").path("id").asString();

        mockMvc.perform(get("/api/v1/me").with(authenticatedAs(UUID.fromString(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void shouldRegisterWithBrasilia_whenTimezoneIsOmitted() throws Exception {
        String email = "auth-tz-" + UUID.randomUUID() + "@test.local";
        String username = "tz" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"email":"%s","password":"Str0ng!Passw0rd","displayName":"TZ Test",
                 "username":"%s","acceptedTermsVersion":"2026-10"}
                """.formatted(email, username);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.timezone").value("America/Sao_Paulo"));
    }

    @Test
    void shouldRejectInvalidTimezoneOnRegister() throws Exception {
        String email = "auth-tz-invalid-" + UUID.randomUUID() + "@test.local";
        String username = "tzi" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"email":"%s","password":"Str0ng!Passw0rd","displayName":"TZ Test",
                 "username":"%s","timezone":"Mars/Olympus","acceptedTermsVersion":"2026-10"}
                """.formatted(email, username);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldLoginWithLegacyEmailPayload() throws Exception {
        Credentials credentials = createUser();

        login("""
                {"email":"%s","password":"%s"}
                """.formatted(credentials.email(), VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(credentials.username()));
    }

    @Test
    void shouldLoginWithIdentifierAsEmail() throws Exception {
        Credentials credentials = createUser();

        login("""
                {"identifier":"%s","password":"%s"}
                """.formatted(credentials.email().toUpperCase(), VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(credentials.email()));
    }

    @Test
    void shouldLoginWithPlainUsername() throws Exception {
        Credentials credentials = createUser();

        login("""
                {"identifier":" %s ","password":"%s"}
                """.formatted(credentials.username(), VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(credentials.username()));
    }

    @Test
    void shouldLoginWithAtPrefixedUsername() throws Exception {
        Credentials credentials = createUser();

        login("""
                {"identifier":"@%s","password":"%s"}
                """.formatted(credentials.username(), VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(credentials.username()));
    }

    @Test
    void shouldLoginWithUppercaseUsername() throws Exception {
        Credentials credentials = createUser();

        login("""
                {"identifier":"%s","password":"%s"}
                """.formatted(credentials.username().toUpperCase(), VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(credentials.username()));
    }

    @Test
    void shouldRejectUnknownIdentifier() throws Exception {
        login("""
                {"identifier":"missing%s","password":"%s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8), VALID_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS_MESSAGE));
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        Credentials credentials = createUser();

        login("""
                {"identifier":"%s","password":"Wrong!Passw0rd"}
                """.formatted(credentials.username()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS_MESSAGE));
    }

    @Test
    void shouldRejectLoginWithoutEmailOrIdentifier() throws Exception {
        login("""
                {"password":"%s"}
                """.formatted(VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'identifierPresent')]").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidWebhookSignature() throws Exception {
        String payload = "{\"zen\":\"ping\"}";

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .header("X-GitHub-Delivery", "invalid-" + UUID.randomUUID())
                        .header("X-GitHub-Event", "ping")
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptWebhookOncePerDelivery() throws Exception {
        String payload = "{\"zen\":\"ping\"}";
        String deliveryId = "delivery-" + UUID.randomUUID();
        String signature = signature(payload);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-GitHub-Event", "ping")
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-GitHub-Event", "ping")
                        .content(payload))
                .andExpect(status().isAccepted());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM github_events WHERE delivery_id = ?", Integer.class, deliveryId);
        assertThat(count).isEqualTo(1);
    }

    private ResultActions login(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Credentials createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity();
        user.setEmail("login-" + suffix + "@test.local");
        user.setUsername("login" + suffix.substring(0, 10));
        user.setDisplayName("Login Test");
        user.setPasswordHash(passwordEncoder.encode(VALID_PASSWORD));
        user.setTimezone("America/Sao_Paulo");
        userRepository.save(user);
        return new Credentials(user.getEmail(), user.getUsername());
    }

    private record Credentials(String email, String username) {
    }

    private String signature(String payload) {
        byte[] hash = SignatureHelper.hmacSha256(payload.getBytes(StandardCharsets.UTF_8), WEBHOOK_SECRET);
        return "sha256=" + HexFormat.of().formatHex(hash);
    }
}
