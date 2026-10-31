package com.devrats.repositories;

import com.devrats.entities.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    List<AuditLogEntity> findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    int deleteByCreatedAtBefore(Instant cutoff);
}
