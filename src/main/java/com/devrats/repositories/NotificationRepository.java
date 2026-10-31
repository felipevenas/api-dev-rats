package com.devrats.repositories;

import com.devrats.entities.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    java.util.List<NotificationEntity> findAllByUserIdAndReadAtIsNull(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<NotificationEntity> findByUserIdAndDedupeKey(UUID userId, String dedupeKey);
}
