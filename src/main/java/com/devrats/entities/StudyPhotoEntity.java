package com.devrats.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Foto de estudo publicada no grupo.
 *
 * <p>A unique key por participante/dia garante a regra de uma foto por dia sem
 * depender de checagem prévia. O objeto vive em bucket privado e só é exposto
 * por URL assinada de curta duração.</p>
 */
@Entity
@Table(name = "study_photos", uniqueConstraints = @UniqueConstraint(name = "uq_study_photos_participant_date", columnNames = {"challenge_id", "participant_id", "activity_date"}))
@Getter
@Setter
@NoArgsConstructor
public class StudyPhotoEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private ChallengeEntity challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private ChallengeParticipantEntity participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    //---- Vínculo de contexto: não participa da pontuação do modo foto.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_repository_id")
    private GitHubRepositoryEntity githubRepository;

    @Column(name = "commit_url", length = 500)
    private String commitUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
