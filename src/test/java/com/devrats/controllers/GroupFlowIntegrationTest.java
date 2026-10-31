package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.dto.RegisterRequest;
import com.devrats.services.AuthService;
import com.devrats.services.AuthSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuthService authService;

    @Test
    void shouldCreateGroupInviteAcceptAndStartChallenge() throws Exception {
        AuthSession owner = register("Owner");
        AuthSession member = register("Member");
        UUID ownerId = owner.response().user().id();
        UUID memberId = member.response().user().id();

        String groupBody = """
                {"name":"Grupo de teste","description":"Fluxo completo","timezone":"America/Sao_Paulo",
                 "maxMembers":10,
                 "challenge":{"verificationMode":"OWN_REPOSITORIES","targetValidDays":5,
                              "startsAt":"%s","repositoryIds":[]}}
                """.formatted(Instant.now().toString());

        MvcResult groupResult = mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentChallenge.status").value("DRAFT"))
                .andReturn();
        String groupId = objectMapper.readTree(groupResult.getResponse().getContentAsString()).path("id").asString();
        String challengeId = objectMapper.readTree(groupResult.getResponse().getContentAsString())
                .path("currentChallenge").path("id").asString();

        MvcResult inviteResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites")
                        .with(authenticatedAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String inviteToken = objectMapper.readTree(inviteResult.getResponse().getContentAsString()).path("token").asString();

        mockMvc.perform(post("/api/v1/invites/" + inviteToken + "/accept")
                        .with(authenticatedAs(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentChallenge.participantCount").value(2));

        mockMvc.perform(post("/api/v1/challenges/" + challengeId + "/start")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.participantCount").value(2));

        mockMvc.perform(get("/api/v1/challenges/" + challengeId + "/progress")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldAllowInviteAfterStart_whenAdmissionWindowIsStillOpen() throws Exception {
        AuthSession owner = register("OwnerWindow");
        AuthSession member = register("MemberWindow");
        UUID ownerId = owner.response().user().id();
        UUID memberId = member.response().user().id();
        Instant startsAt = Instant.now();
        Instant admissionEndsAt = startsAt.plusSeconds(3600);

        String groupBody = """
                {"name":"Grupo admissão","description":null,"timezone":"America/Sao_Paulo","maxMembers":10,
                 "challenge":{"verificationMode":"OWN_REPOSITORIES","targetValidDays":5,
                              "startsAt":"%s","admissionEndsAt":"%s","repositoryIds":[]}}
                """.formatted(startsAt, admissionEndsAt);

        MvcResult groupResult = mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String groupId = objectMapper.readTree(groupResult.getResponse().getContentAsString()).path("id").asString();
        String challengeId = objectMapper.readTree(groupResult.getResponse().getContentAsString())
                .path("currentChallenge").path("id").asString();

        MvcResult inviteResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites")
                        .with(authenticatedAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String inviteToken = objectMapper.readTree(inviteResult.getResponse().getContentAsString()).path("token").asString();

        mockMvc.perform(post("/api/v1/challenges/" + challengeId + "/start")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/invites/" + inviteToken + "/accept")
                        .with(authenticatedAs(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentChallenge.participantCount").value(2));
    }

    @Test
    void shouldCreateGroupWithBrasilia_whenTimezoneIsOmitted() throws Exception {
        AuthSession owner = register("OwnerNoTz");
        UUID ownerId = owner.response().user().id();

        String groupBody = """
                {"name":"Grupo sem fuso","description":null,"maxMembers":10,
                 "challenge":{"verificationMode":"OWN_REPOSITORIES","targetValidDays":5,
                              "startsAt":"%s","repositoryIds":[]}}
                """.formatted(Instant.now());

        mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.currentChallenge.timezone").value("America/Sao_Paulo"));
    }

    @Test
    void shouldRejectInvalidTimezoneOnCreateGroup() throws Exception {
        AuthSession owner = register("OwnerBadTz");
        UUID ownerId = owner.response().user().id();

        String groupBody = """
                {"name":"Grupo fuso inválido","description":null,"timezone":"Mars/Olympus","maxMembers":10,
                 "challenge":{"verificationMode":"OWN_REPOSITORIES","targetValidDays":5,
                              "startsAt":"%s","repositoryIds":[]}}
                """.formatted(Instant.now());

        mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private AuthSession register(String name) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return authService.register(new RegisterRequest(
                name.toLowerCase() + "-" + random + "@test.local",
                "Str0ng!Passw0rd",
                name,
                "user" + random,
                "America/Sao_Paulo",
                "2026-10"));
    }
}
