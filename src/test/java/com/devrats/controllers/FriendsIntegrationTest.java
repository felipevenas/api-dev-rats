package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.dto.RegisterRequest;
import com.devrats.services.AuthService;
import com.devrats.services.AuthSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo ponta a ponta do sistema de amizades.
 *
 * <p>Exige Testcontainers (Docker); não roda em ambientes sem daemon.</p>
 */
class FriendsIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSendAcceptListAndRemoveFriend() throws Exception {
        AuthSession alice = register("Alice");
        AuthSession bob = register("Bob");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();
        String bobUsername = bob.response().user().username();

        MvcResult sent = sendRequest(aliceId, bobUsername)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.direction").value("SENT"))
                .andExpect(jsonPath("$.user.username").value(bobUsername))
                .andReturn();
        String requestId = idOf(sent);

        mockMvc.perform(get("/api/v1/friends/requests")
                        .param("direction", "received")
                        .with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].direction").value("RECEIVED"));

        mockMvc.perform(get("/api/v1/friends/status")
                        .param("userIds", aliceId + "," + bobId)
                        .with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING_INCOMING"))
                .andExpect(jsonPath("$[1].status").value("SELF"));

        mockMvc.perform(post("/api/v1/friends/requests/" + requestId + "/accept")
                        .with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v1/friends").with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId").value(bobId.toString()))
                .andExpect(jsonPath("$.content[0].presence").value("OFFLINE"));

        mockMvc.perform(get("/api/v1/users/" + bobUsername).with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendshipStatus").value("FRIENDS"))
                .andExpect(jsonPath("$.friendsCount").value(1))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.timezone").doesNotExist());

        mockMvc.perform(delete("/api/v1/friends/" + bobId).with(authenticatedAs(aliceId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/friends").with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void shouldAutoAcceptReverseRequest() throws Exception {
        AuthSession alice = register("AliceAuto");
        AuthSession bob = register("BobAuto");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();

        sendRequest(aliceId, bob.response().user().username()).andExpect(status().isCreated());

        sendRequest(bobId, alice.response().user().username())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.direction").value("SENT"));

        mockMvc.perform(get("/api/v1/friends").with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId").value(bobId.toString()));
    }

    @Test
    void shouldReturn404_whenNonParticipantTriesToAccept() throws Exception {
        AuthSession alice = register("AliceStranger");
        AuthSession bob = register("BobStranger");
        AuthSession carol = register("CarolStranger");
        UUID aliceId = alice.response().user().id();
        String requestId = idOf(sendRequest(aliceId, bob.response().user().username())
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/friends/requests/" + requestId + "/accept")
                        .with(authenticatedAs(carol.response().user().id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIENDSHIP_NOT_FOUND"));
    }

    @Test
    void shouldOnlyAllowAddresseeToDeclineAndRequesterToCancel() throws Exception {
        AuthSession alice = register("AliceOwner");
        AuthSession bob = register("BobOwner");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();
        String requestId = idOf(sendRequest(aliceId, bob.response().user().username())
                .andExpect(status().isCreated())
                .andReturn());

        //---- O remetente não pode aceitar a própria solicitação.
        mockMvc.perform(post("/api/v1/friends/requests/" + requestId + "/accept")
                        .with(authenticatedAs(aliceId)))
                .andExpect(status().isNotFound());

        //---- O destinatário não pode cancelar a solicitação recebida.
        mockMvc.perform(delete("/api/v1/friends/requests/" + requestId).with(authenticatedAs(bobId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/friends/requests/" + requestId).with(authenticatedAs(aliceId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/friends/requests")
                        .param("direction", "sent")
                        .with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void shouldRejectSelfRequestAndInvalidSearch() throws Exception {
        AuthSession alice = register("AliceSelf");
        UUID aliceId = alice.response().user().id();

        mockMvc.perform(post("/api/v1/friends/requests")
                        .with(authenticatedAs(aliceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + alice.response().user().username() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/users/search").param("q", "a").with(authenticatedAs(aliceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldNotExposePresenceToNonFriends() throws Exception {
        AuthSession alice = register("AlicePresence");
        AuthSession bob = register("BobPresence");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();
        String bobUsername = bob.response().user().username();

        mockMvc.perform(post("/api/v1/presence/heartbeat").with(authenticatedAs(bobId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + bobUsername).with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presence").doesNotExist())
                .andExpect(jsonPath("$.friendshipStatus").value("NONE"));

        mockMvc.perform(get("/api/v1/users/" + bobUsername).with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendshipStatus").value("SELF"))
                .andExpect(jsonPath("$.presence").value("ONLINE"))
                .andExpect(jsonPath("$.lastSeenAt").doesNotExist());
    }

    @Test
    void shouldRemoveSocialGraph_whenAccountDeleted() throws Exception {
        AuthSession alice = register("AliceDelete");
        AuthSession bob = register("BobDelete");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();
        String requestId = idOf(sendRequest(aliceId, bob.response().user().username())
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/api/v1/friends/requests/" + requestId + "/accept")
                        .with(authenticatedAs(bobId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/presence/heartbeat").with(authenticatedAs(bobId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/me")
                        .with(authenticatedAs(bobId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        Integer friendships = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM friendships WHERE requester_id = ? OR addressee_id = ?",
                Integer.class, bobId, bobId);
        Integer presence = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_presence WHERE user_id = ?", Integer.class, bobId);
        assertThat(friendships).isZero();
        assertThat(presence).isZero();

        mockMvc.perform(get("/api/v1/friends").with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void shouldRateLimitSearchPerUser_andReturnRetryAfter() throws Exception {
        AuthSession alice = register("AliceLimit");
        UUID aliceId = alice.response().user().id();

        for (int attempt = 0; attempt < 20; attempt++) {
            mockMvc.perform(get("/api/v1/users/search").param("q", "zz").with(authenticatedAs(aliceId)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/users/search").param("q", "zz").with(authenticatedAs(aliceId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private org.springframework.test.web.servlet.ResultActions sendRequest(UUID userId, String username)
            throws Exception {
        return mockMvc.perform(post("/api/v1/friends/requests")
                .with(authenticatedAs(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\"}"));
    }

    private String idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asString();
    }

    private AuthSession register(String name) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return authService.register(new RegisterRequest(
                name.toLowerCase() + "-" + random + "@test.local",
                PASSWORD,
                name,
                "user" + random,
                "America/Sao_Paulo",
                "2026-10",
                null, null, null, null, null));
    }
}
