package com.devrats.repositories;

import com.devrats.entities.ConversationReadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationReadRepository extends JpaRepository<ConversationReadEntity, UUID> {

    Optional<ConversationReadEntity> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO conversation_reads
                (id, conversation_id, user_id, last_read_at, updated_at)
            VALUES (:id, :conversationId, :userId, :lastReadAt, :updatedAt)
            ON CONFLICT (conversation_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("conversationId") UUID conversationId,
                       @Param("userId") UUID userId,
                       @Param("lastReadAt") Instant lastReadAt,
                       @Param("updatedAt") Instant updatedAt);
}
