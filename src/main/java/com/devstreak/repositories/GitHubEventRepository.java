package com.devstreak.repositories;

import com.devstreak.entities.GitHubEventEntity;
import com.devstreak.enums.GitHubEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubEventRepository extends JpaRepository<GitHubEventEntity, UUID> {

    Optional<GitHubEventEntity> findByDeliveryId(String deliveryId);

    boolean existsByDeliveryId(String deliveryId);

    long countByStatus(GitHubEventStatus status);

    //---- SKIP LOCKED keeps concurrent workers from processing the same event without blocking each other.
    @Query(value = """
            SELECT * FROM github_events
            WHERE status IN ('PENDING', 'FAILED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= now())
            ORDER BY received_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<GitHubEventEntity> findPendingForUpdate(@Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM GitHubEventEntity e WHERE e.receivedAt < :cutoff")
    int deleteReceivedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE GitHubEventEntity e SET e.status = :pending WHERE e.status = :processing AND e.receivedAt < :cutoff")
    int resetStuckProcessing(@Param("pending") GitHubEventStatus pending,
                             @Param("processing") GitHubEventStatus processing,
                             @Param("cutoff") Instant cutoff);
}
