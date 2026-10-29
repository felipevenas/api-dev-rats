package com.devstreak.entities;

import com.devstreak.enums.GitHubConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_accounts")
@Getter
@Setter
@NoArgsConstructor
public class GitHubAccountEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    //---- Immutable GitHub identity. Username can change; this id is the source of truth.
    @Column(name = "github_user_id", nullable = false, unique = true)
    private Long githubUserId;

    @Column(name = "github_username", nullable = false, length = 100)
    private String githubUsername;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 16)
    private GitHubConnectionStatus connectionStatus = GitHubConnectionStatus.CONNECTED;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "encrypted_access_token", columnDefinition = "bytea")
    private byte[] encryptedAccessToken;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "encrypted_refresh_token", columnDefinition = "bytea")
    private byte[] encryptedRefreshToken;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "token_key_version")
    private Integer tokenKeyVersion;

    @CreationTimestamp
    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
