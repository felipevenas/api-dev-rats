package com.devrats.dto;

import com.devrats.enums.ChatMessageType;
import com.devrats.enums.ExperienceLevel;
import com.devrats.enums.TechStack;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserDataExportResponse(
        UUID userId,
        String displayName,
        String username,
        String email,
        String avatarUrl,
        String avatarSource,
        String bio,
        String location,
        String websiteUrl,
        ExperienceLevel experienceLevel,
        List<TechStack> primaryStack,
        String timezone,
        Instant createdAt,
        List<GroupMembership> groups,
        List<Friendship> friendships,
        Instant lastSeenAt,
        List<ChatMessageExport> chatMessages,
        List<StudyPhotoExport> studyPhotos
) {

    public record GroupMembership(
            UUID groupId,
            String groupName,
            String role,
            String membershipStatus,
            Instant joinedAt
    ) {
    }

    /**
     * Vínculo social do titular sem PII de terceiros: apenas o id do registro,
     * a direção, o estado e as datas.
     */
    public record Friendship(
            UUID friendshipId,
            String direction,
            String status,
            Instant createdAt,
            Instant respondedAt
    ) {
    }

    /**
     * Exporta apenas mensagens enviadas pelo titular. O conteúdo recebido de
     * terceiros não entra no arquivo de portabilidade.
     */
    public record ChatMessageExport(
            UUID conversationId,
            ChatMessageType type,
            String body,
            Instant createdAt,
            boolean deleted,
            List<ChatAttachmentExport> attachments
    ) {
    }

    public record ChatAttachmentExport(
            String fileName,
            String contentType,
            long sizeBytes
    ) {
    }

    /**
     * Metadados das fotos de estudo do titular. A imagem em si vive em bucket
     * privado e não é embutida no arquivo de portabilidade.
     */
    public record StudyPhotoExport(
            UUID photoId,
            UUID challengeId,
            LocalDate activityDate,
            String title,
            String description,
            Instant createdAt
    ) {
    }
}
