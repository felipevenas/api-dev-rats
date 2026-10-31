package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.utils.SignatureHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private String signature(String payload) {
        byte[] hash = SignatureHelper.hmacSha256(payload.getBytes(StandardCharsets.UTF_8), WEBHOOK_SECRET);
        return "sha256=" + HexFormat.of().formatHex(hash);
    }
}
