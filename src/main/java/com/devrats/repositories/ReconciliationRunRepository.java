package com.devrats.repositories;

import com.devrats.entities.ReconciliationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRunEntity, UUID> {

    Optional<ReconciliationRunEntity> findTopByChallengeIdOrderByStartedAtDesc(UUID challengeId);

    List<ReconciliationRunEntity> findAllByChallengeIdOrderByStartedAtDesc(UUID challengeId);
}
