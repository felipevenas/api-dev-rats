package com.devrats.repositories;

import com.devrats.entities.ConversationReadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationReadRepository extends JpaRepository<ConversationReadEntity, UUID> {

    Optional<ConversationReadEntity> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);
}
