package com.devstreak.repositories;

import com.devstreak.entities.ChallengeRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepositoryRepository extends JpaRepository<ChallengeRepositoryEntity, UUID> {

    List<ChallengeRepositoryEntity> findAllByChallengeIdOrderBySlotAsc(UUID challengeId);

    List<ChallengeRepositoryEntity> findAllByGithubRepositoryId(UUID githubRepositoryId);

    Optional<ChallengeRepositoryEntity> findByChallengeIdAndGithubRepositoryId(UUID challengeId, UUID githubRepositoryId);

    long countByChallengeId(UUID challengeId);

    boolean existsByChallengeIdAndGithubRepositoryId(UUID challengeId, UUID githubRepositoryId);
}
