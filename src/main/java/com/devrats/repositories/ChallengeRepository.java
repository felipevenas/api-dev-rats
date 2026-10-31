package com.devrats.repositories;

import com.devrats.entities.ChallengeEntity;
import com.devrats.enums.ChallengeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepository extends JpaRepository<ChallengeEntity, UUID> {

    Optional<ChallengeEntity> findByIdAndGroupId(UUID id, UUID groupId);

    List<ChallengeEntity> findAllByGroupIdOrderBySequenceNoDesc(UUID groupId);

    Optional<ChallengeEntity> findFirstByGroupIdAndStatusInOrderBySequenceNoDesc(UUID groupId, Collection<ChallengeStatus> statuses);

    boolean existsByGroupIdAndStatusIn(UUID groupId, Collection<ChallengeStatus> statuses);

    List<ChallengeEntity> findAllByStatus(ChallengeStatus status);

    List<ChallengeEntity> findAllByStatusAndStartsAtLessThanEqual(ChallengeStatus status, Instant now);

    List<ChallengeEntity> findAllByStatusAndEndsAtLessThanEqual(ChallengeStatus status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChallengeEntity c WHERE c.id = :id")
    Optional<ChallengeEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COALESCE(MAX(c.sequenceNo), 0) FROM ChallengeEntity c WHERE c.group.id = :groupId")
    int findMaxSequenceNo(@Param("groupId") UUID groupId);
}
