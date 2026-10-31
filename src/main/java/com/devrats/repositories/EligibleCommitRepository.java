package com.devrats.repositories;

import com.devrats.entities.EligibleCommitEntity;
import com.devrats.enums.EligibilityStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EligibleCommitRepository extends JpaRepository<EligibleCommitEntity, UUID> {

    Optional<EligibleCommitEntity> findByGithubRepositoryIdAndCommitSha(UUID githubRepositoryId, String commitSha);

    boolean existsByGithubRepositoryIdAndCommitSha(UUID githubRepositoryId, String commitSha);

    //---- JOIN FETCH carrega o repositório na mesma consulta e evita N+1 ao montar a URL do commit.
    @Query("""
            SELECT c
            FROM EligibleCommitEntity c
            JOIN FETCH c.githubRepository r
            WHERE c.githubAuthorId = :authorId
              AND c.eligibilityStatus = :status
              AND c.committedAt >= :from
              AND c.committedAt < :to
              AND r.id IN :repositoryIds
            ORDER BY c.committedAt ASC
            """)
    List<EligibleCommitEntity> findEligibleByAuthorInRepositoriesAndWindow(
            @Param("authorId") Long authorId,
            @Param("status") EligibilityStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("repositoryIds") Collection<UUID> repositoryIds,
            Limit limit);

    //---- The unique (repository, sha) constraint is the idempotency barrier for retries.
    @Modifying
    @Query(value = """
            INSERT INTO eligible_commits
                (id, github_repository_id, commit_sha, github_author_id, github_committer_id, author_login,
                 committed_at, first_observed_at, is_automated, eligibility_status, eligibility_reason, observation_source)
            VALUES
                (gen_random_uuid(), :repositoryId, :sha, :authorId, :committerId, :authorLogin,
                 :committedAt, :observedAt, :automated, :eligibilityStatus, :reason, :source)
            ON CONFLICT (github_repository_id, commit_sha) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("repositoryId") UUID repositoryId,
                       @Param("sha") String sha,
                       @Param("authorId") Long authorId,
                       @Param("committerId") Long committerId,
                       @Param("authorLogin") String authorLogin,
                       @Param("committedAt") Instant committedAt,
                       @Param("observedAt") Instant observedAt,
                       @Param("automated") boolean automated,
                       @Param("eligibilityStatus") String eligibilityStatus,
                       @Param("reason") String reason,
                       @Param("source") String source);
}
