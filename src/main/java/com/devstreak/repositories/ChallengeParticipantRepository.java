package com.devstreak.repositories;

import com.devstreak.entities.ChallengeParticipantEntity;
import com.devstreak.enums.ParticipantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipantEntity, UUID> {

    Optional<ChallengeParticipantEntity> findByChallengeIdAndUserId(UUID challengeId, UUID userId);

    List<ChallengeParticipantEntity> findAllByChallengeIdOrderByValidDaysDescCurrentStreakDescJoinedAtAsc(UUID challengeId);

    List<ChallengeParticipantEntity> findAllByChallengeIdAndStatus(UUID challengeId, ParticipantStatus status);

    List<ChallengeParticipantEntity> findAllByUserIdAndStatus(UUID userId, ParticipantStatus status);

    long countByChallengeIdAndStatus(UUID challengeId, ParticipantStatus status);

    //---- Lock before recalculating counters so two commits on different days cannot lose an update.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ChallengeParticipantEntity p WHERE p.id = :id")
    Optional<ChallengeParticipantEntity> findByIdForUpdate(@Param("id") UUID id);
}
