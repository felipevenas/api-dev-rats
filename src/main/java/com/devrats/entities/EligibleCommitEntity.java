package com.devrats.entities;

import com.devrats.enums.CommitObservationSource;
import com.devrats.enums.EligibilityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

@Entity
@Table(name = "eligible_commits", uniqueConstraints = @UniqueConstraint(name = "uq_eligible_commits_repo_sha", columnNames = {"github_repository_id", "commit_sha"}))
@Getter
@Setter
@NoArgsConstructor
public class EligibleCommitEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_repository_id", nullable = false)
    private GitHubRepositoryEntity githubRepository;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(name = "github_author_id")
    private Long githubAuthorId;

    @Column(name = "github_committer_id")
    private Long githubCommitterId;

    @Column(name = "author_login", length = 100)
    private String authorLogin;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    @CreationTimestamp
    @Column(name = "first_observed_at", nullable = false, updatable = false)
    private Instant firstObservedAt;

    @Column(name = "is_automated", nullable = false)
    private boolean automated;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", nullable = false, length = 16)
    private EligibilityStatus eligibilityStatus;

    @Column(name = "eligibility_reason", length = 120)
    private String eligibilityReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "observation_source", nullable = false, length = 20)
    private CommitObservationSource observationSource;
}
