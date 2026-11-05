package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.dto.CreateChallengeRequest;
import com.devrats.dto.CreateGroupRequest;
import com.devrats.dto.RegisterRequest;
import com.devrats.dto.SendFriendRequestRequest;
import com.devrats.enums.VerificationMode;
import com.devrats.services.AuthService;
import com.devrats.services.AuthSession;
import com.devrats.services.FriendService;
import com.devrats.services.GroupService;
import com.devrats.services.storage.ChatAttachmentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private AuthService authService;

    @Autowired
    private FriendService friendService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ChatAttachmentStorage chatAttachmentStorage;

    @Test
    void shouldCreateDirectConversationExchangeAndReadMessages() throws Exception {
        AuthSession alice = register("ChatAlice");
        AuthSession bob = register("ChatBob");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();
        makeFriends(aliceId, bob.response().user().username(), bobId);

        MvcResult conversationResult = mockMvc.perform(post("/api/v1/chat/conversations")
                        .with(authenticatedAs(aliceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.type").value("DIRECT"))
                .andExpect(jsonPath("$.peer.id").value(bobId.toString()))
                .andReturn();
        String conversationId = idOf(conversationResult);

        String clientMessageId = UUID.randomUUID().toString();
        MvcResult messageResult = mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(aliceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId
                                + "\",\"type\":\"TEXT\",\"body\":\"Bora fechar o desafio hoje?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Bora fechar o desafio hoje?"))
                .andExpect(jsonPath("$.sender.id").value(aliceId.toString()))
                .andReturn();
        String messageId = idOf(messageResult);

        //---- Retry com o mesmo clientMessageId é idempotente e devolve 200.
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(aliceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId
                                + "\",\"type\":\"TEXT\",\"body\":\"Bora fechar o desafio hoje?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(messageId));

        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].body").value("Bora fechar o desafio hoje?"))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/v1/chat/conversations").with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));

        mockMvc.perform(get("/api/v1/chat/unread-count").with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/read")
                        .with(authenticatedAs(bobId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastMessageId\":\"" + messageId + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/chat/conversations").with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));

        mockMvc.perform(delete("/api/v1/chat/messages/" + messageId).with(authenticatedAs(aliceId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].body").doesNotExist());
    }

    @Test
    void shouldReturn404ForNonParticipant() throws Exception {
        AuthSession alice = register("ChatAlicePrivacy");
        AuthSession bob = register("ChatBobPrivacy");
        AuthSession carol = register("ChatCarolPrivacy");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();
        UUID carolId = carol.response().user().id();
        makeFriends(aliceId, bob.response().user().username(), bobId);

        String conversationId = idOf(mockMvc.perform(post("/api/v1/chat/conversations")
                        .with(authenticatedAs(aliceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(carolId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(carolId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + UUID.randomUUID()
                                + "\",\"type\":\"TEXT\",\"body\":\"oi\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/chat/conversations")
                        .with(authenticatedAs(carolId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"userId\":\"" + aliceId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void shouldCreateGroupConversationAndAllowActiveMemberToSend() throws Exception {
        AuthSession alice = register("ChatGroupAlice");
        AuthSession bob = register("ChatGroupBob");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();

        UUID groupId = groupService.createGroup(aliceId, new CreateGroupRequest(
                "Grupo do chat", "Teste", "America/Sao_Paulo", 10,
                new CreateChallengeRequest(VerificationMode.OWN_REPOSITORIES, 7,
                        Instant.now(), null, null, null))).id();
        jdbcTemplate.update("""
                INSERT INTO group_members (id, group_id, user_id, role, status, joined_at)
                VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', now())
                """, UUID.randomUUID(), groupId, bobId);

        String conversationId = idOf(mockMvc.perform(post("/api/v1/chat/conversations")
                        .with(authenticatedAs(bobId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GROUP\",\"groupId\":\"" + groupId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("GROUP"))
                .andExpect(jsonPath("$.groupId").value(groupId.toString()))
                .andReturn());

        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(bobId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + UUID.randomUUID()
                                + "\",\"type\":\"TEXT\",\"body\":\"Mensagem do grupo\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].body").value("Mensagem do grupo"));

        mockMvc.perform(get("/api/v1/chat/conversations").with(authenticatedAs(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("GROUP"))
                .andExpect(jsonPath("$.content[0].title").value("Grupo do chat"))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));
    }

    @Test
    void shouldUploadImageWhenStorageIsAvailable() throws Exception {
        AuthSession alice = register("ChatImageAlice");
        AuthSession bob = register("ChatImageBob");
        UUID aliceId = alice.response().user().id();
        UUID bobId = bob.response().user().id();
        makeFriends(aliceId, bob.response().user().username(), bobId);

        when(chatAttachmentStorage.isAvailable()).thenReturn(true);
        when(chatAttachmentStorage.presignGet(anyString(), any(), anyString(), anyString()))
                .thenReturn("https://cdn.test/chat/image.jpg");

        String conversationId = idOf(mockMvc.perform(post("/api/v1/chat/conversations")
                        .with(authenticatedAs(aliceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isOk())
                .andReturn());

        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/chat/conversations/" + conversationId + "/messages/attachments")
                        .file(file)
                        .param("clientMessageId", UUID.randomUUID().toString())
                        .param("type", "IMAGE")
                        .with(authenticatedAs(aliceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.attachments", hasSize(1)))
                .andExpect(jsonPath("$.attachments[0].kind").value("IMAGE"))
                .andExpect(jsonPath("$.attachments[0].url").value("https://cdn.test/chat/image.jpg"));
    }

    private void makeFriends(UUID requesterId, String addresseeUsername, UUID addresseeId) {
        UUID requestId = friendService.sendRequest(requesterId,
                new SendFriendRequestRequest(addresseeUsername)).id();
        friendService.acceptRequest(addresseeId, requestId);
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

    private byte[] jpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 400, 300);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
}
