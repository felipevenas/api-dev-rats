package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.dto.RegisterRequest;
import com.devrats.services.AuthService;
import com.devrats.services.AuthSession;
import com.devrats.services.storage.StudyPhotoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudyPhotoIntegrationTest extends IntegrationTestBase {

    private static final String PHOTO_URL = "https://cdn.test.local/study-photo.jpg";

    @Autowired
    private AuthService authService;

    @MockitoBean
    private StudyPhotoStorage photoStorage;

    @BeforeEach
    void setUpStorage() {
        when(photoStorage.isAvailable()).thenReturn(true);
        when(photoStorage.presignGet(anyString(), any(), anyString(), anyString())).thenReturn(PHOTO_URL);
    }

    @Test
    void shouldPublishPhotoValidateDayAndRejectSecondPhoto() throws Exception {
        AuthSession owner = register("PhotoOwner");
        UUID ownerId = owner.response().user().id();
        String challengeId = createStartedPhotoChallenge(ownerId);

        mockMvc.perform(multipart("/api/v1/challenges/" + challengeId + "/photos")
                        .file(jpegFile())
                        .param("title", "Estudo de Java")
                        .param("description", "Coleções e streams")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Estudo de Java"))
                .andExpect(jsonPath("$.description").value("Coleções e streams"))
                .andExpect(jsonPath("$.imageUrl").value(PHOTO_URL))
                .andExpect(jsonPath("$.canDelete").value(true));

        mockMvc.perform(get("/api/v1/challenges/" + challengeId + "/progress")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validDays").value(1))
                .andExpect(jsonPath("$[0].lastValidDate").exists());

        mockMvc.perform(multipart("/api/v1/challenges/" + challengeId + "/photos")
                        .file(jpegFile())
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHOTO_ALREADY_PUBLISHED"));

        mockMvc.perform(get("/api/v1/challenges/" + challengeId + "/photos")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].imageUrl").value(PHOTO_URL));
    }

    @Test
    void shouldRemoveDayWhenPhotoIsDeleted() throws Exception {
        AuthSession owner = register("PhotoDelete");
        UUID ownerId = owner.response().user().id();
        String challengeId = createStartedPhotoChallenge(ownerId);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/challenges/" + challengeId + "/photos")
                        .file(jpegFile())
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isCreated())
                .andReturn();
        String photoId = objectMapper.readTree(upload.getResponse().getContentAsString()).path("id").asString();

        mockMvc.perform(delete("/api/v1/challenges/" + challengeId + "/photos/" + photoId)
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/challenges/" + challengeId + "/progress")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validDays").value(0));

        mockMvc.perform(get("/api/v1/challenges/" + challengeId + "/photos")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldReturnNotFoundWhenNonMemberTriesToPublish() throws Exception {
        AuthSession owner = register("PhotoPrivateOwner");
        AuthSession stranger = register("PhotoStranger");
        UUID ownerId = owner.response().user().id();
        UUID strangerId = stranger.response().user().id();
        String challengeId = createStartedPhotoChallenge(ownerId);

        mockMvc.perform(multipart("/api/v1/challenges/" + challengeId + "/photos")
                        .file(jpegFile())
                        .with(authenticatedAs(strangerId)))
                .andExpect(status().isNotFound());
    }

    private String createStartedPhotoChallenge(UUID ownerId) throws Exception {
        String groupBody = """
                {"name":"Grupo foto","description":"Fotos de estudo","timezone":"America/Sao_Paulo",
                 "maxMembers":10,
                 "challenge":{"verificationMode":"STUDY_PHOTO","targetValidDays":5,
                              "startsAt":"%s","repositoryIds":[]}}
                """.formatted(Instant.now());
        MvcResult groupResult = mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String challengeId = objectMapper.readTree(groupResult.getResponse().getContentAsString())
                .path("currentChallenge").path("id").asString();
        mockMvc.perform(post("/api/v1/challenges/" + challengeId + "/start")
                        .with(authenticatedAs(ownerId)))
                .andExpect(status().isOk());
        return challengeId;
    }

    private MockMultipartFile jpegFile() throws IOException {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return new MockMultipartFile("file", "estudo.jpg", "image/jpeg", output.toByteArray());
    }

    private AuthSession register(String name) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return authService.register(new RegisterRequest(
                name.toLowerCase() + "-" + random + "@test.local",
                "Str0ng!Passw0rd",
                name,
                "user" + random,
                "America/Sao_Paulo",
                "2026-10",
                null, null, null, null, null));
    }
}
