package com.devrats.repositories;

import com.devrats.entities.ChallengeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeResultRepository extends JpaRepository<ChallengeResultEntity, UUID> {

    Optional<ChallengeResultEntity> findByChallengeId(UUID challengeId);

    boolean existsByChallengeId(UUID challengeId);

    List<ChallengeResultEntity> findAllByWinnerUserIdOrderByFinalizedAtDesc(UUID winnerUserId);
}
