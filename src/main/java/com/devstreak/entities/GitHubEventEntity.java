package com.devstreak.entities;

import com.devstreak.enums.GitHubEventStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_events")
@Getter
@Setter
@NoArgsConstructor
public class GitHubEventEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    //---- X-GitHub-Delivery is the idempotency key; GitHub retries reuse the same value.
    @Column(name = "delivery_id", nullable = false, unique = true, length = 64)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(length = 64)
    private String action;

    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "github_repository_id")
    private Long githubRepositoryId;

    //---- Sanitized payload: no diffs, file contents or private commit messages.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GitHubEventStatus status = GitHubEventStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
}
