package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileIntegrationTest extends IntegrationTestBase {

    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRegisterWithCompleteProfile() throws Exception {
        MvcResult result = registerWith("""
                ,"bio":"Backend dev","location":"São Paulo, SP","websiteUrl":"https://devrats.app",
                "experienceLevel":"SENIOR","primaryStack":["TYPESCRIPT","JAVA","REACT","JAVA"]
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.bio").value("Backend dev"))
                .andExpect(jsonPath("$.user.location").value("São Paulo, SP"))
                .andExpect(jsonPath("$.user.websiteUrl").value("https://devrats.app"))
                .andExpect(jsonPath("$.user.experienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$.user.primaryStack", contains("JAVA", "REACT", "TYPESCRIPT")))
                .andReturn();

        UUID userId = userIdOf(result);

        mockMvc.perform(get("/api/v1/me").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Backend dev"))
                .andExpect(jsonPath("$.primaryStack", contains("JAVA", "REACT", "TYPESCRIPT")));

        String storedCsv = jdbcTemplate.queryForObject(
                "SELECT primary_stack FROM users WHERE id = ?", String.class, userId);
        assertThat(storedCsv).isEqualTo("JAVA,REACT,TYPESCRIPT");
    }

    @Test
    void shouldUpdateProfileAndClearFields() throws Exception {
        UUID userId = userIdOf(registerWith("").andExpect(status().isCreated()).andReturn());

        mockMvc.perform(patch("/api/v1/me")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Nova bio","location":"Recife, PE","websiteUrl":"http://linkedin.com/in/dev",
                                 "experienceLevel":"PLENO","primaryStack":["DOCKER","AWS"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Nova bio"))
                .andExpect(jsonPath("$.location").value("Recife, PE"))
                .andExpect(jsonPath("$.websiteUrl").value("http://linkedin.com/in/dev"))
                .andExpect(jsonPath("$.experienceLevel").value("PLENO"))
                .andExpect(jsonPath("$.primaryStack", contains("AWS", "DOCKER")));

        //---- Texto vazio vira null (omitido pela serialização) e lista vazia limpa a stack.
        mockMvc.perform(patch("/api/v1/me")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"","location":"","websiteUrl":"","primaryStack":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.websiteUrl").doesNotExist())
                .andExpect(jsonPath("$.primaryStack").isEmpty())
                .andExpect(jsonPath("$.experienceLevel").value("PLENO"));
    }

    @Test
    void shouldExportProfileFieldsInLgpdData() throws Exception {
        UUID userId = userIdOf(registerWith("""
                ,"bio":"Bio export","location":"Curitiba, PR","websiteUrl":"https://example.com",
                "experienceLevel":"ESPECIALISTA","primaryStack":["PYTHON","AWS"]
                """).andExpect(status().isCreated()).andReturn());

        mockMvc.perform(get("/api/v1/me/export").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Bio export"))
                .andExpect(jsonPath("$.location").value("Curitiba, PR"))
                .andExpect(jsonPath("$.websiteUrl").value("https://example.com"))
                .andExpect(jsonPath("$.experienceLevel").value("ESPECIALISTA"))
                .andExpect(jsonPath("$.primaryStack", contains("AWS", "PYTHON")));
    }

    @Test
    void shouldRejectInvalidWebsiteUrl() throws Exception {
        registerWith(",\"websiteUrl\":\"ftp://devrats.app\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectStackOutsideAllowlist() throws Exception {
        registerWith(",\"primaryStack\":[\"COBOL\"]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectMoreThanFiveStacks() throws Exception {
        registerWith(",\"primaryStack\":[\"JAVA\",\"KOTLIN\",\"GO\",\"RUST\",\"SQL\",\"AWS\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectInvalidExperienceLevel() throws Exception {
        registerWith(",\"experienceLevel\":\"MASTER\"")
                .andExpect(status().isBadRequest());
    }

    private ResultActions registerWith(String extraJson) throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@test.local";
        String username = "prof" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"email":"%s","password":"Str0ng!Passw0rd","displayName":"Profile Test",
                 "username":"%s","timezone":"America/Sao_Paulo","acceptedTermsVersion":"2026-10"%s}
                """.formatted(email, username, extraJson);
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(uniqueClientIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID userIdOf(MvcResult result) throws Exception {
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).path("user").path("id").asString();
        return UUID.fromString(id);
    }

    /** Cada requisição de cadastro usa um IP próprio para não esbarrar no rate limit de auth. */
    private RequestPostProcessor uniqueClientIp() {
        String ip = "10.20.0." + CLIENT_SEQUENCE.incrementAndGet();
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
