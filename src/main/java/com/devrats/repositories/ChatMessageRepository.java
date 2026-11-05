package com.devrats.repositories;

import com.devrats.entities.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    Optional<ChatMessageEntity> findByConversationIdAndSenderIdAndClientMessageId(
            UUID conversationId, UUID senderId, UUID clientMessageId);

    Optional<ChatMessageEntity> findByIdAndConversationId(UUID id, UUID conversationId);

    List<ChatMessageEntity> findAllBySenderIdOrderByCreatedAtDesc(UUID senderId);

    @Query(value = """
            SELECT * FROM chat_messages m
            WHERE m.conversation_id = :conversationId
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessageEntity> findFirstPage(@Param("conversationId") UUID conversationId,
                                          @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM chat_messages m
            WHERE m.conversation_id = :conversationId
              AND (m.created_at, m.id) < (:cursorAt, :cursorId)
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessageEntity> findBefore(@Param("conversationId") UUID conversationId,
                                       @Param("cursorAt") Instant cursorAt,
                                       @Param("cursorId") UUID cursorId,
                                       @Param("limit") int limit);

    /**
     * Última mensagem de cada conversa da página, em uma única consulta.
     * Evita N+1 na listagem de conversas.
     */
    @Query(value = """
            SELECT m.* FROM chat_messages m
            WHERE m.conversation_id IN (:conversationIds)
              AND m.id = (
                SELECT m2.id FROM chat_messages m2
                WHERE m2.conversation_id = m.conversation_id
                ORDER BY m2.created_at DESC, m2.id DESC
                LIMIT 1
              )
            """, nativeQuery = true)
    List<ChatMessageEntity> findLastMessages(@Param("conversationIds") Collection<UUID> conversationIds);

    /**
     * Contagem de não lidas por conversa. A comparação (created_at, id) evita
     * perder mensagens gravadas no mesmo instante do cursor de leitura.
     */
    @Query(value = """
            SELECT m.conversation_id, COUNT(*)
            FROM chat_messages m
            LEFT JOIN conversation_reads r
              ON r.conversation_id = m.conversation_id AND r.user_id = :userId
            WHERE m.conversation_id IN (:conversationIds)
              AND m.sender_id <> :userId
              AND m.deleted_at IS NULL
              AND (
                r.last_read_at IS NULL
                OR m.created_at > r.last_read_at
                OR (
                  m.created_at = r.last_read_at
                  AND (r.last_read_message_id IS NULL OR m.id > r.last_read_message_id)
                )
              )
            GROUP BY m.conversation_id
            """, nativeQuery = true)
    List<Object[]> countUnreadByConversation(@Param("userId") UUID userId,
                                             @Param("conversationIds") Collection<UUID> conversationIds);

    @Query(value = """
            SELECT COUNT(*)
            FROM chat_messages m
            JOIN conversations c ON c.id = m.conversation_id
            LEFT JOIN conversation_reads r
              ON r.conversation_id = c.id AND r.user_id = :userId
            WHERE m.sender_id <> :userId
              AND m.deleted_at IS NULL
              AND (
                r.last_read_at IS NULL
                OR m.created_at > r.last_read_at
                OR (
                  m.created_at = r.last_read_at
                  AND (r.last_read_message_id IS NULL OR m.id > r.last_read_message_id)
                )
              )
              AND (
                (
                  c.type = 'DIRECT'
                  AND (c.user_a_id = :userId OR c.user_b_id = :userId)
                  AND EXISTS (
                    SELECT 1 FROM friendships f
                    WHERE f.status = 'ACCEPTED'
                      AND (
                        (f.requester_id = c.user_a_id AND f.addressee_id = c.user_b_id)
                        OR (f.requester_id = c.user_b_id AND f.addressee_id = c.user_a_id)
                      )
                  )
                )
                OR (
                  c.type = 'GROUP'
                  AND EXISTS (
                    SELECT 1 FROM groups g
                    WHERE g.id = c.group_id AND g.status = 'ACTIVE'
                  )
                  AND EXISTS (
                    SELECT 1 FROM group_members gm
                    WHERE gm.group_id = c.group_id
                      AND gm.user_id = :userId
                      AND gm.status = 'ACTIVE'
                  )
                )
              )
            """, nativeQuery = true)
    long countUnreadTotal(@Param("userId") UUID userId);
}
