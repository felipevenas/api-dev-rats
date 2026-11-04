package com.devrats.repositories;

import com.devrats.entities.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    java.util.List<NotificationEntity> findAllByUserIdAndReadAtIsNull(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<NotificationEntity> findByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    /** Remove as notificações do titular em exclusão de conta (o delete de usuário é soft). */
    @Modifying(flushAutomatically = true)
    @Query("delete from NotificationEntity n where n.user.id = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);

    /**
     * Remove notificações de terceiros que carregam o snapshot público do ator excluído.
     * Sem isso, username/displayName/avatar do titular sobrevivem no payload JSONB.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from notifications where payload->>'actorUserId' = :userId", nativeQuery = true)
    int deleteSocialByActor(@Param("userId") String userId);
}
