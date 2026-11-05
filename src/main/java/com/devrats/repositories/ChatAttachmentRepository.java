package com.devrats.repositories;

import com.devrats.entities.ChatAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachmentEntity, UUID> {

    List<ChatAttachmentEntity> findAllByMessageIdIn(Collection<UUID> messageIds);

    List<ChatAttachmentEntity> findAllByMessageId(UUID messageId);

    @Query("""
            select a from ChatAttachmentEntity a
            join fetch a.message m
            where a.id = :attachmentId and m.conversation.id = :conversationId
            """)
    Optional<ChatAttachmentEntity> findInConversation(@Param("attachmentId") UUID attachmentId,
                                                      @Param("conversationId") UUID conversationId);

    @Query("""
            select a.objectKey from ChatAttachmentEntity a
            where a.message.conversation.id = :conversationId
            """)
    List<String> findObjectKeysByConversationId(@Param("conversationId") UUID conversationId);
}
