package com.devrats.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_installations")
@Getter
@Setter
@NoArgsConstructor
public class GitHubInstallationEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_account_id", nullable = false)
    private GitHubAccountEntity githubAccount;

    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;

    @Column(name = "account_login", nullable = false, length = 100)
    private String accountLogin;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
