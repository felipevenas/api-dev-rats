package com.devrats.entities;

import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.RepositoryAccessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_repositories")
@Getter
@Setter
@NoArgsConstructor
public class GitHubRepositoryEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "github_repository_id", nullable = false, unique = true)
    private Long githubRepositoryId;

    @Column(name = "owner_github_user_id")
    private Long ownerGithubUserId;

    @Column(name = "owner_login", nullable = false, length = 100)
    private String ownerLogin;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private GitHubOwnerType ownerType;

    @Column(name = "repository_name", nullable = false, length = 200)
    private String repositoryName;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(name = "is_fork", nullable = false)
    private boolean fork;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Column(name = "installation_id")
    private Long installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false, length = 20)
    private RepositoryAccessStatus accessStatus = RepositoryAccessStatus.ACCESSIBLE;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
