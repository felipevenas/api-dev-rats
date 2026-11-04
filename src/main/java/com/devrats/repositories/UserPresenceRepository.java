package com.devrats.repositories;

import com.devrats.entities.UserPresenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserPresenceRepository extends JpaRepository<UserPresenceEntity, UUID> {

    List<UserPresenceEntity> findAllByUserIdIn(Collection<UUID> userIds);

    /** Remove heartbeats antigos (retenção LGPD) em uma única operação de bulk. */
    @Modifying(flushAutomatically = true)
    @Query("delete from UserPresenceEntity p where p.lastSeenAt < :cutoff")
    int deleteByLastSeenAtBefore(@Param("cutoff") Instant cutoff);

    /**
     * Upsert atômico: um heartbeat concorrente não gera violação de PK
     * nem uma segunda ida ao banco.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_presence (user_id, last_seen_at)
            VALUES (:userId, :lastSeenAt)
            ON CONFLICT (user_id) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
            """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId, @Param("lastSeenAt") Instant lastSeenAt);
}
